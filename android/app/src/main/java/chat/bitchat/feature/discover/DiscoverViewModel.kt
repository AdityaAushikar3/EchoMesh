package chat.bitchat.feature.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import chat.bitchat.core.bluetooth.BluetoothRepository
import chat.bitchat.core.bluetooth.NearbyDevice
import chat.bitchat.data.database.EchoMeshDatabase
import chat.bitchat.data.database.Peer
import chat.bitchat.domain.repository.ProfileRepository
import chat.bitchat.ui.util.Interest
import chat.bitchat.ui.util.parseInterests
import chat.bitchat.ui.util.sharedInterests
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class DiscoverTab { All, Matches }

data class DiscoverItem(
    val device: NearbyDevice,
    val peer: Peer?,
    val sharedInterests: List<Interest>
)

@HiltViewModel
class DiscoverViewModel @Inject constructor(
    bluetoothRepository: BluetoothRepository,
    profileRepository: ProfileRepository,
    database: EchoMeshDatabase
) : ViewModel() {

    private val peerDao = database.peerDao()
    val selectedTab = MutableStateFlow(DiscoverTab.All)

    val discoverItems: StateFlow<List<DiscoverItem>> = combine(
        bluetoothRepository.getDiscoveredDevices(),
        peerDao.getAllPeers(),
        profileRepository.getProfile(),
        selectedTab
    ) { devices, peers, myProfile, tab ->
        val myInterests = parseInterests(
            listOfNotNull(
                myProfile?.interests,
                myProfile?.favoriteMusic,
                myProfile?.favoriteMovies,
                myProfile?.singers,
                myProfile?.career
            ).joinToString(", ")
        )

        val items = devices.map { device ->
            val peer = peers.find { it.peerID.equals(device.id, ignoreCase = true) || it.nickname == device.name }
            val theirInterests = parseInterests(
                listOfNotNull(
                    peer?.interests,
                    peer?.favoriteMusic,
                    peer?.favoriteMovies,
                    peer?.singers,
                    peer?.career
                ).joinToString(", ")
            )
            val shared = sharedInterests(myInterests, theirInterests)

            DiscoverItem(
                device = device,
                peer = peer,
                sharedInterests = shared
            )
        }

        when (tab) {
            DiscoverTab.All -> items.sortedByDescending { it.device.rssi }
            DiscoverTab.Matches -> items.filter { it.sharedInterests.isNotEmpty() }
                .sortedByDescending { it.device.rssi }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectTab(tab: DiscoverTab) {
        selectedTab.value = tab
    }
}
