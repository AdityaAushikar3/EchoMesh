package chat.bitchat.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InterestMatcherTest {

    @Test
    fun testTagParsingCommaSemicolonPipe() {
        val raw = "AI, Kotlin; Guitar | Photography · Chess\nMachine Learning"
        val tags = InterestMatcher.parseTags(raw)
        assertEquals(listOf("AI", "Kotlin", "Guitar", "Photography", "Chess", "Machine Learning"), tags)
    }

    @Test
    fun testWhitespaceTrimming() {
        val raw = "   AI   ,   Kotlin  ,  Machine Learning   "
        val tags = InterestMatcher.parseTags(raw)
        assertEquals(listOf("AI", "Kotlin", "Machine Learning"), tags)
    }

    @Test
    fun testCaseInsensitiveDuplicatePrevention() {
        val raw = "Guitar, guitar, GUITAR, Guitar, Coding, coding"
        val tags = InterestMatcher.parseTags(raw)
        assertEquals(listOf("Guitar", "Coding"), tags)
    }

    @Test
    fun testExactMatchingVsSubstringFalseMatches() {
        val myTags = listOf("AI", "Art", "Go", "Kotlin")
        val theirTags = listOf("ai", "Sailing", "Martial Arts", "Algorithms", "kotlin")

        val mutual = InterestMatcher.findMutualTags(myTags, theirTags)

        // "AI" matches "ai"
        assertTrue("AI must match ai", mutual.contains("AI"))
        // "Kotlin" matches "kotlin"
        assertTrue("Kotlin must match kotlin", mutual.contains("Kotlin"))

        // Substring false matches MUST be rejected:
        assertFalse("AI must NOT match Sailing", mutual.any { it.equals("Sailing", ignoreCase = true) })
        assertFalse("Art must NOT match Martial Arts", mutual.contains("Art"))
        assertFalse("Go must NOT match Algorithms", mutual.contains("Go"))

        assertEquals(listOf("AI", "Kotlin"), mutual)
    }

    @Test
    fun testSpecialCharactersAndSlashNormalization() {
        val myTags = listOf("AI / ML", "C++", "C#", "UI/UX")
        val theirTags = listOf("ai/ml", "c++", "c#", "ui / ux")

        val mutual = InterestMatcher.findMutualTags(myTags, theirTags)
        assertEquals(listOf("AI / ML", "C++", "C#", "UI/UX"), mutual)
    }

    @Test
    fun testMultipleMatchesAndOrdering() {
        val mine = listOf("AI", "Kotlin", "Guitar", "Rock", "Gaming")
        val theirs = listOf("AI", "Kotlin", "Guitar", "Chess", "Hiking")

        val mutual = InterestMatcher.findMutualTags(mine, theirs)
        assertEquals(listOf("AI", "Kotlin", "Guitar"), mutual)
        assertEquals(3, mutual.size)
    }

    @Test
    fun testMaxTwentyTagsLimit() {
        val rawTwentyOne = (1..25).joinToString(", ") { "Skill$it" }
        val tags = InterestMatcher.parseTags(rawTwentyOne, max = 20)
        assertEquals(20, tags.size)
        assertEquals("Skill1", tags.first())
        assertEquals("Skill20", tags.last())
    }

    @Test
    fun testLegacyConversionMergesAllFields() {
        val interests = "AI, Coding"
        val music = "Rock, Jazz"
        val movies = "Sci-Fi, Inception"
        val singers = "Daft Punk"
        val career = "Software Engineering"

        val legacyMerged = listOfNotNull(
            interests.takeIf { it.isNotBlank() },
            music.takeIf { it.isNotBlank() },
            movies.takeIf { it.isNotBlank() },
            singers.takeIf { it.isNotBlank() },
            career.takeIf { it.isNotBlank() }
        ).joinToString(", ")

        val tags = InterestMatcher.parseTags(legacyMerged, max = 20)
        assertEquals(
            listOf("AI", "Coding", "Rock", "Jazz", "Sci-Fi", "Inception", "Daft Punk", "Software Engineering"),
            tags
        )
    }

    @Test
    fun testSharedInterestsDataObjectsExactMatching() {
        val myInterests = parseInterests("AI, Art, Kotlin, Guitar")
        val theirInterests = parseInterests("ai, Sailing, Martial Arts, kotlin, Photography")

        val shared = sharedInterests(myInterests, theirInterests)
        val sharedLabels = shared.map { it.label }

        assertEquals(listOf("AI", "Kotlin"), sharedLabels)
    }
}
