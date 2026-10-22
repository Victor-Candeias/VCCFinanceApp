package pt.vcc.financeapp.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IbanTest {
    @Test
    fun acceptsValidIbansIgnoringSpacesAndCase() {
        assertTrue(Iban.isValid("PT50 0002 0123 1234 5678 9015 4"))
        assertTrue(Iban.isValid("de89370400440532013000"))
        assertTrue(Iban.isValid("GB82-WEST-1234-5698-7654-32"))
    }

    @Test
    fun rejectsWrongChecksumOrFormat() {
        assertFalse(Iban.isValid("PT50 0002 0123 1234 5678 9015 5"))
        assertFalse(Iban.isValid("PT5"))
        assertFalse(Iban.isValid("1234 0002 0123 1234 5678 9015 4"))
        assertFalse(Iban.isValid("PT50 0002 0123 1234 5678 9015 4!"))
    }

    @Test
    fun formatsAndMasks() {
        assertEquals("PT50 0002 0123 1234 5678 9015 4", Iban.format("pt50000201231234567890154"))
        assertEquals("PT50 •••• •••• 0154", Iban.mask("PT50 0002 0123 1234 5678 9015 4"))
    }
}
