package chat.bitchat.ui.util

import chat.bitchat.core.bluetooth.NearbyDevice
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

data class Interest(
    val label: String,
    val emoji: String
) {
    val display: String get() = "$emoji $label"
}

object InterestDirectory {
    data class Category(val name: String, val emoji: String, val items: List<String>)

    val categories = listOf(
        Category("Entertainment", "🎬", listOf(
            "Action", "Comedy", "Horror", "Thriller", "Sci-Fi", "Romance", "Drama", "Fantasy", "Crime", "Mystery", "Animation", "Documentary",
            "Breaking Bad", "Game of Thrones", "Stranger Things", "Marvel", "DC", "Harry Potter"
        )),
        Category("Music", "🎵", listOf(
            "Rock", "Pop", "Hip-Hop", "Rap", "EDM", "Classical", "Jazz", "Metal", "Indie", "Bollywood", "Lo-fi", "Folk",
            "Arijit Singh", "A.R. Rahman", "The Weeknd", "Taylor Swift", "Imagine Dragons",
            "Guitar", "Piano", "Drums", "Violin", "Keyboard", "Flute"
        )),
        Category("Career", "💼", listOf(
            "Software Development", "Web Development", "Mobile Development", "AI / ML", "Data Science", "Cybersecurity", "Cloud", "DevOps", "Networking", "Blockchain",
            "IT", "Computer Engineering", "Electronics", "Mechanical", "Civil",
            "Entrepreneurship", "Startups", "Finance", "Marketing", "Management"
        ))
    )
}

private val interestEmojiMap = listOf(
    listOf("music", "song", "band", "audio", "guitar", "piano", "drums", "violin", "keyboard", "flute", "singers", "artists") to "🎸",
    listOf("code", "coding", "programming", "dev", "software", "ai", "ml", "data science", "cybersecurity", "cloud", "devops", "networking", "blockchain", "engineering", "it", "kotlin", "c++", "rust", "python", "java", "swift", "react") to "💻",
    listOf("game", "gaming", "esport", "chess") to "🎮",
    listOf("art", "design", "paint", "creative", "sketching", "drawing") to "🎨",
    listOf("film", "movie", "cinema", "series", "horror", "sci-fi", "thriller", "drama", "action", "comedy") to "🎬",
    listOf("sport", "fitness", "run", "gym", "hiking", "cycling", "swimming") to "🏃",
    listOf("food", "cook", "cafe", "coffee", "baking") to "🍜",
    listOf("travel", "hike", "adventure") to "✈️",
    listOf("photo", "camera", "photography") to "📷",
    listOf("book", "read", "writing") to "📚",
    listOf("startup", "founder", "business", "entrepreneurship", "finance", "marketing", "management") to "🚀",
    listOf("science", "space", "physics", "astronomy", "math") to "🔬"
)

object InterestMatcher {
    /**
     * Canonical comparison key:
     * - Trims whitespace
     * - Lowercases
     * - Collapses internal multiple spaces to a single space
     * - Unifies common slash and ampersand spacing ("AI / ML" -> "ai/ml")
     */
    fun normalize(tag: String): String {
        return tag.trim()
            .lowercase()
            .replace(Regex("\\s+"), " ")
            .replace(" / ", "/")
            .replace(" & ", "&")
    }

    /**
     * Splits raw text into clean, trimmed, non-empty, deduplicated tags.
     */
    fun parseTags(raw: String?, max: Int = 20): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(',', ';', '|', '·', '\n')
            .map { it.trim() }
            .filter { it.length >= 2 }
            .distinctBy { normalize(it) }
            .take(max)
    }

    /**
     * Exact token intersection matching.
     * Compares normalized keys, preserves User A's display formatting.
     */
    fun findMutualTags(mine: List<String>, theirs: List<String>): List<String> {
        if (mine.isEmpty() || theirs.isEmpty()) return emptyList()
        val myMap = mine.associateBy { normalize(it) }
        val theirNormalized = theirs.map { normalize(it) }.toSet()
        return myMap.filterKeys { it in theirNormalized }.values.toList()
    }
}

