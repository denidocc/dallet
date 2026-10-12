package com.dallet.app.ui.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dallet.app.AppStrings
import com.dallet.app.R
import com.dallet.app.ledger.CategoryKind
import com.dallet.app.ledger.Ledger
import com.dallet.app.ledger.Money
import com.dallet.app.ledger.QuantityFormat
import com.dallet.app.ledger.QuantityUnit
import com.dallet.app.ledger.ShoppingItem
import com.dallet.app.ledger.ShoppingListDetail
import com.dallet.app.ledger.ShoppingListSummary
import com.dallet.app.ledger.ShoppingService
import com.dallet.app.ledger.percentChange
import com.dallet.app.ui.SelectableOption
import com.dallet.app.ui.labelRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

enum class HubTab { LISTS, ARCHIVE, PRODUCTS }

data class ShoppingListRowUi(
    val id: Long,
    val title: String,
    val subtitle: String,
    val totalLabel: String
)

data class ProductRowUi(
    val id: Long,
    val name: String,
    val unitLabel: String,
    val priceLabel: String
)

data class ShoppingHubUiState(
    val tab: HubTab = HubTab.LISTS,
    val lists: List<ShoppingListRowUi> = emptyList(),
    val archived: List<ShoppingListRowUi> = emptyList(),
    val products: List<ProductRowUi> = emptyList()
)

data class SuggestionUi(
    val name: String,
    val unit: QuantityUnit,
    val lastPriceMinor: Long?
)

data class ShoppingItemUi(
    val id: Long,
    val name: String,
    val unit: QuantityUnit,
    val quantityInput: String,
    val priceInput: String,
    val quantityLabel: String,
    val priceLabel: String,
    val lineTotalLabel: String,
    val checked: Boolean
)

data class ShoppingEditorUiState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val listId: Long = 0L,
    val title: String = "",
    val readOnly: Boolean = false,
    val items: List<ShoppingItemUi> = emptyList(),
    val totalLabel: String = "0.00",
    val checkedTotalLabel: String = "0.00",
    val checkedCount: Int = 0,
    val closedLabel: String? = null,
    val hasExpense: Boolean = false,
    val note: String = "",
    val operationId: Long? = null,
    val nameInput: String = "",
    val quantityInput: String = "1",
    val unit: QuantityUnit = QuantityUnit.PIECE,
    val priceInput: String = "",
    val suggestions: List<SuggestionUi> = emptyList(),
    val message: String? = null,
    val isError: Boolean = false
)

data class CloseFormUiState(
    val accounts: List<SelectableOption> = emptyList(),
    val categories: List<SelectableOption> = emptyList(),
    val selectedAccountId: Long? = null,
    val selectedCategoryId: Long? = null,
    val dateInput: String = LocalDate.now().toString(),
    val timeInput: String = "",
    val carryOver: Boolean = true,
    val submitting: Boolean = false,
    val errorMessage: String? = null
)

data class PricePointRowUi(
    val dateLabel: String,
    val priceLabel: String,
    val changeLabel: String?,
    val priceWentUp: Boolean?
)

data class ChartPointUi(val x: Float, val y: Float)

data class ProductDetailUiState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val name: String = "",
    val unitLabel: String = "",
    val lastPriceLabel: String = "",
    val minLabel: String = "",
    val maxLabel: String = "",
    val changeLabel: String? = null,
    val priceWentUp: Boolean? = null,
    val chart: List<ChartPointUi> = emptyList(),
    val rows: List<PricePointRowUi> = emptyList()
)

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val DISPLAY_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

