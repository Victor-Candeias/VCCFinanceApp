package pt.vcc.financeapp.core.data.usecase

import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.vcc.financeapp.core.data.testing.FakeCatalogRepository
import pt.vcc.financeapp.core.data.testing.FakePurchaseRepository
import pt.vcc.financeapp.core.data.testing.FakeShoppingListRepository
import pt.vcc.financeapp.core.data.testing.FakeTransactionRepository
import pt.vcc.financeapp.core.model.PurchaseItem
import pt.vcc.financeapp.core.model.PurchaseStatus
import pt.vcc.financeapp.core.model.PurchaseType
import pt.vcc.financeapp.core.model.ShoppingList
import pt.vcc.financeapp.core.model.ShoppingListItem

class ShoppingListUseCasesTest {
    private val now = Instant.parse("2026-10-21T09:30:00Z")
    private val clock = Clock.fixed(now, ZoneId.of("Europe/Lisbon"))
    private val lists = FakeShoppingListRepository()
    private val purchases = FakePurchaseRepository(FakeTransactionRepository())
    private val catalog = FakeCatalogRepository(DefaultProductCatalog.categories, DefaultProductCatalog.products(Instant.EPOCH))
    private val saveList = SaveShoppingListUseCase(lists, clock)
    private val saveItem = SaveShoppingListItemUseCase(lists, purchases, catalog, clock)
    private val manage = ManageShoppingListUseCase(lists, purchases, clock)
    private val start = StartPlannedPurchaseUseCase(purchases, lists, clock)

    private fun listItem(id: String, price: Long? = null, quantity: String = "1", position: Int = 0) =
        ShoppingListItem(id, "l-1", id, BigDecimal(quantity), estimatedPriceMinor = price, position = position)

    private fun purchaseItem(id: String, listItemId: String? = null) =
        PurchaseItem(id, "p-1", id, BigDecimal.ONE, 100, shoppingListItemId = listItemId)

    private suspend fun givenList(vararg items: ShoppingListItem, budget: Long? = null) =
        lists.save(ShoppingList("l-1", "Semana", budget, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH), items.toList())

    private fun input(name: String = "Leite", quantity: String? = "2", price: Long? = 95, id: String? = null) =
        ShoppingListItemInput("l-1", id, name, quantity?.let(::BigDecimal), estimatedPriceMinor = price)

    @Test
    fun reducerClassifiesToBuyAddedSkippedAndExtras() {
        val listItems = listOf(listItem("leite", 95, "2"), listItem("pao", 300, "0.5"), listItem("ovos"), listItem("fruta"))
        val purchaseItems = listOf(purchaseItem("i-1", "leite"), purchaseItem("i-2"), purchaseItem("i-3", "apagado"))

        val progress = PlannedPurchaseReducer.reduce(listItems, purchaseItems, skippedIds = setOf("ovos", "leite"))

        assertEquals(listOf("pao", "fruta"), progress.toBuy.map { it.id })
        assertEquals(listOf("i-1"), progress.added.map { it.id })
        assertEquals(listOf("ovos"), progress.skipped.map { it.id })
        assertEquals(listOf("i-2", "i-3"), progress.extras.map { it.id })
        assertEquals(190L + 150L, progress.estimateMinor)
        assertEquals(4, progress.plannedCount)
        assertEquals(2, progress.handledCount)
    }

    @Test
    fun startingCopiesBudgetAndResumesSamePurchase() = runTest {
        givenList(listItem("leite"), budget = 4000)

        val started = start("l-1") as StartPlannedPurchaseResult.Started

        assertEquals(PurchaseType.PLANNED, started.purchase.type)
        assertEquals("l-1", started.purchase.shoppingListId)
        assertEquals(4000L, started.purchase.budgetMinor)
        assertEquals(started.purchase.id, (start("l-1") as StartPlannedPurchaseResult.Started).purchase.id)
        assertEquals(StartPlannedPurchaseResult.ListNotFound, start("x"))
    }

