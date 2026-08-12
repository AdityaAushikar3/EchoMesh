package chat.bitchat.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import chat.bitchat.data.database.EchoMeshDatabase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val database: EchoMeshDatabase
) : ViewModel() {

    fun clearAllMessagesAndPeers() {
        viewModelScope.launch {
            database.messageDao().clearAllMessages()
            database.peerDao().getAllPeers().firstOrNull()?.forEach {
                database.peerDao().deletePeer(it)
            }
        }
    }
}
