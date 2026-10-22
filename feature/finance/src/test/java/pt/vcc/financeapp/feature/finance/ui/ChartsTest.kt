package pt.vcc.financeapp.feature.finance.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChartsTest {
    @Test
    fun parsesCategoryColorsAndRejectsInvalidOnes() {
        assertEquals(Color(0xFFE65100), parseHexColor("#E65100"))
        assertEquals(Color(0x80E65100), parseHexColor("#80E65100"))
        assertNull(parseHexColor("E651"))
        assertNull(parseHexColor("#GG5100"))
        assertNull(parseHexColor(null))
    }

    @Test
    fun percentagesAreRoundedWithoutFloatingPoint() {
        assertEquals("33 %", percent(1, 3))
        assertEquals("67 %", percent(2, 3))
        assertEquals("0 %", percent(5, 0))
    }
}
