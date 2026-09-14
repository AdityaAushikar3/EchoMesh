package chat.bitchat.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProximityBoundaryTest {

    @Test
    fun testValidRssiCalculations() {
        // -50 dBm -> approx 1m (< 5m)
        val near = rssiToMeters(-50)
        assertEquals(1, near)
        assertTrue(near < 5)

        // -75 dBm -> approx 6m (>= 5m)
        val far = rssiToMeters(-75)
        assertTrue(far >= 5)

        // -90 dBm -> approx 23m (>= 5m)
        val far90 = rssiToMeters(-90)
        assertTrue(far90 >= 20)

        // -95 dBm -> approx 35m (>= 30m)
        val veryFar = rssiToMeters(-95)
        assertTrue(veryFar >= 30)
    }

    @Test
    fun testExtremeAndInvalidRssiBounds() {
        // Positive RSSI (unusual/invalid in BLE) -> safely bounds to 1m
        assertEquals(1, rssiToMeters(0))
        assertEquals(1, rssiToMeters(15))

        // Extreme negative RSSI -> capped at 90m
        assertEquals(90, rssiToMeters(-150))
        assertEquals(90, rssiToMeters(-250))
    }

    @Test
    fun testProximityBands() {
        assertEquals(ProximityBand.Near, rssiToBand(-50))
        assertEquals(ProximityBand.Mid, rssiToBand(-65))
        assertEquals(ProximityBand.Far, rssiToBand(-85))
    }
}