class ShoppingViewModel(
    private val ledger: Ledger,
    private val service: ShoppingService,
    private val strings: AppStrings
) : ViewModel() {

    private var currencyCode: String = "ТМТ"

    private suspend fun syncCurrency() {
        currencyCode = ledger.mainCurrency().code
    }

    private val _hub = MutableStateFlow(ShoppingHubUiState())
    val hub: StateFlow<ShoppingHubUiState> = _hub.asStateFlow()

    private val _editor = MutableStateFlow(ShoppingEditorUiState())
    val editor: StateFlow<ShoppingEditorUiState> = _editor.asStateFlow()

    private val _closeForm = MutableStateFlow(CloseFormUiState())
    val closeForm: StateFlow<CloseFormUiState> = _closeForm.asStateFlow()

    private val _product = MutableStateFlow(ProductDetailUiState())
    val product: StateFlow<ProductDetailUiState> = _product.asStateFlow()

    fun openProduct(productId: Long) {
        viewModelScope.launch {
            _product.value = ProductDetailUiState(loading = true)
            try {
                syncCurrency()
                val found = service.products().firstOrNull { it.id == productId }
                if (found == null) {
                    _product.value = ProductDetailUiState(loading = false, missing = true)
                    return@launch
                }
                val points = service.pricePoints(productId)
                val prices = points.map { it.unitPriceMinor }
                val minPrice = prices.minOrNull()
                val maxPrice = prices.maxOrNull()
                val unit = strings.get(found.unit.labelRes())
                fun label(minor: Long) = "${Money.formatManat(minor)} $currencyCode / $unit"
                val rows = points.mapIndexed { index, point ->
                    val change = points.getOrNull(index - 1)?.let {
                        percentChange(point.unitPriceMinor, it.unitPriceMinor)
                    }
                    PricePointRowUi(
                        dateLabel = point.at.format(DISPLAY_DATE),
                        priceLabel = label(point.unitPriceMinor),
                        changeLabel = change?.let { changeText(it) },
                        priceWentUp = change?.let { if (it == 0) null else it > 0 }
                    )
                }.reversed()
                val lastChange = if (points.size >= 2) {
                    percentChange(points.last().unitPriceMinor, points[points.size - 2].unitPriceMinor)
                } else {
                    null
                }
                val low = minPrice ?: 0L
                val span = ((maxPrice ?: 0L) - low).coerceAtLeast(0L)
                val chart = points.mapIndexed { index, point ->
                    ChartPointUi(
                        x = if (points.size == 1) 0.5f else index.toFloat() / (points.size - 1),
                        y = if (span == 0L) 0.5f else (point.unitPriceMinor - low).toFloat() / span
                    )
                }
                _product.value = ProductDetailUiState(
                    loading = false,
                    name = found.name,
                    unitLabel = unit,
                    lastPriceLabel = prices.lastOrNull()?.let { label(it) } ?: strings.get(R.string.price_not_saved),
                    minLabel = minPrice?.let { label(it) }.orEmpty(),
                    maxLabel = maxPrice?.let { label(it) }.orEmpty(),
                    changeLabel = lastChange?.let { changeText(it) },
                    priceWentUp = lastChange?.let { if (it == 0) null else it > 0 },
                    chart = chart,
                    rows = rows
                )
            } catch (_: Exception) {
                _product.value = ProductDetailUiState(loading = false, missing = true)
            }
        }
    }

    private fun changeText(percent: Int): String = when {
        percent > 0 -> "▲ $percent%"
        percent < 0 -> "▼ ${-percent}%"
        else -> "▬ 0%"
    }

    // region hub

    fun refreshHub() {
        viewModelScope.launch {
            try {
                syncCurrency()
                _hub.update {
                    it.copy(
                        lists = service.openLists().map { row -> row.toRow() },
                        archived = service.archivedLists().map { row -> row.toRow() },
                        products = service.products().map { product ->
                            ProductRowUi(
                                id = product.id,
                                name = product.name,
                                unitLabel = strings.get(product.unit.labelRes()),
                                priceLabel = product.lastPriceMinor
                                    ?.let { price -> "${Money.formatManat(price)} $currencyCode / ${strings.get(product.unit.labelRes())}" }
                                    ?: strings.get(R.string.price_not_saved)
                            )
                        }
                    )
                }
            } catch (_: Exception) {
                // Keep the previous state; the hub is read-only.
            }
        }
    }

    fun selectTab(tab: HubTab) {
        _hub.update { it.copy(tab = tab) }
    }

    fun createList(onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            try {
                val now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)
                val title = strings.get(
                    R.string.shopping_default_title,
                    now.format(DateTimeFormatter.ofPattern("dd.MM"))
                )
                val id = service.createList(title, now)
                refreshHub()
                onCreated(id)
            } catch (_: Exception) {
                // Nothing to open if creation failed.
            }
        }
    }

    fun deleteList(listId: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                service.deleteList(listId)
                refreshHub()
                onDone()
            } catch (_: Exception) {
                _editor.update { it.copy(message = strings.get(R.string.err_delete_failed), isError = true) }
            }
        }
    }

    /** Puts a closed purchase back in work; its expense is deleted (see ShoppingService). */
    fun reopenPurchase(listId: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                service.reopenPurchase(listId)
                reloadEditor(listId)
                refreshHub()
                _editor.update {
                    it.copy(message = strings.get(R.string.msg_purchase_reopened), isError = false)
                }
                onDone()
            } catch (_: Exception) {
                _editor.update {
                    it.copy(message = strings.get(R.string.err_reopen_failed), isError = true)
                }
            }
        }
    }

    fun deleteProduct(productId: Long) {
        viewModelScope.launch {
            try {
                service.deleteProduct(productId)
                refreshHub()
            } catch (_: Exception) {
                // Ignore; the list is reloaded on the next refresh.
            }
        }
    }

    // endregion

    // region editor

    fun openList(listId: Long) {
        viewModelScope.launch {
            _editor.value = ShoppingEditorUiState(loading = true, listId = listId)
            reloadEditor(listId)
        }
    }

    private suspend fun reloadEditor(listId: Long) {
        try {
            syncCurrency()
            val detail = service.detail(listId)
            if (detail == null) {
                _editor.value = ShoppingEditorUiState(loading = false, missing = true, listId = listId)
                return
            }
            _editor.update { current ->
                detail.applyTo(current.copy(loading = false, missing = false, listId = listId))
            }
        } catch (_: Exception) {
            _editor.update { it.copy(loading = false, message = strings.get(R.string.err_load), isError = true) }
        }
    }

    fun onNameChange(value: String) {
        _editor.update { it.copy(nameInput = value, message = null) }
        viewModelScope.launch {
            val found = try {
                service.suggestions(value).map {
                    SuggestionUi(it.name, it.unit, it.lastPriceMinor)
                }
            } catch (_: Exception) {
                emptyList()
            }
            if (_editor.value.nameInput == value) {
                _editor.update { it.copy(suggestions = found) }
            }
        }
    }

    fun onQuantityChange(value: String) {
        _editor.update { it.copy(quantityInput = value, message = null) }
    }

    fun onUnitSelected(unit: QuantityUnit) {
        _editor.update { it.copy(unit = unit, message = null) }
    }

    fun onPriceChange(value: String) {
        _editor.update { it.copy(priceInput = value, message = null) }
    }

    fun onSuggestionSelected(suggestion: SuggestionUi) {
        _editor.update {
            it.copy(
                nameInput = suggestion.name,
                unit = suggestion.unit,
                priceInput = suggestion.lastPriceMinor?.let { price -> Money.formatManat(price) }.orEmpty(),
                suggestions = emptyList(),
                message = null
            )
        }
    }

    fun addItem() {
        viewModelScope.launch {
            val state = _editor.value
            try {
                service.addItem(
                    listId = state.listId,
                    name = state.nameInput,
                    unit = state.unit,
                    quantityThousandths = QuantityFormat.parse(state.quantityInput.ifBlank { "1" }),
                    unitPriceMinor = parsePrice(state.priceInput)
                )
                _editor.update {
                    it.copy(nameInput = "", quantityInput = "1", priceInput = "", suggestions = emptyList())
                }
                reloadEditor(state.listId)
            } catch (_: Exception) {
                _editor.update {
                    it.copy(message = strings.get(R.string.err_check_item), isError = true)
                }
            }
        }
    }

    fun toggleItem(itemId: Long, checked: Boolean) {
        viewModelScope.launch {
            try {
                service.setChecked(
                    itemId,
                    checked,
                    LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)
                )
                reloadEditor(_editor.value.listId)
            } catch (_: Exception) {
                _editor.update { it.copy(message = strings.get(R.string.err_mark), isError = true) }
            }
        }
    }

    fun updateItem(
        itemId: Long,
        name: String,
        quantityInput: String,
        unit: QuantityUnit,
        priceInput: String
    ) {
        viewModelScope.launch {
            val listId = _editor.value.listId
            try {
                val existing = service.detail(listId)?.items?.firstOrNull { it.id == itemId }
                    ?: error("not found")
                service.updateItem(
                    existing.copy(
                        name = name,
                        unit = unit,
                        quantityThousandths = QuantityFormat.parse(quantityInput.ifBlank { "1" }),
                        unitPriceMinor = parsePrice(priceInput)
                    )
                )
                reloadEditor(listId)
            } catch (_: Exception) {
                _editor.update {
                    it.copy(message = strings.get(R.string.err_check_item), isError = true)
                }
            }
        }
    }

    fun deleteItem(itemId: Long) {
        viewModelScope.launch {
            try {
                service.deleteItem(itemId)
                reloadEditor(_editor.value.listId)
            } catch (_: Exception) {
                _editor.update { it.copy(message = strings.get(R.string.err_delete_failed), isError = true) }
            }
        }
    }

    fun saveListInfo(title: String, note: String) {
        viewModelScope.launch {
            val listId = _editor.value.listId
            try {
                service.updateListInfo(listId, title, note)
                reloadEditor(listId)
                refreshHub()
            } catch (_: Exception) {
                _editor.update { it.copy(message = strings.get(R.string.err_save_name), isError = true) }
            }
        }
    }

    fun savePrices() {
        viewModelScope.launch {
            val listId = _editor.value.listId
            try {
                val saved = service.savePrices(listId, LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES))
                val text = if (saved > 0) strings.get(R.string.msg_prices_saved, saved) else strings.get(R.string.msg_no_prices)
                _editor.update { it.copy(message = text, isError = false) }
            } catch (_: Exception) {
                _editor.update { it.copy(message = strings.get(R.string.err_save_prices), isError = true) }
            }
        }
    }

    fun consumeMessage() {
        _editor.update { it.copy(message = null, isError = false) }
    }

    // endregion

    // region close purchase

    fun prepareClose() {
        viewModelScope.launch {
            try {
                val accounts = ledger.accountBalances().map { SelectableOption(it.account.id, it.account.name) }
                val categories = ledger.categories(CategoryKind.EXPENSE)
                    .map { SelectableOption(it.id, it.name) }
                _closeForm.value = CloseFormUiState(
                    accounts = accounts,
                    categories = categories,
                    selectedAccountId = accounts.firstOrNull()?.id,
                    selectedCategoryId = categories.firstOrNull()?.id,
                    dateInput = LocalDate.now().toString(),
                    timeInput = LocalTime.now().truncatedTo(ChronoUnit.MINUTES).format(TIME_FORMAT)
                )
            } catch (_: Exception) {
                _closeForm.value = CloseFormUiState(errorMessage = strings.get(R.string.err_load_accounts))
            }
        }
    }

    fun onCloseAccountSelected(id: Long) {
        _closeForm.update { it.copy(selectedAccountId = id, errorMessage = null) }
    }

    fun onCloseCategorySelected(id: Long) {
        _closeForm.update { it.copy(selectedCategoryId = id, errorMessage = null) }
    }

    fun onCloseDateChange(value: String) {
        _closeForm.update { it.copy(dateInput = value, errorMessage = null) }
    }

    fun onCloseTimeChange(value: String) {
        _closeForm.update { it.copy(timeInput = value, errorMessage = null) }
    }

    fun onCloseCarryOverChange(value: Boolean) {
        _closeForm.update { it.copy(carryOver = value) }
    }

    fun closePurchase(onDone: () -> Unit) {
        viewModelScope.launch {
            val form = _closeForm.value
            val listId = _editor.value.listId
            _closeForm.update { it.copy(submitting = true, errorMessage = null) }
            try {
                val now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)
                val result = service.closePurchase(
                    listId = listId,
                    accountId = form.selectedAccountId ?: error("account"),
                    categoryId = form.selectedCategoryId ?: error("category"),
                    date = LocalDate.parse(form.dateInput.trim()),
                    time = form.timeInput.trim().takeIf { it.isNotEmpty() }
                        ?.let { LocalTime.parse(it, TIME_FORMAT) },
                    now = now,
                    carryOverUnchecked = form.carryOver
                )
                _closeForm.update { it.copy(submitting = false) }
                reloadEditor(listId)
                refreshHub()
                val text = if (result.operationId != null) {
                    strings.get(
                        R.string.msg_purchase_closed,
                        "${Money.formatManat(result.totalMinor)} $currencyCode"
                    )
                } else {
                    strings.get(R.string.msg_purchase_closed_no_expense)
                }
                _editor.update { it.copy(message = text, isError = false) }
                onDone()
            } catch (_: IllegalArgumentException) {
                _closeForm.update {
                    it.copy(submitting = false, errorMessage = strings.get(R.string.err_close_check))
                }
            } catch (_: Exception) {
                _closeForm.update {
                    it.copy(submitting = false, errorMessage = strings.get(R.string.err_close_failed))
                }
            }
        }
    }

    // endregion

    private fun parsePrice(input: String): Long =
        if (input.isBlank()) 0L else Money.parseManat(input)

    private fun ShoppingListSummary.toRow(): ShoppingListRowUi {
        val total = if (list.isClosed) checkedTotalMinor else totalMinor
        val progress = if (list.isClosed) {
            strings.get(R.string.shopping_progress_bought, checkedCount, itemCount)
        } else {
            strings.get(R.string.shopping_progress, checkedCount, itemCount)
        }
        val date = (list.closedAt ?: list.createdAt).format(DISPLAY_DATE)
        return ShoppingListRowUi(
            id = list.id,
            title = list.title,
            subtitle = "$progress · $date",
            totalLabel = "${Money.formatManat(total)} $currencyCode"
        )
    }

    private fun ShoppingListDetail.applyTo(base: ShoppingEditorUiState): ShoppingEditorUiState =
        base.copy(
            title = list.title,
            readOnly = list.isClosed,
            items = items.map { it.toUi() },
            totalLabel = Money.formatManat(totalMinor),
            checkedTotalLabel = Money.formatManat(checkedTotalMinor),
            checkedCount = checkedCount,
            closedLabel = list.closedAt?.format(DISPLAY_DATE),
            hasExpense = list.operationId != null,
            note = list.note,
            operationId = list.operationId
        )

    private fun ShoppingItem.toUi(): ShoppingItemUi {
        val quantity = QuantityFormat.format(quantityThousandths)
        return ShoppingItemUi(
            id = id,
            name = name,
            unit = unit,
            quantityInput = quantity,
            priceInput = if (unitPriceMinor > 0) Money.formatManat(unitPriceMinor) else "",
            quantityLabel = "$quantity ${strings.get(unit.labelRes())}",
            priceLabel = if (unitPriceMinor > 0) {
                "${Money.formatManat(unitPriceMinor)} $currencyCode / ${strings.get(unit.labelRes())}"
            } else {
                strings.get(R.string.shopping_no_price)
            },
            lineTotalLabel = if (unitPriceMinor > 0) "${Money.formatManat(lineTotalMinor)} $currencyCode" else "",
            checked = checked
        )
    }

    companion object {
        fun factory(ledger: Ledger, service: ShoppingService, strings: AppStrings): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ShoppingViewModel(ledger, service, strings) as T
                }
            }
    }
}
