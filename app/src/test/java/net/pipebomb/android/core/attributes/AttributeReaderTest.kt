package net.pipebomb.android.core.attributes

import net.pipebomb.android.core.model.AttributeMap
import net.pipebomb.android.core.net.PipeBombJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AttributeReaderTest {
    private val json = """
        {
          "title": {"type":"string","values":["Raw title"],"formatted":["Formatted title"]},
          "artist": {"type":"string","values":["A","B"],"formatted":[]},
          "year": {"type":"integer","values":[1999],"formatted":[]},
          "duration": {"type":"decimal","values":[123.5],"formatted":[]},
          "explicit": {"type":"boolean","values":[true],"formatted":[]},
          "front": {"type":"buffer","values":[{"uuid":"u","url":"https://x/y.webp","extension":"webp"}],"formatted":[]},
          "future": {"type":"future_type","values":["x"],"formatted":[]},
          "empty": {"type":"string","values":[],"formatted":[]}
        }
    """.trimIndent()

    private val map: AttributeMap = PipeBombJson.decodeFromString(json)

    @Test
    fun usesFormattedValueWhenAvailable() {
        assertEquals("Formatted title", AttributeReader.string(map, "title"))
    }

    @Test
    fun fallsBackToRawValue() {
        assertEquals(listOf("A", "B"), AttributeReader.strings(map, "artist", multiple = true))
        assertEquals("A", AttributeReader.string(map, "artist"))
    }

    @Test
    fun readsTypedValues() {
        assertEquals(1999L, AttributeReader.integer(map, "year"))
        assertEquals(123.5, AttributeReader.decimal(map, "duration")!!, 0.001)
        assertEquals(true, AttributeReader.boolean(map, "explicit"))
    }

    @Test
    fun readsBufferResource() {
        val resource = AttributeReader.resource(map, "front")
        assertEquals("https://x/y.webp", resource?.url)
        assertEquals("webp", resource?.extension)
    }

    @Test
    fun wrongTypeReturnsNull() {
        assertNull(AttributeReader.string(map, "year"))
        assertNull(AttributeReader.integer(map, "title"))
    }

    @Test
    fun missingAndEmptyReturnNull() {
        assertNull(AttributeReader.string(map, "missing"))
        assertNull(AttributeReader.string(map, "empty"))
        assertNull(AttributeReader.any(null, "title"))
    }

    @Test
    fun unknownTypeDoesNotCrash() {
        assertTrue(AttributeReader.any(map, "future") != null)
        assertNull(AttributeReader.string(map, "future"))
    }
}
