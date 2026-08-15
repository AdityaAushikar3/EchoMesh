package chat.bitchat.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    fun getProfile(): Flow<UserProfile?>

    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    suspend fun getProfileDirect(): UserProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProfile(profile: UserProfile)
}

@Dao
interface PeerDao {
    @Query("SELECT * FROM peers ORDER BY lastSeen DESC")
    fun getAllPeers(): Flow<List<Peer>>

    @Query("SELECT * FROM peers ORDER BY lastSeen DESC")
    suspend fun getAllPeersDirect(): List<Peer>

    @Query("DELETE FROM peers WHERE peerID LIKE '%:%' OR peerID NOT LIKE 'dev_%'")
    suspend fun deleteLegacyPeers()

    @Query("SELECT * FROM peers WHERE peerID = :peerId LIMIT 1")
    fun getPeerById(peerId: String): Flow<Peer?>

    @Query("SELECT * FROM peers WHERE peerID = :identifier OR nickname = :identifier LIMIT 1")
    suspend fun getPeerByIdDirect(identifier: String): Peer?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPeer(peer: Peer)

    @Query("UPDATE peers SET nickname = :nickname, lastSeen = :lastSeen WHERE peerID = :peerId")
    suspend fun updatePeerPresence(peerId: String, nickname: String, lastSeen: Long)

    @Query("UPDATE peers SET bio = :bio, interests = :interests, favoriteMovies = :movies, favoriteMusic = :music, singers = :singers, career = :career WHERE peerID = :peerId")
    suspend fun updatePeerProfile(
        peerId: String,
        bio: String,
        interests: String,
        movies: String,
        music: String,
        singers: String,
        career: String
    )

    @Query("UPDATE peers SET publicKey = :publicKey WHERE peerID = :peerId")
    suspend fun updatePeerPublicKey(peerId: String, publicKey: String)

    @Transaction
    suspend fun upsertPeerPresence(peerId: String, nickname: String, lastSeen: Long) {
        val existing = getPeerByIdDirect(peerId)
        if (existing != null) {
            updatePeerPresence(peerId, nickname, lastSeen)
        } else {
            insertPeer(Peer(peerID = peerId, nickname = nickname, trustLevel = "Casual", lastSeen = lastSeen))
        }
    }

    @Query("DELETE FROM peers")
    suspend fun clearAllPeers()

    @Delete
    suspend fun deletePeer(peer: Peer)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<MessageEntity>>

    @Query(
        """
        SELECT * FROM messages
        WHERE isPrivate = 1 AND (
            conversationId = :conversationId OR conversationId = :fallbackName
            OR
            (sender IN (:selfIdentifiers) AND (recipientNickname = :conversationId OR recipientNickname = :fallbackName))
            OR
            ((sender = :conversationId OR sender = :fallbackName) AND recipientNickname IN (:selfIdentifiers))
        )
        ORDER BY timestamp ASC
        """
    )
    fun getPrivateMessagesForConversation(
        conversationId: String,
        fallbackName: String,
        selfIdentifiers: List<String>
    ): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE (sender = :peerId OR recipientNickname = :peerId) AND isPrivate = 1 ORDER BY timestamp ASC")
    fun getPrivateMessagesForPeer(peerId: String): Flow<List<MessageEntity>>

    @Query("""
        SELECT m.* FROM messages m
        INNER JOIN (
            SELECT conversationId, MAX(timestamp) AS maxTs
            FROM messages
            WHERE isPrivate = 1
            GROUP BY conversationId
        ) latest ON m.conversationId = latest.conversationId
            AND m.timestamp = latest.maxTs
        WHERE m.isPrivate = 1
        ORDER BY m.timestamp DESC
    """)
    fun getLatestMessagePerConversation(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE isPrivate = 0 ORDER BY timestamp ASC")
    fun getPublicMessages(): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Query("UPDATE messages SET deliveryStatus = :status WHERE id = :id AND (deliveryStatus != 'delivered' OR :status = 'delivered')")
    suspend fun updateDeliveryStatus(id: String, status: String)

    @Query("UPDATE messages SET deliveryStatus = 'read' WHERE (conversationId = :conversationId OR conversationId = :fallbackName OR sender = :conversationId OR sender = :fallbackName) AND isOutgoing = 0 AND deliveryStatus = 'received'")
    suspend fun markConversationAsRead(conversationId: String, fallbackName: String = "")

    @Query("DELETE FROM messages")
    suspend fun clearAllMessages()

    @Query("DELETE FROM messages WHERE sender LIKE '%:%' OR recipientNickname LIKE '%:%' OR conversationId LIKE '%:%' OR (isPrivate = 1 AND conversationId NOT LIKE 'dev_%')")
    suspend fun deleteLegacyMessages()

    @Query(
        """
        DELETE FROM messages WHERE isPrivate = 1 AND (
            conversationId = :conversationId OR conversationId = :fallbackName
            OR
            (sender IN (:selfIdentifiers) AND (recipientNickname = :conversationId OR recipientNickname = :fallbackName))
            OR
            ((sender = :conversationId OR sender = :fallbackName) AND recipientNickname IN (:selfIdentifiers))
        )
        """
    )
    suspend fun deleteMessagesForConversation(
        conversationId: String,
        fallbackName: String,
        selfIdentifiers: List<String>
    )

    @Query("DELETE FROM messages WHERE (sender = :peerId OR recipientNickname = :peerId OR conversationId = :peerId) AND isPrivate = 1")
    suspend fun deleteMessagesForPeer(peerId: String)
}

@Dao
interface BlockedPeerDao {
    @Query("SELECT * FROM blocked_peers ORDER BY blockedAt DESC")
    fun getAllBlockedPeers(): Flow<List<BlockedPeer>>

    @Query("SELECT * FROM blocked_peers ORDER BY blockedAt DESC")
    suspend fun getAllBlockedPeersDirect(): List<BlockedPeer>

    @Query("SELECT COUNT(*) FROM blocked_peers WHERE peerID = :peerId OR (nickname != '' AND nickname = :nickname)")
    suspend fun isPeerBlocked(peerId: String, nickname: String = ""): Int

    @Query("SELECT COUNT(*) FROM blocked_peers WHERE peerID = :identifier OR (nickname != '' AND nickname = :identifier)")
    fun isPeerBlockedFlow(identifier: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun blockPeer(blockedPeer: BlockedPeer)

    @Query("DELETE FROM blocked_peers WHERE peerID = :peerId OR nickname = :peerId")
    suspend fun unblockPeer(peerId: String)

    @Query("DELETE FROM blocked_peers")
    suspend fun clearAllBlockedPeers()
}
