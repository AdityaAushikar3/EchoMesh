package chat.bitchat.core.bluetooth

import kotlinx.coroutines.flow.Flow

interface BluetoothRepository {
    val localIdentity: String
    fun getDiscoveredDevices(): Flow<List<NearbyDevice>>
    fun isDiscovering(): Flow<Boolean>
    fun startDiscovery()
    fun stopDiscovery()
    fun refreshAdvertisingIdentity()
    fun ensureReady()
}
