package pt.vcc.financeapp.core.model

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PurchaseMathTest {
    @Test
    fun itemTotalRoundsHalfUpToTheCent() {
        // 0,333 kg × 2,99 €/kg = 0,99567 € → 1,00 €
        assertEquals(100, PurchaseMath.itemTotal(BigDecimal("0.333"), 299, 0))
        // 1,5 × 0,25 € = 0,375 € → 0,38 €
        assertEquals(38, PurchaseMath.itemTotal(BigDecimal("1.5"), 25, 0))
    }

    @Test
    fun itemTotalSubtractsDiscountButNeverGoesNegative() {
        assertEquals(250, PurchaseMath.itemTotal(BigDecimal("3"), 100, 50))
        assertEquals(0, PurchaseMath.itemTotal(BigDecimal("1"), 100, 500))
    }

    @Test
    fun totalSumsItemTotals() {
        fun item(id: String, quantity: String, price: Long, discount: Long = 0) =
            PurchaseItem(id, "p-1", "x", BigDecimal(quantity), price, discount)

        assertEquals(380 + 90, PurchaseMath.total(listOf(item("a", "2", 190), item("b", "1", 100, 10))))
    }

    @Test
    fun parseQuantityAcceptsCommaAndRejectsNonPositive() {
        assertEquals(BigDecimal("1.5"), PurchaseMath.parseQuantity(" 1,5 "))
        assertEquals(BigDecimal("2"), PurchaseMath.parseQuantity("2.00"))
        assertNull(PurchaseMath.parseQuantity("0"))
        assertNull(PurchaseMath.parseQuantity("-1"))
        assertNull(PurchaseMath.parseQuantity("abc"))
    }

    @Test
    fun merchantNormalizationIgnoresCaseAccentsAndSpaces() {
        assertEquals("cafe central", Merchant.normalize("  Café   Central "))
        assertEquals(Merchant.normalize("PINGO DOCE"), Merchant.normalize("pingo doce"))
    }
}
