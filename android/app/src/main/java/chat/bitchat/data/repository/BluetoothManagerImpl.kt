package chat.bitchat.data.repository

import android.content.Context
import android.content.Intent
import android.os.Build
import chat.bitchat.core.bluetooth.BLEMeshManager
import chat.bitchat.core.bluetooth.BLEMeshService
import chat.bitchat.core.bluetooth.BluetoothManager
import chat.bitchat.core.bluetooth.NearbyDevice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BluetoothManagerImpl @Inject constructor(
    private val bleMeshManager: BLEMeshManager,
    @ApplicationContext private val context: Context
) : BluetoothManager {

    override val localIdentity: String
        get() = bleMeshManager.localIdentity

    override val isScanning: Flow<Boolean> = bleMeshManager.isScanning
    override val isAdvertising: Flow<Boolean> = bleMeshManager.isAdvertising
    override val discoveredDevices: Flow<List<NearbyDevice>> = bleMeshManager.discoveredDevices

    override fun startScanning() {
        startServiceIfNeeded()
        bleMeshManager.ensureReady()
        bleMeshManager.startScanning()
    }

    override fun stopScanning() {
        bleMeshManager.stopScanning()
        bleMeshManager.stopAdvertising()
        stopServiceIfIdle()
    }

    override fun startAdvertising() {
        startServiceIfNeeded()
        bleMeshManager.startAdvertising()
    }


    override fun stopAdvertising() {
        bleMeshManager.stopAdvertising()
        stopServiceIfIdle()
    }

    override fun refreshAdvertisingIdentity() {
        startServiceIfNeeded()
        bleMeshManager.refreshAdvertisingIdentity()
    }

    override fun ensureReady() {
        startServiceIfNeeded()
        bleMeshManager.ensureReady()
    }

    private fun startServiceIfNeeded() {
        try {
            val intent = Intent(context, BLEMeshService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: IllegalStateException) {
            // Usually ForegroundServiceStartNotAllowedException on Android 12+
            android.util.Log.e("BluetoothManagerImpl", "Foreground service start not allowed in background", e)
        } catch (e: SecurityException) {
            // Permission issues
            android.util.Log.e("BluetoothManagerImpl", "Foreground service denied due to permissions", e)
        } catch (e: Exception) {
            android.util.Log.e("BluetoothManagerImpl", "Failed to start BLE service", e)
        }
    }

    private fun stopServiceIfIdle() {
        if (!bleMeshManager.isScanning.value && !bleMeshManager.isAdvertising.value) {
            try {
                context.stopService(Intent(context, BLEMeshService::class.java))
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}
