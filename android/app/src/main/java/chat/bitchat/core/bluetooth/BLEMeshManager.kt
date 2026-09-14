package chat.bitchat.core.bluetooth

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import chat.bitchat.data.database.EchoMeshDatabase
import chat.bitchat.data.database.Peer
import chat.bitchat.protocol.BitchatPacket
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.Random
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dual-role BLE transport aligned with BitChat iOS:
 * - Advertise service UUID + nickname in *service data* (never rely on adapter local-name cache)
 * - Scan, connect, enable notifications, keep short-lived links
 * - Send via central writeWithoutResponse and/or peripheral notify
 * - Receive via GATT server writes and client notifications
 * - Do not tear down the GATT server after every send
 */
@Singleton
class BLEMeshManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: EchoMeshDatabase
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
    private val bluetoothAdapter = bluetoothManager?.adapter

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<NearbyDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<NearbyDevice>> = _discoveredDevices.asStateFlow()

    private val _receivedPackets =
        MutableSharedFlow<Pair<String, BitchatPacket>>(extraBufferCapacity = 64, replay = 0)
    val receivedPackets: SharedFlow<Pair<String, BitchatPacket>> = _receivedPackets.asSharedFlow()

    private val assemblyBuffer = BLEFragmentAssemblyBuffer()
    private val serverWriteMutexes = ConcurrentHashMap<String, Mutex>()
    private val pendingNotifications = ConcurrentHashMap<String, CompletableDeferred<Unit>>()

    private var scanCallback: ScanCallback? = null
    private var advertiseCallback: AdvertiseCallback? = null
    private var gattServer: BluetoothGattServer? = null
    private var serverCharacteristic: BluetoothGattCharacteristic? = null
    private var pruneJob: Job? = null
    private var serviceAdded = CompletableDeferred<Unit>()

    /** Centrals connected to our GATT server (peer initiated toward us). */
    private val serverConnectedCentrals = ConcurrentHashMap<String, BluetoothDevice>()

    /** Centrals subscribed to our notify characteristic (peer → us as peripheral). */
    private val subscribedCentrals = ConcurrentHashMap<String, BluetoothDevice>()

    /** Live GATT client links (us → peer as central). */
    private val clientLinks = ConcurrentHashMap<String, ClientLink>()
    private val connecting = ConcurrentHashMap.newKeySet<String>()
    private val connectCooldownUntil = ConcurrentHashMap<String, Long>()

    /** Centrals MTU cache (negotiated by connected centrals). */
    private val serverConnectedCentralsMtu = ConcurrentHashMap<String, Int>()

    val nicknameCache = ConcurrentHashMap<String, String>()

    fun updateNicknameCache(key: String, name: String) {
        if (name.isBlank() || name.startsWith("Nearby ")) return
        nicknameCache[key] = name
        nicknameCache[key.uppercase()] = name
        
        // Update active discovered list directly
        _discoveredDevices.update { current ->
            val list = current.toMutableList()
            val idx = list.indexOfFirst { it.identity == key || it.id.equals(key, true) }
            if (idx >= 0) {
                list[idx] = list[idx].copy(name = name)
            }
            list
        }
    }

    val verifiedMacToIdentity = ConcurrentHashMap<String, String>()

    fun registerVerifiedIdentity(macAddress: String, stableId: String) {
        if (stableId.startsWith("dev_")) {
            val cleanMac = macAddress.uppercase()
            verifiedMacToIdentity[cleanMac] = stableId
            Log.d(TAG, "[IDENTITY] verified MAC=$cleanMac -> stableId=$stableId")
            
            _discoveredDevices.update { current ->
                val list = current.toMutableList()
                val idx = list.indexOfFirst { it.id.equals(cleanMac, ignoreCase = true) }
                if (idx >= 0) {
                    val existing = list[idx]
                    if (existing.identity != stableId) {
                        list[idx] = existing.copy(identity = stableId)
                        Log.d(TAG, "[IDENTITY] applied verified identity to discovered device: $cleanMac -> $stableId")
                    }
                }
                list
            }
        }
    }

    @Volatile
    private var displayName: String = "EchoMesh"

    private class ClientLink(
        val gatt: BluetoothGatt,
        val address: String,
        @Volatile var characteristic: BluetoothGattCharacteristic? = null,
        @Volatile var ready: Boolean = false,
        @Volatile var mtu: Int = 23,
        @Volatile var pendingWrite: CompletableDeferred<Int>? = null,
        @Volatile var nickname: String? = null,
        @Volatile var lastUsed: Long = System.currentTimeMillis(),
        val writeMutex: Mutex = Mutex()
    )

    val localIdentity: String
        get() = "dev_${getOrCreateShortDeviceId()}"

    fun ensureReady(forceRestart: Boolean = false) {
        scope.launch {
            Log.d(TAG, "[IDENTITY] localStableId=$localIdentity")
            try {
                val allPeers = database.peerDao().getAllPeersDirect()
                val legacyPeers = allPeers.filter { it.peerID.contains(":") || !it.peerID.startsWith("dev_") }
                Log.d(TAG, "[IDENTITY-MIGRATION] legacy peer records found = ${allPeers.size} records eligible for cleanup = ${legacyPeers.size}")
                if (legacyPeers.isNotEmpty()) {
                    database.peerDao().deleteLegacyPeers()
                }
                database.messageDao().deleteLegacyMessages()
                Log.i(TAG, "[IDENTITY] Legacy database cleanup completed successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to run legacy database cleanup", e)
            }
            refreshDisplayName()
            setupGattServer(force = forceRestart)
            withTimeoutOrNull(5_000) { serviceAdded.await() }
            stopAdvertising()
            delay(150)
            startAdvertising()
            if (!_isScanning.value) startScanning()
        }
    }

    fun stopAll() {
        Log.i(TAG, "Stopping all BLE mesh operations")
        stopScanning()
        stopAdvertising()
        disconnectAllClients()
        try {
            gattServer?.close()
        } catch (_: Exception) {}
        gattServer = null
        serverCharacteristic = null
        subscribedCentrals.clear()
        serverConnectedCentrals.clear()
        serverConnectedCentralsMtu.clear()
        if (!serviceAdded.isCompleted) serviceAdded.completeExceptionally(java.util.concurrent.CancellationException("GATT server reset"))
        serviceAdded = CompletableDeferred()
    }

    private suspend fun refreshDisplayName() {
        displayName = try {
            database.userProfileDao().getProfileDirect()
                ?.name?.takeIf { it.isNotBlank() } ?: "EchoMesh"
        } catch (_: Exception) {
            "EchoMesh"
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun checkBluetoothState(): Boolean {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasPermission(Manifest.permission.BLUETOOTH_SCAN) &&
                hasPermission(Manifest.permission.BLUETOOTH_ADVERTISE) &&
                hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    fun startScanning() {
        if (_isScanning.value) return
        if (!checkBluetoothState()) {
            Log.e(TAG, "Cannot scan — bluetooth/permissions not ready")
            return
        }
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return

        _isScanning.value = true
        pruneJob?.cancel()
        pruneJob = scope.launch {
            while (_isScanning.value) {
                delay(8_000)
                pruneStale()
            }
        }

        val filters = listOf(
            ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE_UUID)).build()
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                onDeviceFound(result)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { onDeviceFound(it) }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "Scan failed: $errorCode")
                _isScanning.value = false
            }
        }
        scanCallback = callback
        try {
            scanner.startScan(filters, settings, callback)
            Log.i(TAG, "Scanning started")
        } catch (e: SecurityException) {
            Log.e(TAG, "Scan denied", e)
            _isScanning.value = false
        }
    }

    private data class ParsedAd(
        val shortId: Int?,
        val name: String
    )

    private fun getOrCreateShortDeviceId(): Int {
        val prefs = context.getSharedPreferences("echomesh_prefs", Context.MODE_PRIVATE)
        var id = prefs.getInt("short_device_id", 0)
        if (id == 0) {
            id = java.util.Random().nextInt(Int.MAX_VALUE - 1) + 1
            prefs.edit().putInt("short_device_id", id).apply()
        }
        return id
    }

    private fun onDeviceFound(result: ScanResult) {
        val address = result.device.address
        val parsedAd = parseAdvertisedPayload(result)
        val name = parsedAd?.name ?: "Nearby ${address.takeLast(5)}"
        val identity = verifiedMacToIdentity[address.uppercase()]
            ?: parsedAd?.shortId?.let { "dev_$it" }
            ?: name.lowercase()
        Log.d(TAG, "onDeviceFound: address=$address, parsedAdName=${parsedAd?.name}, shortId=${parsedAd?.shortId}, identity=$identity, rssi=${result.rssi}")

        Log.d(TAG, "[IDENTITY] discovered transport=$address")
        upsertDevice(
            NearbyDevice(
                id = address,
                name = name,
                rssi = result.rssi,
                discoveredAt = System.currentTimeMillis(),
                identity = identity
            )
        )
        // ONLY PERSIST NICKNAME IF DERIVED FROM LIVE SERVICE DATA (not OS cache)
        if (parsedAd?.shortId != null) {
            updateNicknameCache(identity, name)
            updateNicknameCache(address, name)
            scope.launch { persistPeerNickname(identity, name) }
            clientLinks[address.uppercase()]?.nickname = name
        }
        maybeConnect(result.device, name, identity)
    }

    /**
     * Prefer EchoMesh service-data nickname (fresh on every advertise).
     * Fall back to scan-record deviceName only if it looks like a real nickname —
     * never trust the system-cached adapter name alone when service data exists elsewhere.
     */
    private fun parseAdvertisedPayload(result: ScanResult): ParsedAd? {
        val record = result.scanRecord ?: return null
        val serviceBytes = record.getServiceData(ParcelUuid(SERVICE_UUID))
        decodeNicknamePayload(serviceBytes)?.let { return it }

        val fromScan = record.deviceName?.trim()?.takeIf { it.isNotBlank() }
        if (fromScan != null &&
            !fromScan.equals("Unknown", true) &&
            !fromScan.equals("Unknown Device", true) &&
            !fromScan.startsWith("Galaxy", true) &&
            !fromScan.startsWith("Pixel", true) &&
            !fromScan.startsWith("OPPO", true) &&
            !fromScan.startsWith("vivo", true) &&
            !fromScan.startsWith("CPH", true) &&
            !fromScan.startsWith("V20", true)
        ) {
            // Null shortId indicates a legacy scan fallback name
            return ParsedAd(shortId = null, name = fromScan.take(24))
        }
        return null
    }

    private fun decodeNicknamePayload(bytes: ByteArray?): ParsedAd? {
        if (bytes == null || bytes.size < 6) return null
        if (bytes[0] != NICK_MAGIC_0 || bytes[1] != NICK_MAGIC_1) return null
        val shortId = ByteBuffer.wrap(bytes, 2, 4).order(java.nio.ByteOrder.BIG_ENDIAN).int
        val name = String(bytes, 6, bytes.size - 6, StandardCharsets.UTF_8).trim()
        if (name.isBlank()) return null
        return ParsedAd(shortId = shortId, name = name.take(24))
    }

    private fun encodeNicknamePayload(name: String, shortId: Int): ByteArray {
        val fullBytes = name.trim().ifBlank { "Echo" }.toByteArray(StandardCharsets.UTF_8)
        val validLength = minOf(fullBytes.size, 7)
        val nickBytes = fullBytes.copyOfRange(0, validLength)
        
        val buf = ByteBuffer.allocate(6 + nickBytes.size).order(java.nio.ByteOrder.BIG_ENDIAN)
        buf.put(NICK_MAGIC_0)
        buf.put(NICK_MAGIC_1)
        buf.putInt(shortId)
        buf.put(nickBytes)
        return buf.array()
    }

    private suspend fun persistPeerNickname(peerId: String, name: String) {
        if (name.startsWith("Nearby ") || peerId.startsWith("Nearby ") || peerId.contains(":")) return
        try {
            Log.d(TAG, "[IDENTITY] peer resolved stableId=$peerId name=$name")
            database.peerDao().upsertPeerPresence(
                peerId,
                name,
                System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist peer nickname", e)
        }
    }

    fun stopScanning() {
        stopScanInternal(clear = true)
        disconnectAllClients()
    }

    private fun stopScanInternal(clear: Boolean) {
        _isScanning.value = false
        pruneJob?.cancel()
        pruneJob = null
        if (scanCallback != null && checkBluetoothState()) {
            try {
                bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
            } catch (_: SecurityException) {
            }
        }
        scanCallback = null
        if (clear) _discoveredDevices.value = emptyList()
    }

    fun startAdvertising() {
        if (_isAdvertising.value) return
        if (!checkBluetoothState()) {
            Log.e(TAG, "Cannot advertise — bluetooth/permissions not ready")
            return
        }
        setupGattServer(force = false)

        val advertiser = bluetoothAdapter?.bluetoothLeAdvertiser ?: run {
            Log.e(TAG, "No BLE advertiser")
            return
        }

        // Mark immediately as advertising to prevent concurrent race entries
        _isAdvertising.value = true

        // Keep adapter name fixed — OEM stacks cache local names and show stale nicknames.
        try {
            bluetoothAdapter.name = "EchoMesh"
        } catch (_: SecurityException) {
        }

        val shortId = getOrCreateShortDeviceId()
        val nickPayload = encodeNicknamePayload(displayName, shortId)
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .setTimeout(0)
            .build()

        // BitChat: UUID only in ads. We add compact service-data nickname so peers
        // update names without relying on adapter-name cache (Android-specific).
        val primary = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .setIncludeDeviceName(false)
            .build()
        val scanResponse = AdvertiseData.Builder()
            .addServiceData(ParcelUuid(SERVICE_UUID), nickPayload)
            .setIncludeDeviceName(false)
            .build()

        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                Log.i(TAG, "Advertising nickname='$displayName' via service data")
                _isAdvertising.value = true
            }

            override fun onStartFailure(errorCode: Int) {
                Log.e(TAG, "Advertise failed code=$errorCode")
                _isAdvertising.value = false
                // Fallback: UUID-only (BitChat style) — names will come after GATT announce path later
                if (errorCode == AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE ||
                    errorCode == AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR
                ) {
                    try {
                        val bare = object : AdvertiseCallback() {
                            override fun onStartSuccess(s: AdvertiseSettings) {
                                Log.i(TAG, "UUID-only advertising OK (no nickname in ads)")
                                _isAdvertising.value = true
                            }

                            override fun onStartFailure(code: Int) {
                                Log.e(TAG, "UUID-only advertise failed=$code")
                                _isAdvertising.value = false
                            }
                        }
                        advertiseCallback = bare
                        advertiser.startAdvertising(settings, primary, bare)
                    } catch (e: Exception) {
                        Log.e(TAG, "Advertise retry crashed", e)
                    }
                }
            }
        }
        advertiseCallback = callback
        try {
            advertiser.startAdvertising(settings, primary, scanResponse, callback)
        } catch (e: Exception) {
            Log.e(TAG, "Advertise denied or failed", e)
            _isAdvertising.value = false
        }
    }

    fun stopAdvertising() {
        _isAdvertising.value = false
        val cb = advertiseCallback  // capture locally to avoid race condition
        if (cb != null && checkBluetoothState()) {
            try {
                bluetoothAdapter?.bluetoothLeAdvertiser?.stopAdvertising(cb)
            } catch (_: SecurityException) {
            } catch (_: IllegalArgumentException) {
                // Some vendors (e.g. Vivo) throw this when the internal advertiser
                // callback state is inconsistent. Safe to ignore — advertising is
                // already stopping.
            }
        }
        advertiseCallback = null
    }

    fun refreshAdvertisingIdentity() {
        scope.launch {
            refreshDisplayName()
            stopAdvertising()
            delay(250)
            startAdvertising()
            broadcastLocalProfile()
        }
    }

    fun broadcastLocalProfile() {
        scope.launch {
            try {
                val profile = database.userProfileDao().getProfileDirect() ?: return@launch
                val json = org.json.JSONObject().apply {
                    put("identity", localIdentity)
                    put("nickname", profile.name)
                    put("bio", profile.bio)
                    put("interests", profile.interests)
                    put("music", profile.favoriteMusic)
                    put("movies", profile.favoriteMovies)
                    put("singers", profile.singers)
                    put("career", profile.career)
                    val pubKey = chat.bitchat.core.security.KeyManager.getPublicKeyBytes()
                    if (pubKey.isNotEmpty()) {
                        put("publicKey", android.util.Base64.encodeToString(pubKey, android.util.Base64.NO_WRAP))
                    }
                }.toString()

                val myNickname = profile.name.takeIf { it.isNotBlank() } ?: "Me"

                // Hash the stable localIdentity (dev_XXXX), NOT the display nickname
                val myPeerId = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(localIdentity.toByteArray())
                    .copyOf(8)

                val packet = BitchatPacket(
                    version = 2,
                    type = TYPE_PROFILE,
                    senderID = myPeerId,
                    recipientID = null,
                    timestamp = System.currentTimeMillis() / 1000,
                    payload = json.toByteArray(Charsets.UTF_8),
                    signature = null,
                    ttl = 1,
                    route = null,
                    isRSR = false
                )

                // Send to all active client links
                clientLinks.forEach { (address, link) ->
                    if (link.ready) {
                        Log.i(TAG, "Broadcasting updated profile to client link: $address")
                        sendPacket(address, packet) { success ->
                            Log.d(TAG, "Broadcast profile to client $address success=$success")
                        }
                    }
                }

                // Send to all subscribed server centrals
                subscribedCentrals.keys.forEach { address ->
                    Log.i(TAG, "Broadcasting updated profile to subscribed central: $address")
                    sendPacket(address, packet) { success ->
                        Log.d(TAG, "Broadcast profile to central $address success=$success")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to broadcast local profile", e)
            }
        }
    }

    @Synchronized
    fun setupGattServer(force: Boolean = false) {
        if (!checkBluetoothState()) {
            Log.w(TAG, "GATT server skipped — not ready")
            return
        }
        if (gattServer != null && !force) {
            if (!serviceAdded.isCompleted) serviceAdded.complete(Unit)
            return
        }
        if (force) {
            try {
                gattServer?.close()
            } catch (_: Exception) {
            }
            gattServer = null
            serverCharacteristic = null
            subscribedCentrals.clear()
            serverConnectedCentrals.clear()
            serverConnectedCentralsMtu.clear()
            if (!serviceAdded.isCompleted) serviceAdded.completeExceptionally(java.util.concurrent.CancellationException("GATT server reset"))
            serviceAdded = CompletableDeferred()
        }

        Log.i(TAG, "Opening GATT server (notify+write)")
        val callback = object : BluetoothGattServerCallback() {
            override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
                val key = device.address.uppercase()
                Log.i(TAG, "Server CSE ${device.address} status=$status state=$newState")
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    serverConnectedCentrals[key] = device
                } else {
                    serverConnectedCentrals.remove(key)
                    subscribedCentrals.remove(key)
                    serverConnectedCentralsMtu.remove(key)
                    serverWriteMutexes.remove(key)
                }
            }

            override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
                Log.i(TAG, "Server MTU changed for ${device.address} to $mtu")
                serverConnectedCentralsMtu[device.address.uppercase()] = mtu
            }

            override fun onServiceAdded(status: Int, service: BluetoothGattService?) {
                Log.i(TAG, "Service added status=$status uuid=${service?.uuid}")
                if (status == BluetoothGatt.GATT_SUCCESS && !serviceAdded.isCompleted) {
                    serviceAdded.complete(Unit)
                }
            }

            override fun onNotificationSent(device: BluetoothDevice, status: Int) {
                pendingNotifications[device.address.uppercase()]?.complete(Unit)
            }

            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice,
                requestId: Int,
                characteristic: BluetoothGattCharacteristic,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray?
            ) {
                val bytes = value ?: ByteArray(0)
                Log.i(TAG, ">>> SERVER WRITE from ${device.address} bytes=${bytes.size}")
                if (responseNeeded) {
                    try {
                        gattServer?.sendResponse(
                            device, requestId, BluetoothGatt.GATT_SUCCESS, offset, bytes
                        )
                    } catch (e: SecurityException) {
                        Log.e(TAG, "sendResponse failed", e)
                    }
                }
                if (characteristic.uuid == CHAR_UUID && bytes.isNotEmpty()) {
                    handleIncoming(device.address, bytes)
                }
            }

            override fun onDescriptorWriteRequest(
                device: BluetoothDevice,
                requestId: Int,
                descriptor: BluetoothGattDescriptor,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray?
            ) {
                if (descriptor.uuid == CCCD_UUID && value != null) {
                    val enabled = value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    val key = device.address.uppercase()
                    if (enabled) {
                        subscribedCentrals[key] = device
                        Log.i(TAG, "Central subscribed: ${device.address}")
                        scope.launch {
                            delay(200)
                            sendLocalProfile(device.address)
                        }
                    } else {
                        subscribedCentrals.remove(key)
                        Log.i(TAG, "Central unsubscribed: ${device.address}")
                    }
                }
                if (responseNeeded) {
                    try {
                        gattServer?.sendResponse(
                            device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value
                        )
                    } catch (_: SecurityException) {
                    }
                }
            }
        }

        try {
            gattServer = bluetoothManager?.openGattServer(context, callback)?.also { server ->
                val service =
                    BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
                val characteristic = BluetoothGattCharacteristic(
                    CHAR_UUID,
                    BluetoothGattCharacteristic.PROPERTY_READ or
                        BluetoothGattCharacteristic.PROPERTY_WRITE or
                        BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or
                        BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                    BluetoothGattCharacteristic.PERMISSION_READ or
                        BluetoothGattCharacteristic.PERMISSION_WRITE
                )
                val cccd = BluetoothGattDescriptor(
                    CCCD_UUID,
                    BluetoothGattDescriptor.PERMISSION_READ or
                        BluetoothGattDescriptor.PERMISSION_WRITE
                )
                characteristic.addDescriptor(cccd)
                service.addCharacteristic(characteristic)
                serverCharacteristic = characteristic
                server.addService(service)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "openGattServer failed", e)
            gattServer = null
            serverCharacteristic = null
        }
    }

    private fun handleIncoming(address: String, data: ByteArray) {
        clientLinks[address.uppercase()]?.lastUsed = System.currentTimeMillis()
        val packet = BitchatPacket.fromBinaryData(data)
        if (packet == null) {
            Log.e(TAG, "Incoming decode failed (${data.size} bytes)")
            return
        }
        Log.i(TAG, "Incoming packet type=${packet.type} from $address")
        if (packet.type == 0x20.toByte()) {
            val frag = BLEFragmentAssemblyBuffer.unpackFragmentPayload(packet.payload) ?: return
            val complete = assemblyBuffer.addFragment(
                frag.fragmentId, frag.index, frag.total, frag.originalType, frag.data
            ) ?: return
            val original = BitchatPacket.fromBinaryData(complete.data) ?: return
            scope.launch { _receivedPackets.emit(address to original) }
        } else {
            scope.launch { _receivedPackets.emit(address to packet) }
        }
    }

    private fun maybeConnect(device: BluetoothDevice, nickname: String, peerIdentity: String? = null) {
        val key = device.address.uppercase()
        if (serverConnectedCentrals.containsKey(key)) {
            Log.d(TAG, "Skip client connect — $key already connected to our server")
            return
        }
        if (clientLinks.containsKey(key)) {
            clientLinks[key]?.nickname = nickname.takeUnless { it.startsWith("Nearby ") }
            return
        }
        if (!ConnectionTieBreaker.shouldInitiateConnection(localIdentity, peerIdentity)) {
            Log.d(TAG, "Connection tie-breaker: skipping outbound connection to $peerIdentity (waiting for inbound)")
            return
        }
        if (connecting.contains(key)) return
        if (connecting.size >= 1) return // one outbound connect at a time — OEM stacks choke
        val cooldown = connectCooldownUntil[key] ?: 0L
        if (System.currentTimeMillis() < cooldown) return
        if (clientLinks.size >= MAX_CLIENT_LINKS) return
        if (!checkBluetoothState()) return

        connecting.add(key)
        scope.launch {
            try {
                // Randomized delay before connection attempt to resolve bidirectional GATT collisions (error 133)
                delay((200L..1500L).random())
                if (serverConnectedCentrals.containsKey(key)) {
                    Log.d(TAG, "Skip client connect — $key connected to our server during delay")
                    return@launch
                }
                openClientLink(device, nickname)
            } finally {
                connecting.remove(key)
            }
        }
    }

    private suspend fun openClientLink(device: BluetoothDevice, nickname: String) {
        val key = device.address.uppercase()
        val connected = CompletableDeferred<BluetoothGatt>()
        val servicesReady = CompletableDeferred<Unit>()
        val mtuReady = CompletableDeferred<Int>()
        val descriptorReady = CompletableDeferred<Boolean>()

        val cb = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                Log.i(TAG, "Client CSE ${device.address} status=$status state=$newState")
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    scope.launch {
                        try {
                            refreshDeviceCache(g)
                            delay(250)
                            g.discoverServices()
                        } catch (_: SecurityException) {
                            connected.completeExceptionally(Exception("discover denied"))
                        }
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    clientLinks.remove(key)
                    if (!connected.isCompleted) {
                        connected.completeExceptionally(Exception("disconnected $status"))
                    }
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        connectCooldownUntil[key] = System.currentTimeMillis() + 5_000
                    }
                    try {
                        g.close()
                    } catch (_: Exception) {
                    }
                }
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    if (!connected.isCompleted) connected.complete(g)
                    servicesReady.complete(Unit)
                } else if (!connected.isCompleted) {
                    connected.completeExceptionally(Exception("services $status"))
                }
            }

            override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
                val value = if (status == BluetoothGatt.GATT_SUCCESS) mtu else 23
                clientLinks[key]?.mtu = value
                if (!mtuReady.isCompleted) mtuReady.complete(value)
            }

            override fun onDescriptorWrite(
                g: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int
            ) {
                if (descriptor.uuid == CCCD_UUID) {
                    val ok = status == BluetoothGatt.GATT_SUCCESS
                    if (ok) {
                        clientLinks[key]?.ready = true
                        Log.i(TAG, "Notifications enabled on ${device.address}")
                    }
                    if (!descriptorReady.isCompleted) descriptorReady.complete(ok)
                }
            }

            override fun onCharacteristicChanged(
                g: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {
                Log.i(TAG, ">>> NOTIFY from ${device.address} bytes=${value.size}")
                handleIncoming(device.address, value)
            }

            @Deprecated("Deprecated in API 33")
            override fun onCharacteristicChanged(
                g: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {
                @Suppress("DEPRECATION")
                val value = characteristic.value ?: return
                Log.i(TAG, ">>> NOTIFY(legacy) from ${device.address} bytes=${value.size}")
                handleIncoming(device.address, value)
            }

            override fun onCharacteristicWrite(
                g: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                clientLinks[key]?.pendingWrite?.complete(status)
            }
        }

        val gatt = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(context, false, cb, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(context, false, cb)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "connectGatt denied", e)
            return
        } ?: return

        clientLinks[key] = ClientLink(
            gatt = gatt,
            address = device.address,
            nickname = nickname.takeUnless { it.startsWith("Nearby ") }
        )
        try {
            withTimeout(15_000) {
                val g = connected.await()
                servicesReady.await()

                val characteristic = g.getService(SERVICE_UUID)?.getCharacteristic(CHAR_UUID)
                if (characteristic == null) {
                    Log.e(TAG, "Peer ${device.address} missing characteristic")
                    g.disconnect()
                    clientLinks.remove(key)
                    return@withTimeout
                }
                clientLinks[key]?.characteristic = characteristic

                try {
                    g.requestMtu(512)
                } catch (_: SecurityException) {
                }
                val mtu = withTimeoutOrNull(3_000) { mtuReady.await() } ?: 23
                clientLinks[key]?.mtu = mtu

                g.setCharacteristicNotification(characteristic, true)
                val cccd = characteristic.getDescriptor(CCCD_UUID)
                if (cccd != null) {
                    delay(100)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    } else {
                        @Suppress("DEPRECATION")
                        run {
                            cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            g.writeDescriptor(cccd)
                        }
                    }
                    val descOk = withTimeoutOrNull(3_000) { descriptorReady.await() } ?: false
                    if (!descOk) {
                        Log.w(TAG, "CCCD write timed out on ${device.address} — write-only fallback")
                        clientLinks[key]?.ready = true
                    }
                } else {
                    clientLinks[key]?.ready = true
                }
                Log.i(TAG, "Client link ready → ${device.address} mtu=$mtu")
                sendLocalProfile(device.address)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Client link failed ${device.address}: ${e.message}")
            connectCooldownUntil[key] = System.currentTimeMillis() + 5_000
            clientLinks.remove(key)
            try {
                gatt.disconnect()
                gatt.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun disconnectAllClients() {
        clientLinks.values.forEach { link ->
            try {
                link.gatt.disconnect()
                link.gatt.close()
            } catch (_: Exception) {
            }
        }
        clientLinks.clear()
        connecting.clear()
    }

    val activeLinkCount: Int
        get() = clientLinks.size + serverConnectedCentrals.size

    fun getLinkQuality(address: String): Byte {
        val rssi = _discoveredDevices.value.find { it.id.equals(address, ignoreCase = true) }?.rssi
        return (rssi ?: -100).toByte()
    }

    fun relayPacket(excludeAddress: String, packet: BitchatPacket) {
        val raw = packet.toBinaryData(padding = false) ?: return
        val excludeKey = excludeAddress.uppercase()

        scope.launch {
            val clients = clientLinks.filter { (key, link) ->
                key != excludeKey && link.ready && link.characteristic != null
            }
            val servers = subscribedCentrals.filter { (key, _) ->
                key != excludeKey
            }

            if (clients.isEmpty() && servers.isEmpty()) {
                Log.d(TAG, "No relay targets found (excluding $excludeAddress)")
                return@launch
            }

            var targetMtu = Int.MAX_VALUE
            for ((_, link) in clients) {
                targetMtu = minOf(targetMtu, link.mtu)
            }
            for (key in servers.keys) {
                val mtu = serverConnectedCentralsMtu[key.uppercase()] ?: 23
                targetMtu = minOf(targetMtu, mtu)
            }
            if (targetMtu == Int.MAX_VALUE) targetMtu = 23

            val maxPayload = maxOf(20, targetMtu - 3)

            // Calculate overhead of the fragmentation BitchatPacket (excluding chunk data)
            val dummyPacket = BitchatPacket(
                version = packet.version,
                type = 0x20,
                senderID = packet.senderID,
                recipientID = packet.recipientID,
                timestamp = packet.timestamp,
                payload = ByteArray(0),
                signature = null,
                ttl = packet.ttl,
                route = packet.route,
                isRSR = packet.isRSR
            )
            val dummyRaw = dummyPacket.toBinaryData(padding = false) ?: ByteArray(0)
            val overhead = dummyRaw.size + 13 // 13 bytes for fragment header (fragmentId (8) + index (2) + total (2) + originalType (1))

            val maxGattPayload = maxOf(20, targetMtu - 3)
            val chunkSize = if (targetMtu - 3 > overhead) {
                targetMtu - 3 - overhead
            } else {
                maxGattPayload
            }
            val chunks = if (raw.size <= maxGattPayload) {
                listOf(raw)
            } else {
                fragment(packet, chunkSize)
            }

            // 1. Notify to subscribed servers
            for ((_, device) in servers) {
                val server = gattServer ?: continue
                val characteristic = serverCharacteristic ?: continue
                launch {
                    notifyToCentral(server, characteristic, device, chunks)
                    Log.i(TAG, "Relayed via notify → ${device.address}")
                }
            }

            // 2. Write to active clients
            for ((_, link) in clients) {
                val characteristic = link.characteristic ?: continue
                launch {
                    writeChunks(link.gatt, characteristic, chunks, link)
                    Log.i(TAG, "Relayed via client write → ${link.address}")
                }
            }
        }
    }

    fun sendPacket(deviceAddress: String, packet: BitchatPacket, callback: (Boolean) -> Unit) {
        Log.i(TAG, "sendPacket → $deviceAddress")
        if (!checkBluetoothState()) {
            callback(false)
            return
        }
        setupGattServer(force = false)

        scope.launch {
            val ok = try {
                deliverPacket(deviceAddress, packet)
            } catch (e: Exception) {
                Log.e(TAG, "sendPacket error", e)
                false
            }
            Log.i(TAG, "sendPacket done ok=$ok")
            callback(ok)
        }
    }

    private suspend fun deliverPacket(deviceAddress: String, packet: BitchatPacket): Boolean {
        val raw = packet.toBinaryData(padding = false) ?: return false
        val candidates = resolveSendTargets(deviceAddress)
        Log.i(TAG, "Deliver targets for $deviceAddress → ${candidates.joinToString()}")

        for (target in candidates) {
            if (serverConnectedCentrals.containsKey(target.uppercase())) continue
            awaitClientLink(target)
        }

        var targetMtu = 23
        for (target in candidates) {
            val key = target.uppercase()
            val mtu = clientLinks[key]?.mtu ?: serverConnectedCentralsMtu[key]
            if (mtu != null && mtu > targetMtu) {
                targetMtu = mtu
            }
        }
        val maxPayload = maxOf(20, targetMtu - 3)

        // Calculate overhead of the fragmentation BitchatPacket (excluding chunk data)
        val dummyPacket = BitchatPacket(
            version = packet.version,
            type = 0x20,
            senderID = packet.senderID,
            recipientID = packet.recipientID,
            timestamp = packet.timestamp,
            payload = ByteArray(0),
            signature = null,
            ttl = packet.ttl,
            route = packet.route,
            isRSR = packet.isRSR
        )
        val dummyRaw = dummyPacket.toBinaryData(padding = false) ?: ByteArray(0)
        val overhead = dummyRaw.size + 13 // 13 bytes for fragment header (fragmentId (8) + index (2) + total (2) + originalType (1))

        val maxGattPayload = maxOf(20, targetMtu - 3)
        val chunkSize = if (targetMtu - 3 > overhead) {
            targetMtu - 3 - overhead
        } else {
            maxGattPayload
        }
        val chunks = if (raw.size <= maxGattPayload) {
            listOf(raw)
        } else {
            fragment(packet, chunkSize)
        }

        var notified = false
        for (target in candidates) {
            if (notifyChunks(target, chunks)) {
                notified = true
                Log.i(TAG, "Sent via notify → $target")
                break
            }
        }
        if (!notified && subscribedCentrals.isNotEmpty()) {
            notified = notifyAllSubscribed(chunks)
            if (notified) Log.i(TAG, "Sent via notify-all")
        }

        var wrote = false
        if (!notified) {
            for (target in candidates) {
                val link = clientLinks[target.uppercase()] ?: continue
                val characteristic = link.characteristic ?: continue
                if (writeChunks(link.gatt, characteristic, chunks, link)) {
                    wrote = true
                    Log.i(TAG, "Sent via client write → $target")
                    break
                }
            }
        }

        // Last resort: flood to all active client links and subscribed centrals
        if (!wrote && !notified) {
            var flooded = false
            for ((key, link) in clientLinks) {
                val characteristic = link.characteristic ?: continue
                if (writeChunks(link.gatt, characteristic, chunks, link)) {
                    flooded = true
                    Log.i(TAG, "Flooded via fallback client write → $key")
                }
            }
            if (subscribedCentrals.isNotEmpty()) {
                val notifiedAll = notifyAllSubscribed(chunks)
                if (notifiedAll) flooded = true
            }
            val ok = flooded || wrote || notified
            if (!ok) {
                Log.e(
                    TAG,
                    "No path to $deviceAddress (clientWrite=$wrote notify=$notified " +
                        "links=${clientLinks.keys} subs=${subscribedCentrals.keys})"
                )
            }
            return ok
        }
        return wrote || notified
    }

    /** BLE MACs rotate — map stored address / nickname to current scan addresses. */
    private fun resolveSendTargets(requestedAddress: String): List<String> {
        val out = linkedSetOf<String>()
        val devices = _discoveredDevices.value

        if (requestedAddress.isNotBlank()) {
            out.add(requestedAddress)
            devices.find { it.id.equals(requestedAddress, ignoreCase = true) }?.id?.let { out.add(it) }
        }

        val peerNick = devices.find { it.id.equals(requestedAddress, ignoreCase = true) }?.name
            ?: clientLinks[requestedAddress.uppercase()]?.nickname
        if (peerNick != null && !peerNick.startsWith("Nearby ")) {
            devices.filter {
                it.name.equals(peerNick, ignoreCase = true) && !it.name.startsWith("Nearby ")
            }.forEach { out.add(it.id) }
        }

        // Match by nickname embedded in outgoing packet recipient field
        if (out.size <= 1 && devices.size == 1) {
            out.add(devices.first().id)
        }

        return out.toList()
    }

    private suspend fun awaitClientLink(address: String) {
        val key = address.uppercase()
        val existing = clientLinks[key]
        if (existing?.characteristic != null && existing.ready) return

        val device = try {
            bluetoothAdapter?.getRemoteDevice(address)
        } catch (_: IllegalArgumentException) {
            null
        } ?: return

        val nick = _discoveredDevices.value.find { it.id.equals(address, ignoreCase = true) }?.name ?: ""
        if (connecting.add(key)) {
            scope.launch {
                try {
                    openClientLink(device, nick)
                } finally {
                    connecting.remove(key)
                }
            }
        }

        withTimeoutOrNull(8_000) {
            while (true) {
                val link = clientLinks[key]
                if (link?.characteristic != null && link.ready) return@withTimeoutOrNull
                if (!connecting.contains(key) && link == null) return@withTimeoutOrNull // Abort early if connect failed
                delay(100)
            }
        }
    }

    private suspend fun writeChunks(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        chunks: List<ByteArray>,
        link: ClientLink
    ): Boolean = link.writeMutex.withLock {
        link.lastUsed = System.currentTimeMillis()
        var allOk = true
        for ((i, chunk) in chunks.withIndex()) {
            val waiter = CompletableDeferred<Int>()
            link.pendingWrite = waiter
            val accepted = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeCharacteristic(
                        characteristic,
                        chunk,
                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    ) == BluetoothStatusCodes.SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    run {
                        characteristic.value = chunk
                        characteristic.writeType =
                            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                        gatt.writeCharacteristic(characteristic)
                    }
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "write denied", e)
                false
            }
            if (!accepted) {
                allOk = false
                break
            }
            val status = try {
                withTimeoutOrNull(5_000) { waiter.await() }
            } finally {
                link.pendingWrite = null
                waiter.cancel()
            }
            if (status == null) {
                // Some stacks never callback for write-without-response; treat as ok
                Log.d(TAG, "Write chunk $i no callback — assuming sent")
            } else if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Write chunk $i status=$status")
                allOk = false
                break
            }
            delay(25)
        }
        return@withLock allOk
    }

    private suspend fun notifyChunks(peerKey: String, chunks: List<ByteArray>): Boolean {
        val server = gattServer ?: return false
        val characteristic = serverCharacteristic ?: return false
        val central = subscribedCentrals[peerKey.uppercase()] ?: return false
        return notifyToCentral(server, characteristic, central, chunks)
    }

    private suspend fun notifyAllSubscribed(chunks: List<ByteArray>): Boolean {
        val server = gattServer ?: return false
        val characteristic = serverCharacteristic ?: return false
        var any = false
        for ((_, central) in subscribedCentrals) {
            if (notifyToCentral(server, characteristic, central, chunks)) any = true
        }
        return any
    }

    private suspend fun notifyToCentral(
        server: BluetoothGattServer,
        characteristic: BluetoothGattCharacteristic,
        central: BluetoothDevice,
        chunks: List<ByteArray>
    ): Boolean {
        val mutex = serverWriteMutexes.getOrPut(central.address.uppercase()) { Mutex() }
        return mutex.withLock {
            var allOk = true
            for (chunk in chunks) {
                val waiter = CompletableDeferred<Unit>()
                pendingNotifications[central.address.uppercase()] = waiter
                val ok = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        server.notifyCharacteristicChanged(central, characteristic, false, chunk) ==
                            BluetoothStatusCodes.SUCCESS
                    } else {
                        @Suppress("DEPRECATION")
                        run {
                            characteristic.value = chunk
                            server.notifyCharacteristicChanged(central, characteristic, false)
                        }
                    }
                } catch (e: SecurityException) {
                    Log.e(TAG, "notify denied", e)
                    false
                }
                
                if (ok) {
                    try {
                        withTimeoutOrNull(2_000) { waiter.await() }
                    } finally {
                        pendingNotifications.remove(central.address.uppercase())
                        waiter.cancel()
                    }
                } else {
                    pendingNotifications.remove(central.address.uppercase())
                    waiter.cancel()
                    allOk = false
                }
                delay(5) // Prevent BLE stack queue saturation
            }
            allOk
        }
    }

    private fun fragment(packet: BitchatPacket, chunkSize: Int): List<ByteArray> {
        val raw = packet.toBinaryData(padding = false) ?: return emptyList()
        val id = ByteArray(8) { Random().nextInt(256).toByte() }
        val parts = raw.toList().chunked(chunkSize).map { it.toByteArray() }
        return parts.mapIndexed { index, part ->
            val buf = ByteBuffer.allocate(13 + part.size)
            buf.put(id)
            buf.putShort(index.toShort())
            buf.putShort(parts.size.toShort())
            buf.put(packet.type)
            buf.put(part)
            BitchatPacket(
                version = packet.version,
                type = 0x20,
                senderID = packet.senderID,
                recipientID = packet.recipientID,
                timestamp = packet.timestamp,
                payload = buf.array(),
                signature = null,
                ttl = packet.ttl,
                route = packet.route,
                isRSR = packet.isRSR
            ).toBinaryData(padding = false) ?: ByteArray(0)
        }
    }

    private fun upsertDevice(device: NearbyDevice) {
        val verifiedId = verifiedMacToIdentity[device.id.uppercase()]
        val finalDeviceInput = if (verifiedId != null && device.identity != verifiedId) {
            device.copy(identity = verifiedId)
        } else {
            device
        }
        val cachedName = nicknameCache[finalDeviceInput.identity] ?: nicknameCache[finalDeviceInput.id.uppercase()]
        val finalDevice = if (cachedName != null && finalDeviceInput.name.startsWith("Nearby ")) {
            finalDeviceInput.copy(name = cachedName)
        } else {
            finalDeviceInput
        }

        _discoveredDevices.update { current ->
            val list = current.toMutableList()

            // Check if this is a MAC rotation case:
            // Incoming device has a temporary identity and a premium name,
            // and there is an existing device in the list with the same premium name and a dev_ identity.
            val isIncomingTemporary = !finalDevice.identity.startsWith("dev_")
            val isIncomingPremiumName = !finalDevice.name.startsWith("Nearby ")

            var rotatedIndex = -1
            if (isIncomingTemporary && isIncomingPremiumName) {
                rotatedIndex = list.indexOfFirst {
                    it.name.equals(finalDevice.name, ignoreCase = true) && it.identity.startsWith("dev_")
                }
            }

            if (rotatedIndex >= 0) {
                val existing = list[rotatedIndex]
                Log.d(TAG, "upsertDevice: Detected MAC rotation for ${existing.name}. Overwriting MAC: ${existing.id} -> ${finalDevice.id}")

                // Update verified mapping so subsequent packets resolve immediately
                verifiedMacToIdentity[finalDevice.id.uppercase()] = existing.identity

                // Update the existing device with the new MAC address, new RSSI, and new discovery time
                list[rotatedIndex] = existing.copy(
                    id = finalDevice.id,
                    rssi = finalDevice.rssi,
                    discoveredAt = finalDevice.discoveredAt
                )

                // Remove any temporary item with the new MAC address if it was already added
                val tempIdx = list.indexOfFirst { it.id.equals(finalDevice.id, true) && it != list[rotatedIndex] }
                if (tempIdx >= 0) {
                    list.removeAt(tempIdx)
                }

                list.sortByDescending { it.rssi }
                return@update list
            }

            val byId = list.indexOfFirst { it.id.equals(finalDevice.id, true) }
            val byIdentity = list.indexOfFirst { it.identity == finalDevice.identity }
            Log.d(TAG, "upsertDevice: incoming=$finalDevice, byId=$byId, byIdentity=$byIdentity")

            if (byId >= 0) {
                val existing = list[byId]
                val nameToKeep = if (finalDevice.name.startsWith("Nearby ") && !existing.name.startsWith("Nearby ")) {
                    existing.name
                } else {
                    finalDevice.name
                }

                if (existing.identity.startsWith("dev_") && !finalDevice.identity.startsWith("dev_")) {
                    // Keep premium name and identity, update RSSI and timestamp
                    Log.d(TAG, "upsertDevice: Keep existing premium byId -> existing=$existing")
                    list[byId] = existing.copy(
                        name = nameToKeep,
                        rssi = finalDevice.rssi,
                        discoveredAt = finalDevice.discoveredAt
                    )
                } else {
                    Log.d(TAG, "upsertDevice: Replace existing byId -> incoming=$finalDevice")
                    list[byId] = finalDevice.copy(name = nameToKeep)
                    // Remove duplicate of the same identity at another index (rotated MAC)
                    val otherIdx = list.indexOfFirst { it.identity == finalDevice.identity && !it.id.equals(finalDevice.id, true) }
                    if (otherIdx >= 0) {
                        list.removeAt(otherIdx)
                    }
                }
            } else if (byIdentity >= 0) {
                val existing = list[byIdentity]
                val nameToKeep = if (finalDevice.name.startsWith("Nearby ") && !existing.name.startsWith("Nearby ")) {
                    existing.name
                } else {
                    finalDevice.name
                }

                if (existing.identity.startsWith("dev_") && !finalDevice.identity.startsWith("dev_")) {
                    // Keep premium name, update MAC address and signal metadata
                    Log.d(TAG, "upsertDevice: Keep existing premium byIdentity -> existing=$existing, newMac=${finalDevice.id}")
                    list[byIdentity] = existing.copy(
                        id = finalDevice.id,
                        name = nameToKeep,
                        rssi = finalDevice.rssi,
                        discoveredAt = finalDevice.discoveredAt
                    )
                } else {
                    Log.d(TAG, "upsertDevice: Replace existing byIdentity -> incoming=$finalDevice")
                    list[byIdentity] = finalDevice.copy(name = nameToKeep)
                }
            } else {
                Log.d(TAG, "upsertDevice: Add new device -> incoming=$finalDevice")
                list.add(finalDevice)
            }
            list.sortByDescending { it.rssi }
            Log.d(TAG, "upsertDevice result: $list")
            list
        }
    }

    @Synchronized
    private fun pruneStale() {
        val now = System.currentTimeMillis()

        // Disconnect idle client links (> 15 seconds idle)
        val idleTimeout = 15_000
        clientLinks.forEach { (key, link) ->
            if (now - link.lastUsed > idleTimeout) {
                Log.i(TAG, "Disconnecting idle client link: ${link.address}")
                try {
                    link.gatt.disconnect()
                    link.gatt.close()
                } catch (_: Exception) {}
                clientLinks.remove(key)
            }
        }

        _discoveredDevices.update { current ->
            val kept = current.filter { device ->
                val key = device.id.uppercase()
                val isConnected = clientLinks.containsKey(key) || serverConnectedCentrals.containsKey(key)
                isConnected || (now - device.discoveredAt < 12_000)
            }
            kept.map { device ->
                val key = device.id.uppercase()
                val isConnected = clientLinks.containsKey(key) || serverConnectedCentrals.containsKey(key)
                if (isConnected) {
                    device.copy(discoveredAt = now)
                } else {
                    device
                }
            }
        }
    }

    fun sendLocalProfile(deviceAddress: String) {
        scope.launch {
            try {
                val profile = database.userProfileDao().getProfileDirect() ?: return@launch
                 val json = org.json.JSONObject().apply {
                    put("identity", localIdentity)
                    put("nickname", profile.name)
                    put("bio", profile.bio)
                    put("interests", profile.interests)
                    put("music", profile.favoriteMusic)
                    put("movies", profile.favoriteMovies)
                    put("singers", profile.singers)
                    put("career", profile.career)
                    val pubKey = chat.bitchat.core.security.KeyManager.getPublicKeyBytes()
                    if (pubKey.isNotEmpty()) {
                        put("publicKey", android.util.Base64.encodeToString(pubKey, android.util.Base64.NO_WRAP))
                    }
                }.toString()

                val myPeerId = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(localIdentity.toByteArray())
                    .copyOf(8)

                val packet = BitchatPacket(
                    version = 2,
                    type = TYPE_PROFILE,
                    senderID = myPeerId,
                    recipientID = null,
                    timestamp = System.currentTimeMillis() / 1000,
                    payload = json.toByteArray(Charsets.UTF_8),
                    signature = null,
                    ttl = 1,
                    route = null,
                    isRSR = false
                )
                Log.i(TAG, "Sending local profile to $deviceAddress payloadSize=${packet.payload.size}")
                sendPacket(deviceAddress, packet) { success ->
                    Log.d(TAG, "Sent profile status to $deviceAddress success=$success")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send local profile to $deviceAddress", e)
            }
        }
    }

    private fun refreshDeviceCache(gatt: BluetoothGatt): Boolean {
        return try {
            val refreshMethod = gatt.javaClass.getMethod("refresh")
            refreshMethod.invoke(gatt) as Boolean
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh GATT cache", e)
            false
        }
    }

    companion object {
        private const val TAG = "BLEMeshManager"
        private const val MAX_CLIENT_LINKS = 6
        private const val NICK_MAGIC_0: Byte = 0x45 // 'E'
        private const val NICK_MAGIC_1: Byte = 0x4D // 'M'
        val SERVICE_UUID: UUID = UUID.fromString("f47b5e2d-4a9e-4c5a-9b3f-8e1d2c3a4b5c")
        val CHAR_UUID: UUID = UUID.fromString("a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        val TYPE_PROFILE: Byte = 0x05.toByte()
        val TYPE_DELIVERY_ACK: Byte = 0x06.toByte()
    }
}
