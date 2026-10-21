package pt.vcc.financeapp.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pt.vcc.financeapp.core.model.AmountParser.Reason
import pt.vcc.financeapp.core.model.AmountParser.Result

class AmountParserTest {
    @Test
    fun parsesPortugueseFormatWithThousandsSeparator() {
        assertEquals(123_456L, AmountParser.parseOrNull("1.234,56"))
        assertEquals(123_456L, AmountParser.parseOrNull("1 234,56 €"))
        assertEquals(123_456_700L, AmountParser.parseOrNull("1.234.567"))
        assertEquals(50L, AmountParser.parseOrNull(",5"))
    }

    @Test
    fun acceptsDotAsDecimalOnlyWithOneOrTwoDigits() {
        assertEquals(1_250L, AmountParser.parseOrNull("12.5"))
        assertEquals(1_250L, AmountParser.parseOrNull("12.50"))
        assertEquals(123_400L, AmountParser.parseOrNull("1.234"))
    }

    @Test
    fun handlesSigns() {
        assertEquals(-4_550L, AmountParser.parseOrNull("-45,50"))
        assertEquals(-4_550L, AmountParser.parseOrNull("45,50-"))
        assertEquals(-4_550L, AmountParser.parseOrNull("−45,50"))
        assertEquals(4_550L, AmountParser.parseOrNull("+45,50"))
    }

    @Test
    fun respectsCurrencyFractionDigits() {
        assertEquals(1_234L, AmountParser.parseOrNull("1.234", fractionDigits = 0))
        assertEquals(Result.Failure(Reason.TOO_MANY_DECIMALS), AmountParser.parse("10,5", fractionDigits = 0))
        assertEquals(Result.Failure(Reason.TOO_MANY_DECIMALS), AmountParser.parse("10,555"))
    }

    @Test
    fun rejectsInvalidInput() {
        assertEquals(Result.Failure(Reason.EMPTY), AmountParser.parse("  "))
        assertEquals(Result.Failure(Reason.INVALID_FORMAT), AmountParser.parse("12a"))
        assertEquals(Result.Failure(Reason.INVALID_FORMAT), AmountParser.parse("1,2,3"))
        assertEquals(Result.Failure(Reason.INVALID_FORMAT), AmountParser.parse("12.34.5"))
        assertEquals(Result.Failure(Reason.OUT_OF_RANGE), AmountParser.parse("99999999999999999999"))
        assertNull(AmountParser.parseOrNull("-"))
    }
}
