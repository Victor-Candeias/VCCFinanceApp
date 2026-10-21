package pt.vcc.financeapp.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PinPolicyTest {
    private fun validate(pin: String) = PinPolicy.validate(pin.toCharArray())

    @Test
    fun acceptsNonTrivialPinsBetweenFourAndEightDigits() {
        assertNull(validate("2580"))
        assertNull(validate("19730412"))
        assertNull(validate("1123"))
    }

    @Test
    fun rejectsInvalidLengthsAndCharacters() {
        assertEquals(PinValidationError.TOO_SHORT, validate("259"))
        assertEquals(PinValidationError.TOO_LONG, validate("258025802"))
        assertEquals(PinValidationError.NOT_DIGITS, validate("25a0"))
    }

    @Test
    fun rejectsRepeatedAndSequentialPins() {
        assertEquals(PinValidationError.TOO_SIMPLE, validate("0000"))
        assertEquals(PinValidationError.TOO_SIMPLE, validate("123456"))
        assertEquals(PinValidationError.TOO_SIMPLE, validate("9876"))
    }

    @Test
    fun hasherIsDeterministicPerSalt() {
        val salt = PinHasher.newSalt()
        val a = PinHasher.derive("2580".toCharArray(), salt, 1_000)
        val b = PinHasher.derive("2580".toCharArray(), salt, 1_000)
        val c = PinHasher.derive("2580".toCharArray(), PinHasher.newSalt(), 1_000)
        assertEquals(32, a.size)
        assertTrue(a.contentEquals(b))
        assertFalse(a.contentEquals(c))
    }
}
