package chat.bitchat.domain.router

import android.util.Log
import chat.bitchat.core.bluetooth.BLEMeshManager
import chat.bitchat.data.database.EchoMeshDatabase
import chat.bitchat.data.database.MessageEntity
import chat.bitchat.data.database.Peer
import chat.bitchat.protocol.BitchatMessage
import chat.bitchat.protocol.BitchatPacket
import chat.bitchat.protocol.DeliveryAckPayload
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
    private val blockedPeerDao = database.blockedPeerDao()
    private val seenPackets = ConcurrentHashMap<String, Long>()

    @Volatile
    private var cachedMyName: String? = null

    init {
        scope.launch {
            userProfileDao.getProfile().collect { p ->
                cachedMyName = p?.name?.takeIf { it.isNotBlank() }
            }
        }
        observeIncomingPackets()
        startPruningLoop()
    }

    private fun startPruningLoop() {
        scope.launch {
            while (true) {
                delay(60_000)
                val cutoff = System.currentTimeMillis() - (5 * 60 * 1000)
                seenPackets.entries.removeIf { it.value < cutoff }
            }
        }
    }

    @kotlin.OptIn(kotlin.ExperimentalStdlibApi::class)
    private fun observeIncomingPackets() {
        scope.launch {
            Log.i("MessageRouter", "Listening for incoming mesh packets")
            bleMeshManager.receivedPackets.collect { (senderAddress, packet) ->
                val now = System.currentTimeMillis()

                // 1. Compute packet unique ID
                val payloadDigest = MessageDigest.getInstance("SHA-256")
                    .digest(packet.payload)
                    .take(4)
                    .joinToString("") { "%02x".format(it) }
                val senderHex = packet.senderID.joinToString("") { "%02x".format(it) }
                val messageID = "$senderHex-${packet.timestamp}-${packet.type}-$payloadDigest"

                // 2. Deduplication check
                if (seenPackets.putIfAbsent(messageID, now) != null) {
                    Log.d("MessageRouter", "Duplicate packet ignored: $messageID")
                    return@collect
                }

                // Calculate my own PeerID (stable identity hash and display name hash)
                val myStablePeerId = getMockPeerID(bleMeshManager.localIdentity)
                val myName = cachedMyName
                val myNamePeerId = myName?.let { getMockPeerID(it) }

                // 4. Recipient Check (packet-level)
                val isForMe = packet.recipientID == null ||
                        packet.recipientID.contentEquals(myStablePeerId) ||
                        (myNamePeerId != null && packet.recipientID.contentEquals(myNamePeerId))

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
                    // Handle delivery ACKs
                    if (packet.type == BLEMeshManager.TYPE_DELIVERY_ACK) {
                        handleDeliveryAck(packet)
                        return@collect
                    }
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
                            val pubKeyStr = json.optString("publicKey", "").takeIf { it.isNotBlank() }
                            if (pubKeyStr != null) {
                                peerDao.updatePeerPublicKey(identity, pubKeyStr)
                                Log.i("MessageRouter", "[E2EE] Stored public key for $identity")
                                attemptRetroactiveDecryption(identity, pubKeyStr)
                            }
                            Log.i("MessageRouter", "Stored profile details for $identity")
                        } catch (e: Exception) {
                            Log.e("MessageRouter", "Failed to parse profile payload", e)
                        }
                        
                        // Relay profile packet to mesh
                        if (packet.ttl > 1) {
                            val relayedPacket = prepareRelayPacket(packet, senderAddress)
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
                    // 4b. Secondary recipient check using stable identity or display name
                    if (message.isPrivate && message.recipientNickname != null) {
                        val stableIdentity = bleMeshManager.localIdentity
                        val myName = cachedMyName
                        val recipMatch = message.recipientNickname.equals(stableIdentity, ignoreCase = true) ||
                                (myName != null && message.recipientNickname.equals(myName, ignoreCase = true))
                        if (!recipMatch) {
                            Log.i(
                                "MessageRouter",
                                "Private message not for us (recipient='${message.recipientNickname}', me='$stableIdentity', name='$myName'). Relaying."
                            )
                            // Relay instead of storing
                            if (packet.ttl > 1) {
                                val relayedPacket = prepareRelayPacket(packet, senderAddress)
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
                        ?: bleMeshManager.verifiedMacToIdentity[senderAddress.uppercase()]
                        ?: peerDao.getAllPeersDirect().find { it.nickname.equals(message.sender, ignoreCase = true) }?.peerID?.takeIf { it.startsWith("dev_") }
                        ?: message.senderPeerID?.id?.takeIf { it.isNotBlank() }
                        ?: message.sender.takeIf { it.isNotBlank() }
                        ?: senderAddress

                    if (blockedPeerDao.isPeerBlocked(senderStableId, message.sender) > 0) {
                        Log.w("MessageRouter", "Dropped incoming message from blocked node $senderStableId (${message.sender})")
                        return@collect
                    }

                    val finalContent = if (message.isPrivate && message.content.startsWith(chat.bitchat.core.security.CryptoUtil.E2EE_PREFIX)) {
                        val senderPeer = peerDao.getPeerByIdDirect(senderStableId)
                        val senderPubKeyBytes = senderPeer?.publicKey?.let { android.util.Base64.decode(it, android.util.Base64.NO_WRAP) }
                        val myPrivateKey = chat.bitchat.core.security.KeyManager.getPrivateKey()
                        if (senderPubKeyBytes != null && myPrivateKey != null) {
                            val decrypted = chat.bitchat.core.security.CryptoUtil.decrypt(message.content, senderStableId, myPrivateKey, senderPubKeyBytes)
                            if (decrypted != null) {
                                Log.i("MessageRouter", "[E2EE] Decrypted incoming message from $senderStableId")
                                decrypted
                            } else {
                                Log.e("MessageRouter", "[E2EE] Decryption pending key for $senderStableId")
                                message.content
                            }
                        } else {
                            Log.w("MessageRouter", "[E2EE] Preserving ciphertext pending public key for $senderStableId")
                            message.content
                        }
                    } else {
                        message.content
                    }

                    messageDao.insertMessage(
                        MessageEntity(
                            id = message.id,
                            sender = senderStableId,
                            content = finalContent,
                            timestamp = message.timestamp,
                            isPrivate = message.isPrivate,
                            recipientNickname = message.recipientNickname,
                            isRelay = message.isRelay,
                            conversationId = senderStableId,
                            isOutgoing = false,
                            deliveryStatus = "received"
                        )
                    )

                    // Send delivery ACK back to sender for private messages
                    if (message.isPrivate) {
                        sendDeliveryAck(
                            originalMessageId = message.id,
                            originalSenderHash = packet.senderID
                        )
                    }

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
                            val relayedPacket = prepareRelayPacket(packet, senderAddress)
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

                    // Decrement TTL and append route telemetry
                    val relayedPacket = prepareRelayPacket(packet, senderAddress)

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
                ?: recipientAddress.takeIf { it.startsWith("dev_") }
                ?: targetDevice?.identity?.takeIf { it.isNotBlank() }
                ?: recipientAddress.takeIf { it.isNotBlank() }
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

            val recipientPeer = if (isPrivate) peerDao.getPeerByIdDirect(targetIdentity) else null
            val recipientPubKeyBytes = recipientPeer?.publicKey?.let { android.util.Base64.decode(it, android.util.Base64.NO_WRAP) }
            val myPrivateKey = if (isPrivate) chat.bitchat.core.security.KeyManager.getPrivateKey() else null

            val wireContent = if (isPrivate && recipientPubKeyBytes != null && myPrivateKey != null) {
                val encrypted = chat.bitchat.core.security.CryptoUtil.encrypt(content, targetIdentity, myPrivateKey, recipientPubKeyBytes)
                if (encrypted != null) {
                    Log.i("MessageRouter", "[E2EE] Encrypted message for targetIdentity=$targetIdentity")
                    encrypted
                } else {
                    content
                }
            } else {
                content
            }

            val message = BitchatMessage(
                id = messageId,
                sender = senderName,
                content = wireContent,
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
                ttl = 10,
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

    private fun sendDeliveryAck(originalMessageId: String, originalSenderHash: ByteArray) {
        val ackPayload = DeliveryAckPayload(
            messageId = originalMessageId,
            acknowledgedAt = System.currentTimeMillis()
        ).toBytes()

        val myPeerIdHash = getMockPeerID(bleMeshManager.localIdentity)

        val ackPacket = BitchatPacket(
            version = 2,
            type = BLEMeshManager.TYPE_DELIVERY_ACK,
            senderID = myPeerIdHash,
            recipientID = originalSenderHash,
            timestamp = System.currentTimeMillis() / 1000,
            payload = ackPayload,
            signature = null,
            ttl = 8,
            route = null,
            isRSR = false
        )

        scope.launch {
            delay((10L..50L).random())  // Jitter to prevent BLE collisions
            bleMeshManager.relayPacket("", ackPacket)
            Log.i("MessageRouter", "Sent delivery ACK for messageId=$originalMessageId")
        }
    }

    private suspend fun handleDeliveryAck(packet: BitchatPacket) {
        val ack = DeliveryAckPayload.fromBytes(packet.payload)
        if (ack == null) {
            Log.e("MessageRouter", "Failed to decode ACK payload")
            return
        }
        Log.i("MessageRouter", "Received delivery ACK for messageId=${ack.messageId}")
        messageDao.updateDeliveryStatus(ack.messageId, "delivered")
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

    private fun attemptRetroactiveDecryption(senderStableId: String, publicKeyStr: String) {
        scope.launch {
            try {
                val pubKeyBytes = android.util.Base64.decode(publicKeyStr, android.util.Base64.NO_WRAP)
                val myPrivateKey = chat.bitchat.core.security.KeyManager.getPrivateKey() ?: return@launch
                val messages = messageDao.getPrivateMessagesForPeer(senderStableId).firstOrNull() ?: return@launch
                for (msg in messages) {
                    if (msg.content.startsWith(chat.bitchat.core.security.CryptoUtil.E2EE_PREFIX)) {
                        val decrypted = chat.bitchat.core.security.CryptoUtil.decrypt(msg.content, senderStableId, myPrivateKey, pubKeyBytes)
                        if (decrypted != null) {
                            messageDao.insertMessage(msg.copy(content = decrypted))
                            Log.i("MessageRouter", "[E2EE] Retroactively decrypted message id=${msg.id}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MessageRouter", "Failed retroactive decryption for $senderStableId", e)
            }
        }
    }

    private fun getMockPeerID(name: String): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(name.toByteArray()).copyOf(8)
    }

    private fun prepareRelayPacket(packet: BitchatPacket, senderAddress: String): BitchatPacket {
        val myNodeId = getMockPeerID(bleMeshManager.localIdentity)
        val rssi = bleMeshManager.getLinkQuality(senderAddress)
        val newRoute = (packet.route ?: emptyList()) + myNodeId
        val newMetrics = (packet.routeMetrics ?: emptyList()) + rssi
        return packet.copy(
            ttl = (packet.ttl - 1).toByte(),
            route = newRoute,
            routeMetrics = newMetrics
        )
    }
}
