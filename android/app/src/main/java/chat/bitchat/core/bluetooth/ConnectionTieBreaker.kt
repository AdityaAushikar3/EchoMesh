package chat.bitchat.core.bluetooth

object ConnectionTieBreaker {
    /**
     * Determines whether this device should initiate an outbound client connection to a discovered peer.
     * When two EchoMesh peers discover each other simultaneously, an asymmetric tie-breaker based on
     * canonical stable identity (dev_*) ensures only one device initiates the client connection,
     * while the other device receives it via its GATT server.
     *
     * Rules:
     * 1. If both devices have stable canonical identities (dev_*), the device with the lexicographically
     *    larger identity initiates the outbound connection.
     * 2. If one or both identities are non-canonical/legacy (e.g. temporary MAC or unverified name),
     *    outbound connection is allowed to ensure backward compatibility.
     * 3. If identities are identical (self-loop / edge-case), returns true to allow standard handling.
     */
    fun shouldInitiateConnection(myLocalIdentity: String, peerIdentity: String?): Boolean {
        if (peerIdentity.isNullOrBlank()) return true
        val myIsCanonical = myLocalIdentity.startsWith("dev_")
        val peerIsCanonical = peerIdentity.startsWith("dev_")

        if (myIsCanonical && peerIsCanonical) {
            return myLocalIdentity >= peerIdentity
        }
        return true
    }
}
