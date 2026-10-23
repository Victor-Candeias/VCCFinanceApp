package pt.vcc.financeapp.core.data.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PriceCandidateParserTest {

    private fun parse(vararg lines: String) = PriceCandidateParser.parse(lines.map { OcrLine(it) })

    @Test
    fun `reconhece os formatos portugueses`() {
        listOf("1,49 €", "€ 1,49", "1.49€", "1€49", "1 € 49", "1 49 €", "PVP 1,49 EUR").forEach { text ->
            val result = parse(text)
            assertEquals(text, 149L, result.suggested?.cents)
            assertEquals(text, PriceKind.SALE, result.suggested?.kind)
        }
    }

    @Test
    fun `distingue preco de venda do preco por kg e por litro`() {
        val result = PriceCandidateParser.parse(
            listOf(OcrLine("Bananas da Madeira", 20), OcrLine("1,49 €", 90), OcrLine("1,49 €/kg", 20)),
        )
        assertEquals(setOf(PriceKind.SALE to 149L, PriceKind.PER_KG to 149L), result.candidates.map { it.kind to it.cents }.toSet())
        assertEquals(PriceKind.SALE, result.suggested?.kind)

        assertEquals(PriceKind.PER_L, parse("Leite 1,09 € / L").candidates.single().kind)
        assertEquals(PriceKind.PER_KG, parse("Preço/kg: 3,98 €").candidates.single().kind)
        assertEquals(PriceKind.PER_L, parse("0,45 €/lt").candidates.single().kind)
    }

    @Test
    fun `preco antigo, promocao e desconto`() {
        val sameLine = parse("Antes 1,99 € Agora 1,49 €")
        assertEquals(PriceKind.OLD, sameLine.candidates.single { it.cents == 199L }.kind)
        assertEquals(149L, sameLine.suggested?.cents)

        val labels = parse("-30%", "Poupe 0,50 €", "-0,20 €", "Promoção 1,19 €")
        assertTrue(labels.candidates.none { it.cents == 30L || it.cents == 3000L })
        assertEquals(PriceKind.DISCOUNT, labels.candidates.single { it.cents == 50L }.kind)
        assertEquals(PriceKind.DISCOUNT, labels.candidates.single { it.cents == 20L }.kind)
        assertEquals(119L, labels.suggested?.cents)
    }

    @Test
    fun `texto maior pesa mais do que a ordem ou o valor`() {
        val result = PriceCandidateParser.parse(
            listOf(OcrLine("2,49 €", 20), OcrLine("1,29 €", 100), OcrLine("3,99 €", 20)),
        )
        assertEquals(129L, result.suggested?.cents)
        assertEquals(3, result.candidates.size)
    }

    @Test
    fun `ignora numeros que nao sao precos e nao sugere sem confianca`() {
        assertTrue(parse("Peso 0,750 kg", "Lote 12 34", "Validade 12.10.2026", "1.234,56").candidates.isEmpty())

        val bare = parse("1,49")
        assertEquals(PriceKind.UNKNOWN, bare.candidates.single().kind)
        assertNull(bare.suggested)

        val perKgOnly = parse("2,98 €/kg")
        assertEquals(1, perKgOnly.candidates.size)
        assertNull(perKgOnly.suggested)
    }

    @Test
    fun `guarda o texto bruto e a linha de cada candidato`() {
        val result = parse("Atum", "Promo 0,99 €")
        assertEquals("Atum\nPromo 0,99 €", result.rawText)
        assertEquals("Promo 0,99 €", result.candidates.single().rawText)
    }

    @Test
    fun `junta euros grandes e centimos em expoente lidos em linhas separadas`() {
        val result = PriceCandidateParser.parse(
            listOf(
                OcrLine("Arroz Carolino", height = 30, left = 40, top = 20, right = 400),
                OcrLine("1", height = 200, left = 100, top = 100, right = 180),
                OcrLine("49€", height = 80, left = 190, top = 110, right = 320),
                OcrLine("1,14 €/kg", height = 25, left = 40, top = 330, right = 220),
            ),
        )
        assertEquals(149L, result.suggested?.cents)
        assertEquals(PriceKind.SALE, result.suggested?.kind)
        assertTrue(result.candidates.any { it.cents == 114L && it.kind == PriceKind.PER_KG })
    }

    @Test
    fun `nao junta numeros afastados ou abaixo dos euros`() {
        val farAway = PriceCandidateParser.parse(
            listOf(OcrLine("2", height = 100, left = 0, top = 0, right = 50), OcrLine("50", height = 40, left = 600, top = 0, right = 650)),
        )
        assertTrue(farAway.candidates.isEmpty())
        val below = PriceCandidateParser.parse(
            listOf(OcrLine("2", height = 100, left = 0, top = 0, right = 50), OcrLine("50", height = 40, left = 60, top = 150, right = 110)),
        )
        assertTrue(below.candidates.isEmpty())
    }

    @Test
    fun `centimos colados aos euros no maior texto da etiqueta`() {
        val result = PriceCandidateParser.parse(listOf(OcrLine("Leite meio-gordo", 30), OcrLine("149€", 150)))
        assertEquals(149L, result.suggested?.cents)
        assertTrue(PriceCandidateParser.parse(listOf(OcrLine("Leite", 150), OcrLine("500", 30))).candidates.isEmpty())
    }

    @Test
    fun `corrige trocas comuns do OCR`() {
        listOf("1,4O €", "I,49 €", "1,49E", "1, 49 €", "O,99 €").zip(listOf(140L, 149L, 149L, 149L, 99L)).forEach { (text, cents) ->
            assertEquals(text, cents, parse(text).suggested?.cents)
        }
        assertTrue(parse("Leite 1,5l").candidates.isEmpty())
    }
}
