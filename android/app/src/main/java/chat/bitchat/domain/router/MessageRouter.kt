package chat.bitchat.domain.router

import android.util.Log
import chat.bitchat.core.bluetooth.BLEMeshManager
import chat.bitchat.data.database.EchoMeshDatabase
import chat.bitchat.data.database.MessageEntity
import chat.bitchat.data.database.Peer
import chat.bitchat.protocol.BitchatMessage
import chat.bitchat.protocol.BitchatPacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRouter @Inject constructor(
    private val bleMeshManager: BLEMeshManager,
    private val database: EchoMeshDatabase
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val messageDao = database.messageDao()
    private val peerDao = database.peerDao()
    private val userProfileDao = database.userProfileDao()
    private val seenPackets = ConcurrentHashMap<String, Long>()

    init {
        observeIncomingPackets()
    }

    @kotlin.OptIn(kotlin.ExperimentalStdlibApi::class)
    private fun observeIncomingPackets() {
        scope.launch {
            Log.i("MessageRouter", "Listening for incoming mesh packets")
            bleMeshManager.receivedPackets.collect { (senderAddress, packet) ->
                val now = System.currentTimeMillis()
                
                // 1. Seen-set clean up (older than 5 minutes)
                seenPackets.entries.removeIf { now - it.value > 5 * 60 * 1000 }

                // 2. Compute packet unique ID
                val payloadDigest = MessageDigest.getInstance("SHA-256")
                    .digest(packet.payload)
                    .take(4)
                    .joinToString("") { "%02x".format(it) }
                val senderHex = packet.senderID.joinToString("") { "%02x".format(it) }
                val messageID = "$senderHex-${packet.timestamp}-${packet.type}-$payloadDigest"

                // 3. Deduplication check
                if (seenPackets.containsKey(messageID)) {
                    Log.d("MessageRouter", "Duplicate packet ignored: $messageID")
                    return@collect
                }
                seenPackets[messageID] = now

                // Calculate my own PeerID (stable identity hash)
                val myStablePeerId = getMockPeerID(bleMeshManager.localIdentity)

                // 4. Recipient Check (packet-level)
                val isForMe = packet.recipientID == null ||
                        packet.recipientID.contentEquals(myStablePeerId)

                val targetIdHex = packet.recipientID?.toHexString() ?: "null (public)"
                val localIdHex = myStablePeerId.toHexString()
                Log.d("MessageRouter", "[ROUTING] recipientId=$targetIdHex")
                Log.d("MessageRouter", "[ROUTING] localId=$localIdHex")
                Log.d("MessageRouter", "[ROUTING] isForMe=$isForMe")

                if (isForMe) {
                    // Packet is for us (or a public broadcast)! Process and save it.
                    Log.i(
                        "MessageRouter",
                        "Packet from $senderAddress is for us. type=${packet.type} payload=${packet.payload.size}"
                    )
                    if (packet.type == 0x05.toByte()) {
                        Log.i("MessageRouter", "Processing profile metadata packet from $senderAddress")
                        try {
                            val json = org.json.JSONObject(String(packet.payload, Charsets.UTF_8))
                            val identity = json.optString("identity", "").takeIf { it.isNotBlank() } ?: senderAddress
                            Log.d("MessageRouter", "[IDENTITY] handshake remoteStableId=$identity")
                            if (identity.startsWith("dev_")) {
                                bleMeshManager.registerVerifiedIdentity(senderAddress, identity)
                            }
                            val nickname = json.optString("nickname", "")
                            if (nickname.isNotBlank()) {
                                bleMeshManager.updateNicknameCache(identity, nickname)
                                bleMeshManager.updateNicknameCache(senderAddress, nickname)
                            }
                            val bio = json.optString("bio", "")
                            val interests = json.optString("interests", "")
                            val movies = json.optString("movies", "")
                            val music = json.optString("music", "")
                            val singers = json.optString("singers", "")
                            val career = json.optString("career", "")
                            val nameToStore = nickname.takeIf { it.isNotBlank() } ?: "Someone"
                            peerDao.upsertPeerPresence(identity, nameToStore, System.currentTimeMillis())
                            peerDao.updatePeerProfile(
                                peerId = identity,
                                bio = bio,
                                interests = interests,
                                movies = movies,
                                music = music,
                                singers = singers,
                                career = career
                            )
                            Log.i("MessageRouter", "Stored profile details for $identity")
                        } catch (e: Exception) {
                            Log.e("MessageRouter", "Failed to parse profile payload", e)
                        }
                        
                        // Relay profile packet to mesh
                        if (packet.ttl > 1) {
                            val relayedPacket = packet.copy(ttl = (packet.ttl - 1).toByte())
                            scope.launch {
                                delay((50..150).random().toLong())
                                bleMeshManager.relayPacket(senderAddress, relayedPacket)
                            }
                        }
                        return@collect
                    }
                    if (packet.type != 0x02.toByte()) {
                        Log.d("MessageRouter", "Ignoring non-message type ${packet.type}")
                        return@collect
                    }
                    val message = BitchatMessage.fromBinaryPayload(packet.payload)
                    if (message == null) {
                        Log.e("MessageRouter", "Failed to decode BitchatMessage")
                        return@collect
                    }
                    // 4b. Secondary recipient check using stable identity
                    if (message.isPrivate && message.recipientNickname != null) {
                        val stableIdentity = bleMeshManager.localIdentity
                        val recipMatch = message.recipientNickname.equals(stableIdentity, ignoreCase = true)
                        if (!recipMatch) {
                            Log.i(
                                "MessageRouter",
                                "Private message not for us (recipient='${message.recipientNickname}', me='$stableIdentity'). Relaying."
                            )
                            // Relay instead of storing
                            if (packet.ttl > 1) {
                                val relayedPacket = packet.copy(ttl = (packet.ttl - 1).toByte())
                                scope.launch {
                                    delay((50..150).random().toLong())
                                    bleMeshManager.relayPacket(senderAddress, relayedPacket)
                                }
                            }
                            return@collect
                        }
                    }

                    val discoveredSender = bleMeshManager.discoveredDevices.value
                        .find { it.id.equals(senderAddress, ignoreCase = true) }
                    val senderStableId = message.senderPeerID?.id?.takeIf { it.startsWith("dev_") }
                        ?: discoveredSender?.identity?.takeIf { it.startsWith("dev_") }
                        ?: peerDao.getAllPeersDirect().find { it.nickname.equals(message.sender, ignoreCase = true) }?.peerID?.takeIf { it.startsWith("dev_") }

                    if (senderStableId == null) {
                        Log.w("MessageRouter", "[IDENTITY] Cannot resolve stable identity for incoming message sender=${message.sender} address=$senderAddress. Aborting storage.")
                        // Relay message to mesh as fallback
                        if (!message.isPrivate && packet.ttl > 1) {
                            val relayedPacket = packet.copy(ttl = (packet.ttl - 1).toByte())
                            scope.launch {
                                delay((50..150).random().toLong())
                                bleMeshManager.relayPacket(senderAddress, relayedPacket)
                            }
                        }
                        return@collect
                    }

                    messageDao.insertMessage(
                        MessageEntity(
                            id = message.id,
                            sender = senderStableId,
                            content = message.content,
                            timestamp = message.timestamp,
                            isPrivate = message.isPrivate,
                            recipientNickname = message.recipientNickname,
                            isRelay = message.isRelay,
                            conversationId = senderStableId,
                            isOutgoing = false,
                            deliveryStatus = "received"
                        )
                    )
                    val inboundName = message.sender.takeIf {
                        it.isNotBlank() && !it.contains(":") && !it.startsWith("Nearby ")
                    }
                    if (inboundName != null) {
                        peerDao.upsertPeerPresence(
                            senderStableId,
                            inboundName,
                            System.currentTimeMillis()
                        )
                    }
                    Log.i("MessageRouter", "Stored inbound message from $senderStableId")

                    // Relay public messages to mesh
                    if (!message.isPrivate) {
                        if (packet.ttl > 1) {
                            val relayedPacket = packet.copy(ttl = (packet.ttl - 1).toByte())
                            scope.launch {
                                delay((50..150).random().toLong())
                                bleMeshManager.relayPacket(senderAddress, relayedPacket)
                            }
                        }
                    }
                } else {
                    // Packet is for someone else! Relay it.
                    Log.i("MessageRouter", "Transit packet detected (recipient ID mismatch). Initiating relay flow...")
                    if (packet.ttl <= 1) {
                        Log.d("MessageRouter", "Relay dropped: TTL expired (ttl=${packet.ttl})")
                        return@collect
                    }

                    // Decrement TTL
                    val relayedPacket = packet.copy(ttl = (packet.ttl - 1).toByte())

                    // Calculate delay based on connection degree
                    val degree = bleMeshManager.activeLinkCount
                    val delayMs = when (degree) {
                        in 0..2 -> (10..40).random()
                        in 3..5 -> (60..150).random()
                        in 6..9 -> (80..180).random()
                        else -> (100..220).random()
                    }
                    Log.d(
                        "MessageRouter",
                        "Scheduling relay to mesh after ${delayMs}ms jitter (degree=$degree, newTtl=${relayedPacket.ttl})"
                    )

                    scope.launch {
                        delay(delayMs.toLong())
                        bleMeshManager.relayPacket(senderAddress, relayedPacket)
                    }
                }
            }
        }
    }

    fun sendMessage(
        recipientAddress: String,
        recipientName: String,
        content: String,
        isPrivate: Boolean,
        callback: (Boolean) -> Unit
    ) {
        Log.i("MessageRouter", "send → $recipientName ($recipientAddress)")
        scope.launch {
            val profile = userProfileDao.getProfileDirect()
            val senderName = profile?.name?.takeIf { it.isNotBlank() } ?: "Me"
            val targetDevice = bleMeshManager.discoveredDevices.value
                .find { it.id.equals(recipientAddress, ignoreCase = true) }
            val liveName = targetDevice
                ?.name
                ?.takeIf { it.isNotBlank() && !it.startsWith("Nearby ") }
            val liveAddress = targetDevice?.id ?: recipientAddress
            val resolvedTarget = targetDevice?.identity?.takeIf { it.startsWith("dev_") }
                ?: bleMeshManager.verifiedMacToIdentity[liveAddress.uppercase()]
                ?: peerDao.getAllPeersDirect().find { it.peerID.equals(recipientAddress, ignoreCase = true) || it.nickname.equals(recipientName, ignoreCase = true) }?.peerID?.takeIf { it.startsWith("dev_") }

            if (isPrivate && (resolvedTarget == null || !resolvedTarget.startsWith("dev_"))) {
                Log.w("MessageRouter", "Cannot send private message: stable target identity has not yet been verified for $recipientAddress ($recipientName)")
                callback(false)
                return@launch
            }
            val targetIdentity = resolvedTarget ?: recipientName
            val messageId = UUID.randomUUID().toString()
            val timestamp = System.currentTimeMillis()

            // Show in UI immediately - group by the stable identity
            messageDao.insertMessage(
                MessageEntity(
                    id = messageId,
                    sender = bleMeshManager.localIdentity,
                    content = content,
                    timestamp = timestamp,
                    isPrivate = isPrivate,
                    recipientNickname = targetIdentity,
                    isRelay = false,
                    conversationId = targetIdentity,
                    isOutgoing = true,
                    deliveryStatus = "sending"
                )
            )

            // Prefer live discovery nickname over stale Room/chat labels
            val peerName = recipientName.takeIf {
                it.isNotBlank() && !it.contains(":") && !it.startsWith("Nearby ")
            }
            if (peerName != null) {
                peerDao.upsertPeerPresence(
                    targetIdentity,
                    peerName,
                    System.currentTimeMillis()
                )
            }

            val message = BitchatMessage(
                id = messageId,
                sender = senderName,
                content = content,
                timestamp = timestamp,
                isRelay = false,
                isPrivate = isPrivate,
                recipientNickname = targetIdentity,
                senderPeerID = chat.bitchat.protocol.PeerID.fromString(bleMeshManager.localIdentity)
            )
            Log.d("MessageRouter", "[ROUTING] recipientId=$targetIdentity")
            Log.d("MessageRouter", "[ROUTING] localId=${bleMeshManager.localIdentity}")
            val packet = BitchatPacket(
                version = 2,
                type = 0x02.toByte(),
                senderID = getMockPeerID(bleMeshManager.localIdentity),
                recipientID = if (isPrivate) getMockPeerID(targetIdentity) else null,
                timestamp = timestamp / 1000,
                payload = message.toBinaryPayload(),
                signature = null,
                ttl = 7,
                route = null,
                isRSR = false
            )

            bleMeshManager.sendPacket(liveAddress, packet) { success ->
                scope.launch {
                    messageDao.updateDeliveryStatus(
                        messageId,
                        if (success) "sent" else "failed"
                    )
                    Log.i("MessageRouter", "Delivery ${if (success) "sent" else "failed"} id=$messageId")
                }
                callback(success)
            }
        }
    }

    private fun isGenericNickname(name: String): Boolean {
        val clean = name.trim().lowercase()
        return clean.isBlank() ||
                clean == "echomesh" ||
                clean == "me" ||
                clean == "someone" ||
                clean.startsWith("nearby") ||
                clean.contains(":")
    }

    private fun getMockPeerID(name: String): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(name.toByteArray()).copyOf(8)
    }
}
