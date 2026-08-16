package chat.bitchat.feature.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import chat.bitchat.core.bluetooth.BluetoothRepository
import chat.bitchat.data.database.EchoMeshDatabase
import chat.bitchat.data.database.MessageEntity
import chat.bitchat.domain.repository.ProfileRepository
import chat.bitchat.ui.util.displayName
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConversationPreview(
    val peerKey: String,
    val displayName: String,
    val lastMessage: String,
    val timestamp: Long,
    val isNearby: Boolean,
    val isUnread: Boolean = false,
    val isBlocked: Boolean = false
)

@HiltViewModel
class ChatsViewModel @Inject constructor(
    private val database: EchoMeshDatabase,
    bluetoothRepository: BluetoothRepository,
    profileRepository: ProfileRepository
) : ViewModel() {

    private val messageDao = database.messageDao()
    private val blockedPeerDao = database.blockedPeerDao()

    val nearbyCount: StateFlow<Int> = bluetoothRepository.getDiscoveredDevices()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val conversations: StateFlow<List<ConversationPreview>> = combine(
        messageDao.getLatestMessagePerConversation(),
        bluetoothRepository.getDiscoveredDevices(),
        database.peerDao().getAllPeers(),
        blockedPeerDao.getAllBlockedPeers(),
        profileRepository.getProfile()
    ) { latestMessages, devices, peers, blockedList, profile ->
        val selfNames = setOfNotNull(
            profile?.name?.takeIf { it.isNotBlank() },
            "Me",
            bluetoothRepository.localIdentity
        ).map { it.lowercase() }.toSet()

        val nearbyById = devices.associateBy { it.id.uppercase() }
        val nearbyByIdentity = devices.associateBy { it.identity.uppercase() }

        latestMessages.mapNotNull { msg ->
            val key = conversationKey(msg, selfNames)
            if (key.isBlank()) return@mapNotNull null

            val peer = peers.find { it.peerID.equals(key, ignoreCase = true) }
            val nearby = nearbyByIdentity[key.uppercase()] ?: nearbyById[key.uppercase()]
            val name = nearby?.displayName()
                ?: peer?.nickname?.takeIf { it.isNotBlank() && !it.contains(":") }
                ?: (if (msg.isOutgoing) msg.recipientNickname else msg.sender)
                    ?.takeIf { it.isNotBlank() && it.lowercase() !in selfNames }
                ?: key

            val isUnread = !msg.isOutgoing && msg.deliveryStatus == "received"
            val isBlocked = blockedList.any {
                it.peerID.equals(key, ignoreCase = true) ||
                        (it.nickname.isNotBlank() && it.nickname.equals(name, ignoreCase = true))
            }

            ConversationPreview(
                peerKey = key,
                displayName = name,
                lastMessage = msg.content,
                timestamp = msg.timestamp,
                isNearby = nearby != null,
                isUnread = isUnread,
                isBlocked = isBlocked
            )
        }.distinctBy { it.peerKey }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteConversation(peerKey: String, displayName: String) {
        viewModelScope.launch {
            val peer = database.peerDao().getAllPeersDirect().find {
                it.peerID.equals(peerKey, ignoreCase = true) ||
                        it.nickname.equals(displayName, ignoreCase = true)
            }
            if (peer != null) {
                database.peerDao().deletePeer(peer)
            }
            database.messageDao().deleteMessagesForPeer(peerKey)
            if (displayName.isNotBlank() && displayName != peerKey) {
                database.messageDao().deleteMessagesForPeer(displayName)
            }
        }
    }

    private fun conversationKey(msg: MessageEntity, selfNames: Set<String>): String {
        val senderIsSelf = msg.sender.lowercase() in selfNames
        val recipient = msg.recipientNickname?.takeIf { it.isNotBlank() }
        return when {
            !senderIsSelf -> msg.sender
            recipient != null && recipient.lowercase() !in selfNames -> recipient
            else -> msg.sender
        }
    }
}
