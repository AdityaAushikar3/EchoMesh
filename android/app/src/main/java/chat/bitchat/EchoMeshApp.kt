package chat.bitchat

import android.app.Application
import chat.bitchat.core.security.KeyManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class EchoMeshApp : Application() {
    override fun onCreate() {
        super.onCreate()
        KeyManager.init(this)
    }
}
