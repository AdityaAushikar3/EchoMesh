package chat.bitchat.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import chat.bitchat.core.proximity.ProximityAlertManager
import chat.bitchat.data.database.BlockedPeer
import chat.bitchat.data.database.EchoMeshDatabase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val database: EchoMeshDatabase,
    private val proximityAlertManager: ProximityAlertManager
) : ViewModel() {

    private val blockedPeerDao = database.blockedPeerDao()

    val blockedPeers: StateFlow<List<BlockedPeer>> = blockedPeerDao.getAllBlockedPeers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val proximityAlertsEnabled: StateFlow<Boolean> = proximityAlertManager.enabled
    val proximityThreshold: StateFlow<Int> = proximityAlertManager.threshold

    fun setProximityAlertsEnabled(enabled: Boolean) {
        proximityAlertManager.setEnabled(enabled)
    }

    fun setProximityThreshold(threshold: Int) {
        proximityAlertManager.setThreshold(threshold)
    }

    fun unblockPeer(peerId: String) {
        viewModelScope.launch {
            blockedPeerDao.unblockPeer(peerId)
        }
    }

    fun clearAllMessagesAndPeers() {
        viewModelScope.launch {
            database.messageDao().clearAllMessages()
            database.peerDao().clearAllPeers()
            blockedPeerDao.clearAllBlockedPeers()
        }
    }

    fun setThemeMode(context: android.content.Context, mode: String) {
        val prefs = context.getSharedPreferences("echomesh_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("theme_mode", mode).apply()
        chat.bitchat.ui.theme.ThemeConfig.themeMode.value = mode
    }
}
