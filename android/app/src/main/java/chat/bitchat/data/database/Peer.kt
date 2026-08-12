package chat.bitchat.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "peers")
data class Peer(
    @PrimaryKey val peerID: String,
    val nickname: String,
    val trustLevel: String, // Unknown, Casual, Trusted, Verified
    val lastSeen: Long,
    val bio: String = "",
    val interests: String = "",
    val favoriteMovies: String = "",
    val favoriteMusic: String = "",
    val singers: String = "",
    val career: String = ""
)