    @Test
    fun onlyOnePlannedPurchaseCanBeActive() = runTest {
        givenList()
        lists.save(ShoppingList("l-2", "Festa", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH))
        val first = (start("l-1") as StartPlannedPurchaseResult.Started).purchase

        val conflict = start("l-2") as StartPlannedPurchaseResult.Conflict
        assertEquals(first.id, conflict.active.id)

        val replaced = (start("l-2", replaceActive = true) as StartPlannedPurchaseResult.Started).purchase
        assertNotEquals(first.id, replaced.id)
        assertEquals(PurchaseStatus.CANCELLED, purchases.getById(first.id)!!.status)
        assertEquals(replaced.id, purchases.getActive(PurchaseType.PLANNED)!!.id)
    }

    @Test
    fun listIsLockedWhilePurchaseIsActive() = runTest {
        givenList(listItem("leite"))
        start("l-1")

        assertEquals(setOf(ShoppingListError.IN_USE), (saveItem(input()) as SaveShoppingListItemResult.Invalid).errors)
        assertFalse(manage.removeItem("l-1", "leite"))
        assertFalse(manage.delete("l-1"))
        assertFalse(manage.setArchived("l-1", true))
        assertEquals(1, lists.getItems("l-1").size)

        purchases.update(purchases.getActive(PurchaseType.PLANNED)!!.copy(status = PurchaseStatus.CANCELLED))
        assertTrue(manage.delete("l-1"))
        assertTrue(lists.getItems("l-1").isEmpty())
    }

    @Test
    fun savingListValidatesNameAndBudget() = runTest {
        val saved = (saveList(null, "  Compras   do mês ", 0) as SaveShoppingListResult.Saved).list
        assertEquals("Compras do mês", saved.name)
        assertEquals(null, saved.budgetMinor)

        val invalid = saveList(null, " ", -1) as SaveShoppingListResult.Invalid
        assertEquals(setOf(ShoppingListError.BLANK_NAME, ShoppingListError.INVALID_BUDGET), invalid.errors)
        assertEquals(setOf(ShoppingListError.NOT_FOUND), (saveList("x", "Nome", null) as SaveShoppingListResult.Invalid).errors)
    }

    @Test
    fun itemsAppendEditAndMove() = runTest {
        givenList()
        val leite = (saveItem(input()) as SaveShoppingListItemResult.Saved).item
        val pao = (saveItem(input("Pão", "1", null)) as SaveShoppingListItemResult.Saved).item
        assertEquals(listOf(0, 1), listOf(leite.position, pao.position))

        val edited = (saveItem(input("Leite magro", "3", 99, id = leite.id)) as SaveShoppingListItemResult.Saved).item
        assertEquals(0, edited.position)
        assertEquals(BigDecimal("3"), edited.quantity)

        assertTrue(manage.moveItem("l-1", pao.id, up = true))
        assertEquals(listOf("Pão", "Leite magro"), lists.getItems("l-1").map { it.name })
        assertFalse(manage.moveItem("l-1", pao.id, up = true))

        val errors = (saveItem(input("", "0", -5)) as SaveShoppingListItemResult.Invalid).errors
        assertEquals(setOf(ShoppingListError.BLANK_NAME, ShoppingListError.INVALID_QUANTITY, ShoppingListError.INVALID_PRICE), errors)
    }

    @Test
    fun duplicateCopiesItemsWithNewIds() = runTest {
        givenList(listItem("leite", 95), listItem("pao", position = 1), budget = 2000)
        manage.setArchived("l-1", true)

        val copy = manage.duplicate("l-1")!!

        assertEquals("Semana (cópia)", copy.name)
        assertEquals(2000L, copy.budgetMinor)
        assertFalse(copy.isArchived)
        val items = lists.getItems(copy.id)
        assertEquals(listOf("leite", "pao"), items.map { it.name })
        assertTrue(items.none { it.id == "leite" || it.id == "pao" })
        assertEquals(2, lists.getItems("l-1").size)
    }
}
