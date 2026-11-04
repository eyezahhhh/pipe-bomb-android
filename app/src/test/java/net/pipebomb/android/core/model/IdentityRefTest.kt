package net.pipebomb.android.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IdentityRefTest {
    @Test
    fun parsesThreePartIdentity() {
        assertEquals(
            IdentityRef("plugin", "identifier", "value"),
            parseIdentityRef("plugin~identifier~value"),
        )
    }

    @Test
    fun rejectsMalformedOrBlankIdentity() {
        assertNull(parseIdentityRef("album-uuid"))
        assertNull(parseIdentityRef("plugin~identifier"))
        assertNull(parseIdentityRef("plugin~~value"))
        assertNull(parseIdentityRef("plugin~identifier~value~extra"))
    }
}
