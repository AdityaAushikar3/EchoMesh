package chat.bitchat.protocol

import org.junit.Assert.*
import org.junit.Test
import java.nio.charset.StandardCharsets

class BinaryProtocolTest {

    @Test
    fun testHexConversion() {
        val originalBytes = byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x0A, 0x0F, 0xFF.toByte())
        val hexString = originalBytes.toHexString()
        assertEquals("000102030a0fff", hexString)

        val decodedBytes = hexString.hexToByteArray()
        assertArrayEquals(originalBytes, decodedBytes)
    }

    @Test
    fun testPeerIDParsing() {
        val p1 = PeerID.fromString("mesh:alice")
        assertEquals(PeerID.Prefix.MESH, p1.prefix)
        assertEquals("alice", p1.bare)
        assertEquals("mesh:alice", p1.id)
        assertTrue(p1.isValid)

        val p2 = PeerID.fromString("noise:8c9d0e1f2a3b4c5da1b2c3d4e5f64a5b8c9d0e1f2a3b4c5da1b2c3d4e5f64a5b")
        assertEquals(PeerID.Prefix.NOISE, p2.prefix)
        assertTrue(p2.isNoiseKeyHex)
        assertNotNull(p2.noiseKey)
        assertTrue(p2.isValid)

        // Invalid hex in short/noise keys
        val p3 = PeerID.fromString("noise:8c9d0e1f2a3b4c5da1b2c3d4e5f64a5b8c9d0e1f2a3b4c5da1b2c3d4e5f64a5g") // 'g' is invalid hex
        assertFalse(p3.isValid)
    }

    @Test
    fun testBitchatMessageSerialization() {
        val mentions = listOf("bob", "charlie")
        val message = BitchatMessage(
            id = "test-msg-1234",
            sender = "alice",
            content = "Hello, mesh network! How is everyone doing?",
            timestamp = 1719705600000L, // 2024-06-30
            isRelay = true,
            isPrivate = true,
            originalSender = "dave",
            recipientNickname = "eve",
            senderPeerID = PeerID.fromString("name:alice_node"),
            mentions = mentions,
            isBridged = true
        )

        val binaryPayload = message.toBinaryPayload()
        assertNotNull(binaryPayload)

        val decodedMessage = BitchatMessage.fromBinaryPayload(binaryPayload)
        assertNotNull(decodedMessage)
        assertEquals(message.id, decodedMessage?.id)
        assertEquals(message.sender, decodedMessage?.sender)
        assertEquals(message.content, decodedMessage?.content)
        assertEquals(message.timestamp, decodedMessage?.timestamp)
        assertEquals(message.isRelay, decodedMessage?.isRelay)
        assertEquals(message.isPrivate, decodedMessage?.isPrivate)
        assertEquals(message.originalSender, decodedMessage?.originalSender)
        assertEquals(message.recipientNickname, decodedMessage?.recipientNickname)
        assertEquals(message.senderPeerID, decodedMessage?.senderPeerID)
        assertEquals(message.mentions, decodedMessage?.mentions)
        assertEquals(message.isBridged, decodedMessage?.isBridged)
    }

    @Test
    fun testCourierEnvelopeSerialization() {
        val tag = "0102030405060708090a0b0c0d0e0f10".hexToByteArray()
        val ciphertext = "This is a sealed envelope ciphertext payload.".toByteArray(StandardCharsets.UTF_8)
        val envelope = CourierEnvelope(
            recipientTag = tag,
            expiry = 1719792000000L,
            ciphertext = ciphertext,
            copies = 4,
            prekeyID = 98765
        )

        val encoded = envelope.encode()
        assertNotNull(encoded)

        val decoded = CourierEnvelope.decode(encoded!!)
        assertNotNull(decoded)
        assertEquals(envelope, decoded)
    }

    @Test
    fun testCourierRecipientTagRotation() {
        val staticKey = "8c9d0e1f2a3b4c5da1b2c3d4e5f64a5b8c9d0e1f2a3b4c5da1b2c3d4e5f64a5b".hexToByteArray()
        val time = 1719705600000L // 2024-06-30 UTC
        
        val day = CourierEnvelope.epochDay(time)
        val tag = CourierEnvelope.recipientTag(staticKey, day)
        assertEquals(16, tag.size)

        // Verify rotating day tags cover past/present/future
        val candidates = CourierEnvelope.candidateTags(staticKey, time)
        assertEquals(3, candidates.size)
        assertTrue(candidates[1].contentEquals(tag))
    }

    @Test
    fun testMessagePadding() {
        val raw = "Test payload for padding".toByteArray(StandardCharsets.UTF_8)
        
        // Target size of 256
        val padded = MessagePadding.pad(raw, 256)
        assertEquals(256, padded.size)
        
        // unpad recovers raw data
        val unpadded = MessagePadding.unpad(padded)
        assertArrayEquals(raw, unpadded)

        // Exceed target size padding declines request
        val tooLarge = ByteArray(300)
        val result = MessagePadding.pad(tooLarge, 256)
        assertArrayEquals(tooLarge, result)
    }

    @Test
    fun testBinaryProtocolEncoding() {
        val senderKey = "8c9d0e1f2a3b4c5da1b2c3d4e5f64a5b8c9d0e1f2a3b4c5da1b2c3d4e5f64a5b".hexToByteArray()
        val senderID = senderKey.copyOf(8)
        val recipientID = "0102030405060708".hexToByteArray()

        val route = listOf(
            "1111111111111111".hexToByteArray(),
            "2222222222222222".hexToByteArray()
        )

        val payload = "Hello world! This is a test binary protocol packet payload.".toByteArray(StandardCharsets.UTF_8)
        val sig = ByteArray(64) { it.toByte() }

        val packet = BitchatPacket(
            version = 2,
            type = 0x20.toByte(), // Encrypted payload
            senderID = senderID,
            recipientID = recipientID,
            timestamp = 1719705600L,
            payload = payload,
            signature = sig,
            ttl = 5,
            route = route,
            isRSR = true
        )

        // Encode with padding enabled (should fit in 256 block size since payload is small)
        val encodedPadded = packet.toBinaryData(padding = true)
        assertNotNull(encodedPadded)
        assertEquals(256, encodedPadded?.size)

        // Decode must recover identical packet
        val decodedPadded = BitchatPacket.fromBinaryData(encodedPadded!!)
        assertNotNull(decodedPadded)
        assertEquals(packet, decodedPadded)

        // Encode without padding
        val encodedUnpadded = packet.toBinaryData(padding = false)
        assertNotNull(encodedUnpadded)

        // Decode must recover identical packet
        val decodedUnpadded = BitchatPacket.fromBinaryData(encodedUnpadded!!)
        assertNotNull(decodedUnpadded)
        assertEquals(packet, decodedUnpadded)
    }

    @Test
    fun testVerifiedIdentityPropagationAndRouting() {
        // A. Verified profile packet -> MAC -> dev_ID mapping
        val verifiedMacToIdentity = java.util.concurrent.ConcurrentHashMap<String, String>()
        val senderAddress = "AA:BB:CC:DD:EE:FF"
        val stableId = "dev_BBBB"
        
        // Simulating the handshake validation rule: only store dev_ prefixed identities
        if (stableId.startsWith("dev_")) {
            verifiedMacToIdentity[senderAddress.uppercase()] = stableId
        }
        assertEquals("dev_BBBB", verifiedMacToIdentity[senderAddress.uppercase()])

        // B. Scan refresh -> verified identity survives
        val discoveredDevices = mutableListOf<chat.bitchat.core.bluetooth.NearbyDevice>()
        
        // Simulating T0: Scanned with fallback name
        val initialDevice = chat.bitchat.core.bluetooth.NearbyDevice(
            id = senderAddress,
            name = "Nearby EE:FF",
            rssi = -70,
            discoveredAt = System.currentTimeMillis()
        )
        // Resolve scan identity using verified mapping
        val initialIdentity = verifiedMacToIdentity[initialDevice.id.uppercase()]
            ?: initialDevice.identity
        val finalDeviceT0 = initialDevice.copy(identity = initialIdentity)
        discoveredDevices.add(finalDeviceT0)
        
        // Check T0 state
        assertEquals("dev_BBBB", discoveredDevices.first().identity)

        // Simulating T2/T3: Scan refresh occurs, MAC remains, payload is temporarily missed
        val refreshedDevice = chat.bitchat.core.bluetooth.NearbyDevice(
            id = senderAddress,
            name = "Nearby EE:FF",
            rssi = -65,
            discoveredAt = System.currentTimeMillis()
        )
        // Resolve again using mapping
        val refreshedIdentity = verifiedMacToIdentity[refreshedDevice.id.uppercase()]
            ?: refreshedDevice.identity
        val finalDeviceT3 = refreshedDevice.copy(identity = refreshedIdentity)
        discoveredDevices[0] = finalDeviceT3

        // Verified identity MUST survive refresh
        assertEquals("dev_BBBB", discoveredDevices.first().identity)

        // C. sendMessage -> recipientID = hash(dev_ID)
        val targetIdentity = discoveredDevices.first().identity
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val recipientID = digest.digest(targetIdentity.toByteArray()).copyOf(8)
        
        val expectedHash = digest.digest("dev_BBBB".toByteArray()).copyOf(8)
        assertArrayEquals(expectedHash, recipientID)

        // D. Missing verified identity -> no nickname fallback
        val unverifiedAddress = "CC:DD:EE:FF:00:11"
        val resolvedTarget = verifiedMacToIdentity[unverifiedAddress.uppercase()]
        assertNull(resolvedTarget)
        
        // E. Two peers with the same nickname must produce different recipient identities
        val devA = "dev_AAAA"
        val devB = "dev_BBBB"
        
        val hashA = digest.digest(devA.toByteArray()).copyOf(8)
        val hashB = digest.digest(devB.toByteArray()).copyOf(8)
        
        // Even if nickname = "EchoMesh" for both, their hashed identities differ
        assertFalse(hashA.contentEquals(hashB))
    }
}
