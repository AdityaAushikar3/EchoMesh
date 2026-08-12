package chat.bitchat.data.repository

import chat.bitchat.core.bluetooth.BluetoothManager
import chat.bitchat.core.bluetooth.BluetoothRepository
import chat.bitchat.core.bluetooth.NearbyDevice
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BluetoothRepositoryImpl @Inject constructor(
    private val bluetoothManager: BluetoothManager
) : BluetoothRepository {

    override val localIdentity: String
        get() = bluetoothManager.localIdentity

    override fun getDiscoveredDevices(): Flow<List<NearbyDevice>> {
        return bluetoothManager.discoveredDevices
    }

    override fun isDiscovering(): Flow<Boolean> {
        return bluetoothManager.isScanning
    }

    override fun startDiscovery() {
        bluetoothManager.startScanning()
    }

    override fun stopDiscovery() {
        bluetoothManager.stopScanning()
    }

    override fun refreshAdvertisingIdentity() {
        bluetoothManager.refreshAdvertisingIdentity()
    }

    override fun ensureReady() {
        bluetoothManager.ensureReady()
    }
}
