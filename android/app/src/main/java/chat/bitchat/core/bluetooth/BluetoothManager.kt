package chat.bitchat.core.bluetooth

import kotlinx.coroutines.flow.Flow

interface BluetoothManager {
    val localIdentity: String
    val isScanning: Flow<Boolean>
    val isAdvertising: Flow<Boolean>
    val discoveredDevices: Flow<List<NearbyDevice>>

    fun startScanning()
    fun stopScanning()
    fun startAdvertising()
    fun stopAdvertising()
    fun refreshAdvertisingIdentity()
    fun ensureReady()
}
