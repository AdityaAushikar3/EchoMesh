package chat.bitchat.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import chat.bitchat.core.bluetooth.BluetoothRepository
import chat.bitchat.core.bluetooth.NearbyDevice
import chat.bitchat.data.database.EchoMeshDatabase
import chat.bitchat.domain.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val bluetoothRepository: BluetoothRepository,
    private val database: EchoMeshDatabase,
    profileRepository: ProfileRepository
) : ViewModel() {

    private val peerDao = database.peerDao()
    private val messageDao = database.messageDao()

    val isScanning: StateFlow<Boolean> = bluetoothRepository.isDiscovering()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val discoveredDevices: StateFlow<List<NearbyDevice>> = bluetoothRepository.getDiscoveredDevices()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val youName: StateFlow<String> = profileRepository.getProfile()
        .map { it?.name?.ifBlank { "You" } ?: "You" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "You")

    fun toggleScanning() {
        if (isScanning.value) bluetoothRepository.stopDiscovery()
        else bluetoothRepository.startDiscovery()
    }

    fun deletePeerAndHistory(peerName: String) {
        viewModelScope.launch {
            val peerList = peerDao.getAllPeers().firstOrNull() ?: emptyList()
            val peer = peerList.find { it.nickname == peerName || it.peerID == peerName }
            if (peer != null) {
                peerDao.deletePeer(peer)
                messageDao.deleteMessagesForPeer(peer.peerID)
                if (peer.nickname.isNotBlank() && peer.nickname != peer.peerID) {
                    messageDao.deleteMessagesForPeer(peer.nickname)
                }
            } else {
                messageDao.deleteMessagesForPeer(peerName)
            }
        }
    }
}
