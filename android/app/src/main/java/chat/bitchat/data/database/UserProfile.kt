package chat.bitchat.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: Int = 1,
    val name: String,
    val bio: String,
    val favoriteMovies: String = "",
    val favoriteMusic: String = "",
    val interests: String = "",
    val singers: String = "",
    val career: String = ""
)
