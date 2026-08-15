package chat.bitchat.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "blocked_peers")
data class BlockedPeer(
    @PrimaryKey
    val peerID: String,
    val nickname: String = "",
    val blockedAt: Long = System.currentTimeMillis()
)