fun parseInterests(raw: String?, max: Int = 20): List<Interest> {
    if (raw.isNullOrBlank()) return emptyList()
    val tags = InterestMatcher.parseTags(raw, max)
    return tags.map { token ->
        val lower = token.lowercase()
        val emoji = interestEmojiMap.firstOrNull { (keys, _) ->
            keys.any { key -> lower.contains(key) }
        }?.second ?: "✦"
        Interest(label = token, emoji = emoji)
    }
}

fun sharedInterests(mine: List<Interest>, theirs: List<Interest>): List<Interest> {
    if (mine.isEmpty() || theirs.isEmpty()) return emptyList()
    val myMap = mine.associateBy { InterestMatcher.normalize(it.label) }
    val theirMap = theirs.associateBy { InterestMatcher.normalize(it.label) }
    val mutualKeys = myMap.keys.intersect(theirMap.keys)
    return mutualKeys.mapNotNull { myMap[it] }
}

/** Rough BLE distance estimate in meters from RSSI. */
fun rssiToMeters(rssi: Int): Int {
    val txPower = -59.0
    if (rssi >= 0) return 1
    val ratio = rssi / txPower
    val meters = if (ratio < 1.0) {
        ratio.pow(10.0)
    } else {
        0.89976 * ratio.pow(7.7095) + 0.111
    }
    return meters.coerceIn(1.0, 90.0).roundToInt()
}

enum class ProximityBand { Near, Mid, Far }

fun rssiToBand(rssi: Int): ProximityBand = when {
    rssi >= -55 -> ProximityBand.Near
    rssi >= -72 -> ProximityBand.Mid
    else -> ProximityBand.Far
}

fun NearbyDevice.displayName(): String {
    return if (name.isNotBlank()
        && !name.equals("Unknown", ignoreCase = true)
        && !name.equals("Unknown Device", ignoreCase = true)
    ) name else "Nearby"
}

private fun logDebug(tag: String, message: String) {
    try {
        android.util.Log.d(tag, message)
    } catch (_: Throwable) {
        println("$tag: $message")
    }
}

/** Stable chat key — always the stable identity (dev_*) when available. */
fun NearbyDevice.chatKey(): String {
    val key = if (identity.startsWith("dev_")) identity
              else if (name.startsWith("Nearby ")) id
              else name
    logDebug("ProximityUtils", "[IDENTITY_TRACE] chatKey input=identity:$identity name:$name id:$id output=$key")
    return key
}

/**
 * Safe nav path segment: colons in BLE MACs (AA:BB:CC:DD:EE:FF) break
 * Compose Navigation route args, so we encode ONLY actual MACs.
 * Stable identities (dev_*) and nicknames pass through unchanged.
 */
private val BLE_MAC_REGEX = Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")

fun encodePeerRouteId(raw: String): String {
    val encoded = if (BLE_MAC_REGEX.matches(raw)) raw.replace(":", "|") else raw
    logDebug("ProximityUtils", "[IDENTITY_TRACE] encodedRoute input=$raw output=$encoded")
    return encoded
}

fun decodePeerRouteId(encoded: String): String {
    // Only decode pipe-separated MACs produced by encodePeerRouteId above.
    // dev_* identities and plain nicknames are returned unchanged.
    val decoded = if (encoded.matches(Regex("^([0-9A-Fa-f]{2}\\|){5}[0-9A-Fa-f]{2}$")))
        encoded.replace("|", ":")
    else
        encoded
    logDebug("ProximityUtils", "[IDENTITY_TRACE] decodedPeerId input=$encoded output=$decoded")
    return decoded
}

fun stableAngle(id: String, index: Int, total: Int): Float {
    val hash = abs(id.hashCode() % 360)
    val base = if (total <= 0) 0f else (360f / total) * index
    return (base + (hash % 28) - 14f + 270f) % 360f
}
