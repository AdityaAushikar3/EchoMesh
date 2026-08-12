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
    listOf("code", "coding", "programming", "dev", "software", "ai", "ml", "data science", "cybersecurity", "cloud", "devops", "networking", "blockchain", "engineering", "it") to "💻",
    listOf("game", "gaming", "esport") to "🎮",
    listOf("art", "design", "paint", "creative") to "🎨",
    listOf("film", "movie", "cinema", "series", "horror", "sci-fi", "thriller", "drama", "action", "comedy") to "🎬",
    listOf("sport", "fitness", "run", "gym") to "🏃",
    listOf("food", "cook", "cafe") to "🍜",
    listOf("travel", "hike", "adventure") to "✈️",
    listOf("photo", "camera") to "📷",
    listOf("book", "read", "writing") to "📚",
    listOf("startup", "founder", "business", "entrepreneurship", "finance", "marketing", "management") to "🚀",
    listOf("science", "space", "physics") to "🔬"
)

fun parseInterests(raw: String?): List<Interest> {
    if (raw.isNullOrBlank()) return emptyList()
    return raw.split(',', ';', '|', '·')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }
        .take(8)
        .map { token ->
            val lower = token.lowercase()
            val emoji = interestEmojiMap.firstOrNull { (keys, _) ->
                keys.any { key -> lower.contains(key) }
            }?.second ?: "✦"
            val label = token.removePrefix(emoji).trim().replaceFirstChar { it.uppercase() }
            Interest(label = label.ifBlank { token }, emoji = emoji)
        }
}

fun sharedInterests(mine: List<Interest>, theirs: List<Interest>): List<Interest> {
    if (mine.isEmpty() || theirs.isEmpty()) return emptyList()
    return theirs.filter { other ->
        mine.any { self ->
            self.label.equals(other.label, ignoreCase = true) ||
                self.label.contains(other.label, ignoreCase = true) ||
                other.label.contains(self.label, ignoreCase = true)
        }
    }
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

/** Stable chat key — always the nickname so MAC rotation doesn't break history. */
fun NearbyDevice.chatKey(): String =
    if (identity.startsWith("dev_")) identity
    else if (name.startsWith("Nearby ")) id
    else name

/** Safe nav path segment (colons break Compose Navigation path args). */
fun encodePeerRouteId(raw: String): String = raw.replace(":", "_")

fun decodePeerRouteId(encoded: String): String =
    if (encoded.contains("_") && !encoded.contains(":")) encoded.replace("_", ":") else encoded


fun stableAngle(id: String, index: Int, total: Int): Float {
    val hash = abs(id.hashCode() % 360)
    val base = if (total <= 0) 0f else (360f / total) * index
    return (base + (hash % 28) - 14f + 270f) % 360f
}
