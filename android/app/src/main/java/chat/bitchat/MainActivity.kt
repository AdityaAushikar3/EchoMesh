package chat.bitchat

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import chat.bitchat.core.bluetooth.BluetoothRepository
import chat.bitchat.domain.router.MessageRouter
import chat.bitchat.ui.EchoMeshNavHost
import chat.bitchat.ui.theme.EchoMeshTheme
import chat.bitchat.ui.theme.EchoVoid
import chat.bitchat.ui.theme.ThemeConfig
import androidx.compose.runtime.getValue
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var messageRouter: MessageRouter

    @Inject
    lateinit var bluetoothRepository: BluetoothRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            buildList {
                add(android.Manifest.permission.BLUETOOTH_SCAN)
                add(android.Manifest.permission.BLUETOOTH_ADVERTISE)
                add(android.Manifest.permission.BLUETOOTH_CONNECT)
                add(android.Manifest.permission.ACCESS_FINE_LOCATION)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    add(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }.toTypedArray()
        } else {
            arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }

        try {
            requestPermissions(permissions, 101)
        } catch (_: Exception) {
        }

        // GATT server often fails if created before permissions — retry now.
        window.decorView.postDelayed({
            bluetoothRepository.ensureReady()
        }, 800)

        val prefs = getSharedPreferences("echomesh_prefs", MODE_PRIVATE)
        val savedTheme = prefs.getString("theme_mode", "system") ?: "system"
        ThemeConfig.themeMode.value = savedTheme

        setContent {
            val themeMode by ThemeConfig.themeMode
            val isDark = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            EchoMeshTheme(darkTheme = isDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = EchoVoid
                ) {
                    EchoMeshNavHost()
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            bluetoothRepository.ensureReady()
        }
    }
}
