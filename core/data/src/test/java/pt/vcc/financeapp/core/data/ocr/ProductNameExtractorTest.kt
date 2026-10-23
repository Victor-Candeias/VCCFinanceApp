package pt.vcc.financeapp.core.data.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductNameExtractorTest {

    @Test
    fun `texto maior primeiro e ignora precos, codigos e pesos`() {
        val names = ProductNameExtractor.candidates(
            listOf(
                OcrLine("PVP", 20),
                OcrLine("Bolachas Maria", 90),
                OcrLine("1,49 €", 120),
                OcrLine("5601234567890", 20),
                OcrLine("400 g", 30),
                OcrLine("Marca Continente", 40),
            ),
        )
        assertEquals(listOf("Bolachas Maria", "Marca Continente"), names)
    }

    @Test
    fun `junta duas linhas seguidas do mesmo tamanho e passa maiusculas a frase`() {
        val names = ProductNameExtractor.candidates(listOf(OcrLine("ARROZ CAROLINO", 80), OcrLine("CIGALA", 78), OcrLine("1 kg", 20)))
        assertEquals("Arroz carolino cigala", names.first())
        assertTrue(names.containsAll(listOf("Arroz carolino", "Cigala")))
    }

    @Test
    fun `sem texto util nao sugere nada`() {
        assertTrue(ProductNameExtractor.candidates(listOf(OcrLine("1,49 €"), OcrLine("-30%"), OcrLine("12 34"))).isEmpty())
    }
}
