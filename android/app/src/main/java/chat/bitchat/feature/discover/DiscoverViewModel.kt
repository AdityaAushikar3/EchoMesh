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
    private val blockedPeerDao = database.blockedPeerDao()
    val selectedTab = MutableStateFlow(DiscoverTab.All)

    private val parsedInterestsCache = java.util.concurrent.ConcurrentHashMap<String, List<Interest>>()

    private fun getParsedInterests(cacheKey: String, interestsString: String): List<Interest> {
        val existing = parsedInterestsCache[cacheKey]
        if (existing != null) return existing
        val parsed = parseInterests(interestsString)
        parsedInterestsCache[cacheKey] = parsed
        return parsed
    }

    val discoverItems: StateFlow<List<DiscoverItem>> = combine(
        bluetoothRepository.getDiscoveredDevices(),
        peerDao.getAllPeers(),
        profileRepository.getProfile(),
        blockedPeerDao.getAllBlockedPeers(),
        selectedTab
    ) { devices, peers, myProfile, blockedList, tab ->
        val blockedIds = blockedList.map { it.peerID.lowercase() }.toSet()
        val blockedNames = blockedList.filter { it.nickname.isNotBlank() }.map { it.nickname.lowercase() }.toSet()

        val activeDevices = devices.filter { device ->
            device.identity.lowercase() !in blockedIds &&
                    device.id.lowercase() !in blockedIds &&
                    device.name.lowercase() !in blockedNames
        }

        val myInterests = myProfile?.let {
            val cacheKey = "me-${it.interests}-${it.favoriteMusic}-${it.favoriteMovies}-${it.singers}-${it.career}"
            val interestsStr = listOfNotNull(
                it.interests,
                it.favoriteMusic,
                it.favoriteMovies,
                it.singers,
                it.career
            ).joinToString(", ")
            getParsedInterests(cacheKey, interestsStr)
        } ?: emptyList()

        val peerByIdMap = peers.associateBy { it.peerID.lowercase() }
        val peerByNameMap = peers.filter { it.nickname.isNotBlank() }.associateBy { it.nickname.lowercase() }

        val items = activeDevices.map { device ->
            val peer = peerByIdMap[device.identity.lowercase()]
                ?: peerByIdMap[device.id.lowercase()]
                ?: peerByNameMap[device.name.lowercase()]
            val theirInterests = peer?.let {
                val cacheKey = "${it.peerID}-${it.interests}-${it.favoriteMusic}-${it.favoriteMovies}-${it.singers}-${it.career}"
                val interestsStr = listOfNotNull(
                    it.interests,
                    it.favoriteMusic,
                    it.favoriteMovies,
                    it.singers,
                    it.career
                ).joinToString(", ")
                getParsedInterests(cacheKey, interestsStr)
            } ?: emptyList()
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
