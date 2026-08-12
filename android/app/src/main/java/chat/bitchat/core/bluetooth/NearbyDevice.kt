package chat.bitchat.core.bluetooth

data class NearbyDevice(
    /** Current BLE address (may rotate). Used for GATT connect. */
    val id: String,
    /** Display name from EchoMesh identity payload. */
    val name: String,
    val rssi: Int,
    val discoveredAt: Long,
    /** Stable EchoMesh identity — survives BLE address rotation. */
    val identity: String = name
)
