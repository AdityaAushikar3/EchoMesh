package chat.bitchat.feature.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import chat.bitchat.core.bluetooth.BLEMeshManager
import chat.bitchat.core.bluetooth.BluetoothRepository
import chat.bitchat.core.bluetooth.NearbyDevice
import chat.bitchat.data.database.EchoMeshDatabase
import chat.bitchat.data.database.MessageEntity
import chat.bitchat.data.database.Peer
import chat.bitchat.domain.repository.ProfileRepository
import chat.bitchat.domain.router.MessageRouter
import chat.bitchat.ui.util.displayName
import chat.bitchat.ui.util.decodePeerRouteId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val messageRouter: MessageRouter,
    private val database: EchoMeshDatabase,
    private val bluetoothRepository: BluetoothRepository,
    profileRepository: ProfileRepository,
    private val bleMeshManager: BLEMeshManager
) : ViewModel() {

    /** Route arg — preferably BLE MAC; may be a legacy nickname. */
    val peerId: String = (decodePeerRouteId(savedStateHandle.get<String>("peerId") ?: "")).also {
        try {
            android.util.Log.d("ChatViewModel", "[IDENTITY_TRACE] finalChatPeerId=$it")
        } catch (_: Throwable) {
            println("ChatViewModel: [IDENTITY_TRACE] finalChatPeerId=$it")
        }
    }

    private val messageDao = database.messageDao()
    private val peerDao = database.peerDao()

    private val resolvedAddress: StateFlow<String> = combine(
        bluetoothRepository.getDiscoveredDevices(),
        peerDao.getAllPeers()
    ) { devices, peers ->
        resolveAddress(peerId, devices, peers, bleMeshManager.verifiedMacToIdentity)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), peerId)

    val peerState: StateFlow<Peer?> = peerDao.getAllPeers()
        .map { peers ->
            peers.find { it.peerID.equals(peerId, ignoreCase = true) || it.nickname.equals(peerId, ignoreCase = true) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val displayName: StateFlow<String> = combine(
        bluetoothRepository.getDiscoveredDevices(),
        peerState,
        resolvedAddress
    ) { devices, peer, address ->
        val device = devices.find {
            (address.isNotBlank() && it.id.equals(address, ignoreCase = true)) ||
                    it.identity.equals(peerId, ignoreCase = true)
        }
        device?.displayName()
            ?: peer?.nickname?.takeIf { it.isNotBlank() && !it.contains(":") }
            ?: peerId.takeIf { it.isNotBlank() && !it.contains(":") }
            ?: "Someone"
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), peerId.ifBlank { "Someone" })

    val youName: StateFlow<String> = profileRepository.getProfile()
        .map { it?.name?.ifBlank { "Me" } ?: "Me" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Me")

    val selfIdentifiers: StateFlow<List<String>> = youName.map { name ->
        listOf(name, "Me", bluetoothRepository.localIdentity)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("Me", bluetoothRepository.localIdentity))

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val messagesState: StateFlow<List<MessageEntity>> = combine(
        flowOf(peerId),
        displayName,
        selfIdentifiers
    ) { pId, name, selfIds ->
        Triple(pId, name, selfIds)
    }.flatMapLatest { (pId, name, selfIds) ->
        if (pId.isBlank() && name.isBlank()) flowOf(emptyList())
        else messageDao.getPrivateMessagesForConversation(pId, name, selfIds)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val nearbyRssi: StateFlow<Int?> = combine(
        bluetoothRepository.getDiscoveredDevices(),
        resolvedAddress
    ) { devices, address ->
        devices.find {
            (address.isNotBlank() && it.id.equals(address, ignoreCase = true)) ||
                    it.identity.equals(peerId, ignoreCase = true) ||
                    it.id == peerId
        }?.rssi
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    fun sendMessage(content: String) {
        if (content.isBlank() || _isSending.value) return
        _isSending.value = true

        viewModelScope.launch {
            val devices = bluetoothRepository.getDiscoveredDevices().firstOrNull().orEmpty()
            val peers = peerDao.getAllPeers().firstOrNull().orEmpty()
            val address = resolveAddress(peerId, devices, peers, bleMeshManager.verifiedMacToIdentity)
            val name = devices.find { it.id.equals(address, ignoreCase = true) }?.displayName()
                ?: peers.find { it.peerID.equals(address, ignoreCase = true) }?.nickname
                ?: displayName.value

            if (address.isBlank() || !looksLikeBleAddress(address)) {
                android.util.Log.e("ChatViewModel", "Cannot send: no BLE address for $peerId")
                _isSending.value = false
                return@launch
            }

            messageRouter.sendMessage(
                recipientAddress = address,
                recipientName = name,
                content = content,
                isPrivate = true
            ) {
                _isSending.value = false
            }
        }
    }

    fun clearChatHistory() {
        viewModelScope.launch {
            val address = resolvedAddress.value.ifBlank { peerId }
            val name = displayName.value
            val selfIds = selfIdentifiers.value
            messageDao.deleteMessagesForConversation(address, name, selfIds)
        }
    }

    companion object {
        fun looksLikeBleAddress(value: String): Boolean {
            return value.contains(":") || value.length >= 12
        }

        fun resolveAddress(
            peerId: String,
            devices: List<NearbyDevice>,
            peers: List<Peer>,
            verifiedMacToIdentity: Map<String, String>
        ): String {
            if (peerId.isBlank()) return ""

            // 1. Current discoveredDevices
            val scannedDevice = devices.find { it.identity.equals(peerId, ignoreCase = true) }
            if (scannedDevice != null) {
                val mac = scannedDevice.id
                android.util.Log.d("ChatViewModel", "[CHAT_RESOLVE] peerId=$peerId source=discoveredDevices mac=$mac")
                return mac
            }

            // 2. Existing verified MAC -> stable identity cache
            val cachedMac = verifiedMacToIdentity.entries.find { it.value.equals(peerId, ignoreCase = true) }?.key
            if (cachedMac != null) {
                android.util.Log.d("ChatViewModel", "[CHAT_RESOLVE] peerId=$peerId source=verifiedMacToIdentity mac=$cachedMac")
                return cachedMac
            }

            // 3. Database Peer record if the peerID itself is a MAC address (legacy/fallback)
            val dbPeer = peers.find { it.peerID.equals(peerId, ignoreCase = true) }
            if (dbPeer != null && looksLikeBleAddress(dbPeer.peerID)) {
                val mac = dbPeer.peerID
                android.util.Log.d("ChatViewModel", "[CHAT_RESOLVE] peerId=$peerId source=database mac=$mac")
                return mac
            }

            // 4. Backward compatibility fallback for raw BLE MAC addresses
            if (looksLikeBleAddress(peerId)) {
                val mac = devices.find { it.id.equals(peerId, ignoreCase = true) }?.id ?: peerId
                android.util.Log.d("ChatViewModel", "[CHAT_RESOLVE] peerId=$peerId source=directMac mac=$mac")
                return mac
            }

            android.util.Log.d("ChatViewModel", "[CHAT_RESOLVE] peerId=$peerId source=NONE mac=")
            return ""
        }
    }
}
