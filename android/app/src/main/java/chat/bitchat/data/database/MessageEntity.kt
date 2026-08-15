package chat.bitchat.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["isPrivate", "timestamp"]),
        Index(value = ["sender"]),
        Index(value = ["recipientNickname"]),
        Index(value = ["conversationId"])
    ]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val sender: String,
    val content: String,
    val timestamp: Long,
    val isPrivate: Boolean,
    val recipientNickname: String?,
    val isRelay: Boolean,
    /** Stable BLE address of the other party — survives nickname changes. */
    val conversationId: String = "",
    val isOutgoing: Boolean = false,
    /** sending | sent | failed | received */
    val deliveryStatus: String = if (isOutgoing) "sent" else "received"
)
