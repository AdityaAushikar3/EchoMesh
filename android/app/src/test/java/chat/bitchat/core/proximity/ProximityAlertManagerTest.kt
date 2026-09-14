package chat.bitchat.core.proximity

import chat.bitchat.core.bluetooth.NearbyDevice
import chat.bitchat.data.database.Peer
import chat.bitchat.data.database.UserProfile
import chat.bitchat.ui.util.rssiToMeters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProximityAlertManagerTest {

    private val alertedPeers = mutableMapOf<String, Long>()

    @Before
    fun setup() {
        alertedPeers.clear()
    }

    private fun createMyProfile(interests: String): UserProfile {
        return UserProfile(
            id = 1,
            name = "Me",
            bio = "Decentralized mesh enthusiast",
            interests = interests
        )
    }

    private fun createPeer(peerId: String, name: String, interests: String): Peer {
        return Peer(
            peerID = peerId,
            nickname = name,
            trustLevel = "Casual",
            lastSeen = System.currentTimeMillis(),
            interests = interests
        )
    }

    private fun createDevice(id: String, identity: String, name: String, rssi: Int): NearbyDevice {
        return NearbyDevice(
            id = id,
            name = name,
            rssi = rssi,
            discoveredAt = System.currentTimeMillis(),
            identity = identity
        )
    }

    @Test
    fun testZeroMutualInterestsNoAlert() {
        val myProfile = createMyProfile("Cooking, Travel, Gardening")
        val peer = createPeer("dev_alice", "Alice", "Rock, Metal, Jazz")
        val device = createDevice("mac_1", "dev_alice", "Alice", rssi = -50) // < 5m

        val result = ProximityEvaluator.evaluatePeerAlert(
            device = device,
            myProfile = myProfile,
            peers = listOf(peer),
            threshold = 3,
            alertedPeers = alertedPeers
        )
        assertNull("Zero shared interests must not trigger alert", result)
    }

    @Test
    fun testThresholdsOneTwoThree() {
        val myProfile = createMyProfile("AI / ML, Coding, Rock, Sci-Fi")
        val peer = createPeer("dev_bob", "Bob", "AI / ML, Coding, Rock")
        val device = createDevice("mac_2", "dev_bob", "Bob", rssi = -50) // < 5m

        // Threshold = 1 (matches 3 >= 1)
        alertedPeers.clear()
        val result1 = ProximityEvaluator.evaluatePeerAlert(device, myProfile, listOf(peer), threshold = 1, alertedPeers = alertedPeers)
        assertEquals("Threshold 1 should trigger alert for 3 mutual interests", "dev_bob", result1)

        // Threshold = 2 (matches 3 >= 2)
        alertedPeers.clear()
        val result2 = ProximityEvaluator.evaluatePeerAlert(device, myProfile, listOf(peer), threshold = 2, alertedPeers = alertedPeers)
        assertEquals("Threshold 2 should trigger alert for 3 mutual interests", "dev_bob", result2)

        // Threshold = 3 (matches 3 >= 3)
        alertedPeers.clear()
        val result3 = ProximityEvaluator.evaluatePeerAlert(device, myProfile, listOf(peer), threshold = 3, alertedPeers = alertedPeers)
        assertEquals("Threshold 3 should trigger alert for 3 mutual interests", "dev_bob", result3)

        // Peer with only 2 matches under threshold 3
        val peerTwoMatches = createPeer("dev_carol", "Carol", "AI / ML, Coding, Classical")
        val deviceCarol = createDevice("mac_3", "dev_carol", "Carol", rssi = -50)
        val resultUnder = ProximityEvaluator.evaluatePeerAlert(deviceCarol, myProfile, listOf(peerTwoMatches), threshold = 3, alertedPeers = alertedPeers)
        assertNull("2 mutual interests should not trigger when threshold is 3", resultUnder)
    }

    @Test
    fun testFourPlusMutualInterestsTriggersAlert() {
        val myProfile = createMyProfile("AI / ML, Coding, Rock, Sci-Fi, Gaming")
        val peer = createPeer("dev_dan", "Dan", "AI / ML, Coding, Rock, Sci-Fi, Gaming")
        val device = createDevice("mac_4", "dev_dan", "Dan", rssi = -45)

        val result = ProximityEvaluator.evaluatePeerAlert(device, myProfile, listOf(peer), threshold = 3, alertedPeers = alertedPeers)
        assertEquals("4+ mutual interests must trigger alert under threshold 3", "dev_dan", result)
    }

    @Test
    fun testCaseInsensitivityAndWhitespaceNormalization() {
        val myProfile = createMyProfile("   aI / mL ,   rOcK  ,  sCi-Fi  ")
        val peer = createPeer("dev_eve", "Eve", "AI / ML, rock, Sci-Fi")
        val device = createDevice("mac_5", "dev_eve", "Eve", rssi = -50)

        val result = ProximityEvaluator.evaluatePeerAlert(device, myProfile, listOf(peer), threshold = 3, alertedPeers = alertedPeers)
        assertEquals("Case and whitespace normalized interests should match successfully", "dev_eve", result)
    }

    @Test
    fun testDuplicateAndEmptyInterests() {
        val myProfile = createMyProfile("AI / ML, AI / ML, Coding, , ,")
        val peer = createPeer("dev_frank", "Frank", "AI / ML, Coding, AI / ML")
        val device = createDevice("mac_6", "dev_frank", "Frank", rssi = -50)

        // Only 2 distinct mutual interests ("AI / ML", "Coding")
        val resultThresh3 = ProximityEvaluator.evaluatePeerAlert(device, myProfile, listOf(peer), threshold = 3, alertedPeers = alertedPeers)
        assertNull("Duplicate interests must not artificially inflate mutual count", resultThresh3)

        val resultThresh2 = ProximityEvaluator.evaluatePeerAlert(device, myProfile, listOf(peer), threshold = 2, alertedPeers = alertedPeers)
        assertEquals("Distinct mutual count of 2 should match threshold 2", "dev_frank", resultThresh2)
    }

    @Test
    fun testMissingPeerProfileNoAlert() {
        val myProfile = createMyProfile("AI / ML, Rock, Sci-Fi")
        val device = createDevice("mac_7", "dev_ghost", "Ghost", rssi = -40)

        // Peer is not in the database / no profile received yet
        val result = ProximityEvaluator.evaluatePeerAlert(device, myProfile, emptyList(), threshold = 3, alertedPeers = alertedPeers)
        assertNull("Missing peer profile must safely return null without throwing", result)
    }

    @Test
    fun testDistanceCalculatedThreshold() {
        val myProfile = createMyProfile("AI / ML, Rock, Sci-Fi")
        val peer = createPeer("dev_helen", "Helen", "AI / ML, Rock, Sci-Fi")

        // RSSI -75 dBm -> approx 13 meters (>= 5m)
        val farDevice = createDevice("mac_8", "dev_helen", "Helen", rssi = -75)
        assertTrue("Distance for -75 dBm must be >= 5m", rssiToMeters(farDevice.rssi) >= 5)

        val farResult = ProximityEvaluator.evaluatePeerAlert(farDevice, myProfile, listOf(peer), threshold = 3, alertedPeers = alertedPeers)
        assertNull("Peers at distance >= 5m must not trigger alert", farResult)

        // RSSI -50 dBm -> approx 1 meter (< 5m)
        val nearDevice = createDevice("mac_8", "dev_helen", "Helen", rssi = -50)
        assertTrue("Distance for -50 dBm must be < 5m", rssiToMeters(nearDevice.rssi) < 5)

        val nearResult = ProximityEvaluator.evaluatePeerAlert(nearDevice, myProfile, listOf(peer), threshold = 3, alertedPeers = alertedPeers)
        assertEquals("Peers at distance < 5m must trigger alert", "dev_helen", nearResult)
    }

    @Test
    fun testCooldownPreventsRepeatAlert() {
        val myProfile = createMyProfile("AI / ML, Rock, Sci-Fi")
        val peer = createPeer("dev_ian", "Ian", "AI / ML, Rock, Sci-Fi")
        val device = createDevice("mac_9", "dev_ian", "Ian", rssi = -48)

        val t0 = 1_000_000L
        val firstAlert = ProximityEvaluator.evaluatePeerAlert(device, myProfile, listOf(peer), threshold = 3, alertedPeers = alertedPeers, now = t0)
        assertEquals("First alert at T0 should succeed", "dev_ian", firstAlert)

        // Second alert at T0 + 5 minutes (within 30-minute cooldown)
        val secondAlert = ProximityEvaluator.evaluatePeerAlert(device, myProfile, listOf(peer), threshold = 3, alertedPeers = alertedPeers, now = t0 + 5 * 60 * 1000L)
        assertNull("Subsequent alert within 30-minute cooldown must be ignored", secondAlert)

        // Third alert at T0 + 31 minutes (after cooldown expires)
        val thirdAlert = ProximityEvaluator.evaluatePeerAlert(device, myProfile, listOf(peer), threshold = 3, alertedPeers = alertedPeers, now = t0 + 31 * 60 * 1000L)
        assertEquals("Alert after 30-minute cooldown must trigger again", "dev_ian", thirdAlert)
    }

    @Test
    fun testDistanceOscillationsDoNotTriggerMultipleAlerts() {
        val myProfile = createMyProfile("AI / ML, Rock, Sci-Fi")
        val peer = createPeer("dev_jack", "Jack", "AI / ML, Rock, Sci-Fi")

        val t0 = 1_000_000L

        // >= 5m (RSSI -78, approx 9m) -> No alert
        val step1 = ProximityEvaluator.evaluatePeerAlert(createDevice("mac_10", "dev_jack", "Jack", -78), myProfile, listOf(peer), 3, alertedPeers, t0)
        assertNull("Step 1: >= 5m must not trigger", step1)

        // < 5m (RSSI -50, approx 1m) -> Alert triggered!
        val step2 = ProximityEvaluator.evaluatePeerAlert(createDevice("mac_10", "dev_jack", "Jack", -50), myProfile, listOf(peer), 3, alertedPeers, t0 + 10_000)
        assertEquals("Step 2: < 5m triggers first alert", "dev_jack", step2)

        // < 5m (RSSI -45, approx 1m) -> Cooldown active -> Ignored
        val step3 = ProximityEvaluator.evaluatePeerAlert(createDevice("mac_10", "dev_jack", "Jack", -45), myProfile, listOf(peer), 3, alertedPeers, t0 + 20_000)
        assertNull("Step 3: < 5m ignored due to cooldown", step3)

        // >= 5m (RSSI -78, approx 9m) -> Distance too far -> Ignored
        val step4 = ProximityEvaluator.evaluatePeerAlert(createDevice("mac_10", "dev_jack", "Jack", -78), myProfile, listOf(peer), 3, alertedPeers, t0 + 30_000)
        assertNull("Step 4: >= 5m ignored due to distance", step4)

        // < 5m (RSSI -50, approx 1m) -> Cooldown active -> Ignored
        val step5 = ProximityEvaluator.evaluatePeerAlert(createDevice("mac_10", "dev_jack", "Jack", -50), myProfile, listOf(peer), 3, alertedPeers, t0 + 40_000)
        assertNull("Step 5: < 5m ignored due to cooldown", step5)
    }

    @Test
    fun testStablePeerIdUsedForCooldown() {
        val myProfile = createMyProfile("AI / ML, Rock, Sci-Fi")
        val peer = createPeer("dev_karen", "Karen", "AI / ML, Rock, Sci-Fi")

        val t0 = 1_000_000L

        // Device first seen with MAC 1
        val devMac1 = createDevice("MAC_ADDR_1", "dev_karen", "Karen", -50)
        val alert1 = ProximityEvaluator.evaluatePeerAlert(devMac1, myProfile, listOf(peer), 3, alertedPeers, t0)
        assertEquals("Alert returns canonical stable peer ID", "dev_karen", alert1)

        // Device changes randomized BLE MAC address to MAC 2, but identity remains dev_karen
        val devMac2 = createDevice("MAC_ADDR_2", "dev_karen", "Karen", -50)
        val alert2 = ProximityEvaluator.evaluatePeerAlert(devMac2, myProfile, listOf(peer), 3, alertedPeers, t0 + 5_000)
        assertNull("Cooldown must track dev_karen even when MAC address changes", alert2)
    }
}
