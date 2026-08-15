package chat.bitchat.data.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [UserProfile::class, Peer::class, MessageEntity::class],
    version = 7,
    exportSchema = false
)
abstract class EchoMeshDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
    abstract fun peerDao(): PeerDao
    abstract fun messageDao(): MessageDao
}
