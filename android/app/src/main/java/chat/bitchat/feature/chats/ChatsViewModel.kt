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
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ConversationPreview(
    val peerKey: String,
    val displayName: String,
    val lastMessage: String,
    val timestamp: Long,
    val isNearby: Boolean
)

@HiltViewModel
class ChatsViewModel @Inject constructor(
    database: EchoMeshDatabase,
    bluetoothRepository: BluetoothRepository,
    profileRepository: ProfileRepository
) : ViewModel() {

    private val messageDao = database.messageDao()

    val conversations: StateFlow<List<ConversationPreview>> = combine(
        messageDao.getAllMessages(),
        bluetoothRepository.getDiscoveredDevices(),
        database.peerDao().getAllPeers(),
        profileRepository.getProfile()
    ) { messages, devices, peers, profile ->
        val selfNames = setOfNotNull(
            profile?.name?.takeIf { it.isNotBlank() },
            "Me",
            bluetoothRepository.localIdentity
        ).map { it.lowercase() }.toSet()

        val nearbyById = devices.associateBy { it.id.uppercase() }
        val nearbyByIdentity = devices.associateBy { it.identity.uppercase() }

        messages
            .filter { it.isPrivate }
            .groupBy { msg -> conversationKey(msg, selfNames) }
            .mapNotNull { (key, msgs) ->
                if (key.isBlank()) return@mapNotNull null
                val last = msgs.maxByOrNull { it.timestamp } ?: return@mapNotNull null
                val peer = peers.find { it.peerID.equals(key, ignoreCase = true) }
                val nearby = nearbyByIdentity[key.uppercase()] ?: nearbyById[key.uppercase()]
                val name = nearby?.displayName()
                    ?: peer?.nickname?.takeIf { it.isNotBlank() && !it.contains(":") }
                    ?: msgs.mapNotNull { m ->
                        when {
                            m.isOutgoing -> m.recipientNickname
                            else -> m.sender
                        }?.takeIf { it.isNotBlank() && it.lowercase() !in selfNames }
                    }.lastOrNull()
                    ?: key

                ConversationPreview(
                    peerKey = key,
                    displayName = name,
                    lastMessage = last.content,
                    timestamp = last.timestamp,
                    isNearby = nearby != null
                )
            }
            .sortedByDescending { it.timestamp }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
