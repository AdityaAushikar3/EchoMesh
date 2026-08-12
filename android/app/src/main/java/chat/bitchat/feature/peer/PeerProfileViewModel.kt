package chat.bitchat.feature.peer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import chat.bitchat.core.bluetooth.BluetoothRepository
import chat.bitchat.core.bluetooth.NearbyDevice
import chat.bitchat.data.database.EchoMeshDatabase
import chat.bitchat.domain.repository.ProfileRepository
import chat.bitchat.ui.util.Interest
import chat.bitchat.ui.util.decodePeerRouteId
import chat.bitchat.ui.util.displayName
import chat.bitchat.ui.util.parseInterests
import chat.bitchat.ui.util.rssiToMeters
import chat.bitchat.ui.util.sharedInterests
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class PeerProfileUi(
    val peerKey: String,
    val displayName: String,
    val address: String,
    val meters: Int?,
    val isNearby: Boolean,
    val bio: String,
    val movies: List<Interest> = emptyList(),
    val music: List<Interest> = emptyList(),
    val singers: List<Interest> = emptyList(),
    val career: List<Interest> = emptyList(),
    val interests: List<Interest> = emptyList(),
    val sharedCount: Int,
    val sharedInterests: List<Interest> = emptyList()
)

@HiltViewModel
class PeerProfileViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    bluetoothRepository: BluetoothRepository,
    profileRepository: ProfileRepository,
    database: EchoMeshDatabase
) : ViewModel() {

    val peerId: String = decodePeerRouteId(savedStateHandle.get<String>("peerId") ?: "")

    val uiState: StateFlow<PeerProfileUi> = combine(
        bluetoothRepository.getDiscoveredDevices(),
        profileRepository.getProfile(),
        database.peerDao().getAllPeers()
    ) { devices, myProfile, peers ->
        val device: NearbyDevice? = devices.find {
            it.id.equals(peerId, ignoreCase = true) ||
                    it.identity.equals(peerId, ignoreCase = true) ||
                    it.name == peerId
        }
        val peer = peers.find {
            it.peerID.equals(peerId, ignoreCase = true) || it.nickname == peerId
        }
        val stableId = device?.identity?.takeIf { it.startsWith("dev_") }
            ?: peer?.peerID?.takeIf { it.startsWith("dev_") }
            ?: peerId
        val address = device?.id ?: peer?.peerID ?: peerId
        val name = device?.displayName()
            ?: peer?.nickname?.takeIf { it.isNotBlank() && !it.contains(":") }
            ?: peerId.takeIf { !it.contains(":") }
            ?: "Someone"

        val theirMovies = parseInterests(peer?.favoriteMovies)
        val theirMusic = parseInterests(peer?.favoriteMusic)
        val theirSingers = parseInterests(peer?.singers)
        val theirCareer = parseInterests(peer?.career)
        val theirInterests = parseInterests(peer?.interests)

        val myInterests = parseInterests(
            listOfNotNull(
                myProfile?.interests,
                myProfile?.favoriteMusic,
                myProfile?.favoriteMovies,
                myProfile?.singers,
                myProfile?.career
            ).joinToString(", ")
        )
        val allTheirInterests = theirMovies + theirMusic + theirSingers + theirCareer + theirInterests
        val shared = sharedInterests(myInterests, allTheirInterests)

        PeerProfileUi(
            peerKey = stableId,
            displayName = name,
            address = address,
            meters = device?.rssi?.let { rssiToMeters(it) },
            isNearby = device != null,
            bio = peer?.bio ?: "",
            movies = theirMovies,
            music = theirMusic,
            singers = theirSingers,
            career = theirCareer,
            interests = theirInterests,
            sharedCount = shared.size,
            sharedInterests = shared
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        PeerProfileUi(
            peerKey = peerId,
            displayName = peerId.ifBlank { "Someone" },
            address = peerId,
            meters = null,
            isNearby = false,
            bio = "",
            sharedCount = 0
        )
    )
}
