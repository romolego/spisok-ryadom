package com.spisokryadom.app.ui.goallists

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.spisokryadom.app.SpisokRyadomApp
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.GoalListRepository
import com.spisokryadom.app.data.repository.GoalItemField
import com.spisokryadom.app.data.repository.ProductRepository
import com.spisokryadom.app.data.repository.ShopRepository
import com.spisokryadom.app.data.repository.ProductEditorTransactions
import com.spisokryadom.app.domain.*
import com.spisokryadom.app.util.FuzzySearch
import com.spisokryadom.app.util.Quantities
import com.spisokryadom.app.util.PurchaseMoney
import com.spisokryadom.app.util.DatabaseRevision
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.text.Collator
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

internal fun goalNumber(value: Double): String = DecimalFormat("0.######", DecimalFormatSymbols(Locale.forLanguageTag("ru"))).format(value)
internal fun goalMoney(value: Double): String = PurchaseMoney.format(value)

data class GoalListsUiState(val itemCounts:Map<Long,Int> = emptyMap(),val lists: List<GoalListEntity> = emptyList(), val loading: Boolean = true, val busy: Boolean = false, val error: String? = null, val createdId: Long? = null)

class GoalListsViewModel(private val repository: GoalListRepository, private val sessionGeneration: Long = ProductEditorTransactions.generation.get()) : ViewModel() {
    private val _state = MutableStateFlow(GoalListsUiState())
    val uiState = _state.asStateFlow()
    init { viewModelScope.launch { combine(repository.observeLists(),repository.observeAllEntries()){lists,rows->lists to rows.mapNotNull{it.listId}.groupingBy{it}.eachCount()}.collect { (lists,counts) -> _state.update { it.copy(lists = lists,itemCounts=counts, loading = false) } } } }
    fun create(name: String) {
        if (_state.value.busy || name.isBlank()) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try { repository.withSession(sessionGeneration) { val id = repository.create(name); _state.update { it.copy(createdId = id) } } }
            catch (e: Exception) { if (e is CancellationException) throw e; _state.update { it.copy(error = e.message ?: "Не удалось создать список") } }
            finally { _state.update { it.copy(busy = false) } }
        }
    }
    fun clearNavigation() { _state.update { it.copy(createdId = null) } }
    fun captureHome(name:String){
        if(_state.value.busy)return
        _state.update{it.copy(busy=true,error=null)}
        viewModelScope.launch{try{repository.withSession(sessionGeneration){val id=repository.captureList(null,name);_state.update{it.copy(createdId=id)}}}
        catch(e:Exception){if(e is CancellationException)throw e;_state.update{it.copy(error=e.message)}}finally{_state.update{it.copy(busy=false)}}}
    }
    fun clearError() { _state.update { it.copy(error = null) } }
    companion object { val Factory = viewModelFactory { initializer { GoalListsViewModel((this[APPLICATION_KEY] as SpisokRyadomApp).container.goalListRepository) } } }
}

data class GoalPurchaseRow(val entry: ShoppingListEntryEntity, val product: ProductEntity, val place: PlaceLabel?) {
    val total: Double? get() = entry.plannedUnitPrice?.let { PurchaseMoney.total(it, entry.quantity) }
}

data class GoalItemEditor(
    val session: String = java.util.UUID.randomUUID().toString(), val entryId: Long? = null,
    val name: String = "", val quantity: String = "1", val unit: String = "", val price: String = "",
    val shop: String = "", val address: String = "", val note: String = "", val comment: String = "", val editableName: Boolean = true,
    val dirty: Boolean = false, val saving: Boolean = false, val error: String? = null,
    val editedFields: Set<GoalItemField> = emptySet()
)

data class GoalListUiState(
    val list: GoalListEntity? = null, val rows: List<GoalPurchaseRow> = emptyList(),
    val shops: List<String> = emptyList(), val loading: Boolean = true, val busy: Boolean = false,
    val editor: GoalItemEditor? = null, val error: String? = null, val message: String? = null, val deleted: Boolean = false,
    val shareText: String? = null, val selectionMode: Boolean = false, val selectedIds: Set<Long> = emptySet(), val allLists: List<GoalListEntity> = emptyList(), val openListId:Long?=null
) {
    private fun total(values: List<GoalPurchaseRow>): Double = values.fold(java.math.BigDecimal.ZERO) { sum, row ->
        sum + (row.entry.plannedUnitPrice?.let { PurchaseMoney.amount(it, row.entry.quantity) } ?: java.math.BigDecimal.ZERO)
    }.toDouble()
    val plannedTotal: Double get() = total(rows)
    val boughtTotal: Double get() = total(rows.filter { it.entry.isBought })
    val remainingTotal: Double get() = total(rows.filter { !it.entry.isBought })
    val unpricedCount: Int get() = rows.count { it.entry.plannedUnitPrice == null }
}

class GoalListViewModel(
    private val listId: Long, private val repository: GoalListRepository, private val products: ProductRepository,
    private val shops: ShopRepository, private val savedState: SavedStateHandle = SavedStateHandle(), private val cacheDirectory: java.io.File? = null,
    currentRevision: String? = null, private val sessionGeneration: Long = ProductEditorTransactions.generation.get()
) : ViewModel() {
    private val messages = GoalMessageDrafts(savedState, cacheDirectory)
    init { prepareGoalSavedState(savedState, messages, currentRevision) }
    private val _state = MutableStateFlow(GoalListUiState(editor = restoreEditor(), shareText = messages.read("goal_share_text")))
    val uiState = _state.asStateFlow()
    private val editorMutex = Mutex()
    private var editorJob: Job? = null
    private var pendingFileJson: String? = messages.read("goal_export_json")
    private data class Places(val shops: List<ShopEntity>, val branches: List<ShopBranchEntity>, val venues: List<VenueEntity>)
    init {
        val places = combine(shops.getAllShops(), shops.observeBranches(), shops.venues()) { s, b, v -> Places(s, b, v) }
        viewModelScope.launch {
            combine(repository.observeLists(), repository.observeEntries(listId), products.getAllProductsIncludingTemporary(), places) { lists, entries, ps, places ->
                val byProduct = ps.associateBy { it.id }; val byShop = places.shops.associateBy { it.id }; val byBranch = places.branches.associateBy { it.id }
                val labels = PlaceLabels(places.branches, places.venues)
                val rows = entries.mapNotNull { e -> byProduct[e.productId]?.let { p ->
                    val place = e.assignedShopId?.let { byShop[it] }?.let { s -> labels.place(s.name, e.assignedBranchId?.let { byBranch[it] }) }
                        ?: if (e.customShopName.isNullOrBlank() && e.customShopAddress.isNullOrBlank()) null
                        else physicalPlaceLabel(e.customShopName?.takeIf { it.isNotBlank() } ?: e.customShopAddress.orEmpty(), e.customShopAddress?.takeIf { !e.customShopName.isNullOrBlank() }, null)
                    GoalPurchaseRow(e, p, place)
                } }
                Triple(Triple(lists.find { it.id == listId }, rows, ps.filter { !it.isTemporary }), places.shops.map { labels.shop(it.id, it.name) }.distinct(), lists)
            }.flowOn(Dispatchers.Default).collect { (data, names, lists) ->
                _state.update { it.copy(list = data.first, rows = data.second, loading = false, shops = names, allLists = lists, selectedIds = it.selectedIds.intersect(data.second.map { row -> row.entry.id }.toSet())) }
            }
        }
        if (_state.value.editor?.dirty == true) scheduleSave()
    }
    fun clearError() { _state.update { it.copy(error = null) } }
    fun clearMessage() { _state.update { it.copy(message = null) } }
    fun selection(enabled: Boolean) { _state.update { it.copy(selectionMode = enabled, selectedIds = emptySet()) } }
    fun select(row: GoalPurchaseRow) {
        if (_state.value.rows.none { it.entry.id == row.entry.id }) return
        _state.update { state -> state.copy(selectedIds = if (row.entry.id in state.selectedIds) state.selectedIds - row.entry.id else state.selectedIds + row.entry.id) }
    }
    fun selectAll() { _state.update { state -> val ids = state.rows.map { it.entry.id }.toSet(); state.copy(selectedIds = if (state.selectedIds == ids) emptySet() else ids) } }
    fun batchBought(bought: Boolean) {
        val ids = _state.value.selectedIds.toSet(); if (ids.isEmpty()) return
        command { repository.batch(listId, ids, bought = bought); selection(false) }
    }
    fun batchDelete() {
        val ids = _state.value.selectedIds.toSet(); if (ids.isEmpty()) return
        command { repository.batch(listId, ids, delete = true); selection(false) }
    }
    fun moveSelected(targetListId: Long?) {
        val ids = _state.value.selectedIds.toSet(); if (ids.isEmpty()) return
        command { repository.moveEntriesFrom(listId, ids, targetListId); selection(false) }
    }
    private fun command(block: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try { repository.withSession(sessionGeneration) { block() } }
            catch (e: Exception) { if (e is CancellationException) throw e; _state.update { it.copy(error = e.message ?: "Не удалось изменить список") } }
            finally { _state.update { it.copy(busy = false) } }
        }
    }
    fun metadata(name: String, visible: Boolean) = command { repository.updateList(listId, name, visible) }
    fun captureList(name:String)=command{val id=repository.captureList(listId,name);_state.update{it.copy(openListId=id)}}
    fun clearNavigation(){_state.update{it.copy(openListId=null)}}
    fun deleteList() = command { repository.deleteList(listId); _state.update { it.copy(deleted = true) } }
    fun bought(row: GoalPurchaseRow, value: Boolean) = command { repository.setBought(row.entry.id, value, expectedListId = listId) }
    fun urgent(row: GoalPurchaseRow) = command { repository.toggleUrgent(row.entry.id, expectedListId = listId) }
    fun remove(row: GoalPurchaseRow) = command { repository.removeItem(row.entry.id, expectedListId = listId) }
    fun quantity(row: GoalPurchaseRow, delta: Double) {
        if (!delta.isFinite()) return
        command { repository.adjustQuantity(row.entry.id, delta, expectedListId = listId) }
    }
    fun newItem() { editorJob?.cancel(); setEditor(GoalItemEditor()) }
    fun edit(row: GoalPurchaseRow) {
        editorJob?.cancel()
        setEditor(GoalItemEditor(entryId = row.entry.id, name = row.product.name, quantity = goalNumber(row.entry.quantity), unit = row.entry.unit,
            price = row.entry.plannedUnitPrice?.let { java.math.BigDecimal.valueOf(it).stripTrailingZeros().toPlainString().replace('.', ',') }.orEmpty(),
            shop = if (row.entry.assignedShopId == null) row.entry.customShopName.orEmpty() else row.place?.title.orEmpty(),
            address = if (row.entry.assignedShopId == null) row.entry.customShopAddress.orEmpty() else row.place?.subtitle.orEmpty(),
            note = row.entry.note.orEmpty(), comment = row.entry.comment.orEmpty(), editableName = row.product.isTemporary))
    }
    fun editDraft(transform: (GoalItemEditor) -> GoalItemEditor) {
        val before = _state.value.editor ?: return
        val after = transform(before)
        val changed = GoalItemField.entries.filter { editorFieldValue(before, it) != editorFieldValue(after, it) }.toSet()
        setEditor(after.copy(dirty = true, error = null, editedFields = before.editedFields + changed)); scheduleSave()
    }
    private fun scheduleSave() { editorJob?.cancel(); editorJob = viewModelScope.launch { delay(450); saveEditor(false) } }
    fun finishEditor() { editorJob?.cancel(); editorJob = null; viewModelScope.launch { saveEditor(true) } }
    /** Complete the last valid edit when the app goes to the background. */
    fun flushEditor() { editorJob?.cancel(); editorJob = null; viewModelScope.launch { saveEditor(false) } }
    override fun onCleared() {
        editorJob?.cancel()
        // A widget deep link can remove this destination before the debounce expires.
        // The short final write must outlive viewModelScope, still sharing its mutex
        // and database generation check with every normal autosave.
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            try { saveEditor(false) } finally { cancel() }
        }
        super.onCleared()
    }
    private suspend fun saveEditor(close: Boolean) = editorMutex.withLock {
        var editor = _state.value.editor ?: return@withLock
        if (close && editor.entryId != null) {
            val session = editor.session
            val lost = editorOwnerLost(editor.entryId!!)
            val latest = _state.value.editor ?: return@withLock
            if (latest.session != session) return@withLock
            if (lost) { closeStaleEditor(); return@withLock }
            // Ownership lookup suspends; do not replace text typed while it ran.
            editor = latest
        }
        val emptyNewDraft = editor.entryId == null && editor.name.isBlank() && editor.quantity == "1" &&
            editor.unit.isBlank() && editor.price.isBlank() && editor.shop.isBlank() && editor.address.isBlank() && editor.note.isBlank() && editor.comment.isBlank()
        if (emptyNewDraft) { if (close) setEditor(null); return@withLock }
        val qty = Quantities.parse(editor.quantity)
        val price = editor.price.trim().takeIf { it.isNotEmpty() }?.replace(',', '.')?.toDoubleOrNull()
        val error = when {
            editor.name.isBlank() -> "Укажите название"
            qty == null -> "Введите положительное количество"
            editor.price.isNotBlank() && (price == null || !price.isFinite() || price < 0 || price > 1_000_000_000_000.0) -> "Введите корректную цену за единицу"
            else -> null
        }
        if (error != null) { setEditor(editor.copy(error = if (close) error else null)); return@withLock }
        if (!editor.dirty) { if (close) setEditor(null); return@withLock }
        setEditor(editor.copy(saving = true))
        try {
            // An in-flight Room write must finish before another draft creates a row.
            withContext(NonCancellable) {
              repository.withSession(sessionGeneration) {
                val id = editor.entryId?.also { repository.updateItem(it, editor.name.takeIf { editor.editableName }, qty!!, editor.unit, price,
                    editor.note, editor.shop, editor.address, expectedListId = listId, comment = editor.comment, editedFields = editor.editedFields) }
                    ?: repository.addQuick(listId, editor.name, qty!!, editor.unit, price, editor.shop, editor.address, editor.note, draftStableId = editor.session, comment = editor.comment)
                val current = _state.value.editor
                if (current?.session == editor.session) {
                    val unchanged = current.copy(saving = false) == editor.copy(saving = false)
                    val committed = editor.editedFields.filter { editorFieldValue(current, it) == editorFieldValue(editor, it) }.toSet()
                    setEditor(if (close && unchanged) null else current.copy(entryId = id, saving = false, dirty = !unchanged, error = null,
                        editedFields = current.editedFields - committed))
                    if (!unchanged) scheduleSave()
                }
              }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            val stale = editor.entryId?.let { editorOwnerLost(it) } ?: false
            _state.value.editor?.takeIf { it.session == editor.session }?.let {
                if (stale) {
                    closeStaleEditor()
                } else setEditor(it.copy(saving = false, error = e.message ?: "Не удалось сохранить позицию"))
            }
        }
    }
    private suspend fun editorOwnerLost(id: Long): Boolean {
        if (ProductEditorTransactions.generation.get() != sessionGeneration) return true
        return runCatching { repository.withSession(sessionGeneration) {
            repository.getList(listId) == null || repository.snapshot(listId).entries.none { it.id == id }
        } }.getOrDefault(false)
    }
    private fun closeStaleEditor() {
        setEditor(null)
        _state.update { it.copy(error = "Покупка удалена или перемещена. Откройте её из текущего списка") }
    }
    private fun editorFieldValue(editor: GoalItemEditor, field: GoalItemField): Any = when (field) {
        GoalItemField.NAME -> editor.name
        GoalItemField.QUANTITY -> editor.quantity
        GoalItemField.UNIT -> editor.unit
        GoalItemField.PRICE -> editor.price
        GoalItemField.NOTE -> editor.note
        GoalItemField.COMMENT -> editor.comment
        GoalItemField.PLACE -> editor.shop to editor.address
    }
    private fun setEditor(editor: GoalItemEditor?) {
        _state.update { it.copy(editor = editor) }
        savedState["goal_editor_open"] = editor != null
        editor?.let { e ->
            savedState["goal_editor_session"] = e.session; savedState["goal_editor_id"] = e.entryId
            savedState["goal_editor_name"] = e.name; savedState["goal_editor_quantity"] = e.quantity; savedState["goal_editor_unit"] = e.unit
            savedState["goal_editor_price"] = e.price; savedState["goal_editor_shop"] = e.shop; savedState["goal_editor_address"] = e.address
            savedState["goal_editor_note"] = e.note; savedState["goal_editor_comment"] = e.comment; savedState["goal_editor_editable"] = e.editableName; savedState["goal_editor_dirty"] = e.dirty
            savedState["goal_editor_fields"] = ArrayList(e.editedFields.map { it.name })
        }
    }
    private fun restoreEditor(): GoalItemEditor? = if (savedState.get<Boolean>("goal_editor_open") != true) null else GoalItemEditor(
        session = savedState["goal_editor_session"] ?: java.util.UUID.randomUUID().toString(), entryId = savedState["goal_editor_id"],
        name = savedState["goal_editor_name"] ?: "", quantity = savedState["goal_editor_quantity"] ?: "1", unit = savedState["goal_editor_unit"] ?: "",
        price = savedState["goal_editor_price"] ?: "", shop = savedState["goal_editor_shop"] ?: "", address = savedState["goal_editor_address"] ?: "",
        note = savedState["goal_editor_note"] ?: "", comment = savedState["goal_editor_comment"] ?: "", editableName = savedState["goal_editor_editable"] ?: true, dirty = savedState["goal_editor_dirty"] ?: false,
        editedFields = savedState.get<ArrayList<String>>("goal_editor_fields")?.mapNotNull { runCatching { GoalItemField.valueOf(it) }.getOrNull() }?.toSet()
            ?: if (savedState.get<Boolean>("goal_editor_dirty") == true) GoalItemField.entries.toSet() else emptySet())
    fun export(done: (GoalListDocument) -> Unit) = command { done(repository.exportDocument(listId)) }
    fun openShare() = command { shareText(GoalListTransfer.exportText(repository.exportDocument(listId))) }
    fun shareText(text: String?) {
        try { messages.write("goal_share_text", text); _state.update { it.copy(shareText = text) } }
        catch (e: Exception) { _state.update { it.copy(error = e.message ?: "Не удалось сохранить сообщение") } }
    }
    fun prepareFile(done: (String) -> Unit) = command {
        val document = repository.exportDocument(listId)
        val json = withContext(Dispatchers.Default) { GoalListTransfer.exportJson(document) }
        messages.write("goal_export_json", json)
        pendingFileJson = json
        done(document.name.replace(Regex("[\\p{Cntrl}\\\\/:*?\"<>|]"), "_").take(80).ifBlank { "Список" } + ".json")
    }
    fun writeFile(resolver: ContentResolver, uri: Uri) = command {
        val text = pendingFileJson ?: error("Откройте экспорт списка ещё раз")
        withContext(Dispatchers.IO) { resolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: error("Не удалось сохранить файл") }
        pendingFileJson = null
        runCatching { messages.write("goal_export_json", null) }
        _state.update { it.copy(message = "Файл списка сохранён") }
    }
    fun cancelFileExport() { pendingFileJson = null; runCatching { messages.write("goal_export_json", null) } }
    companion object { fun factory(listId: Long) = viewModelFactory { initializer {
        val app = this[APPLICATION_KEY] as SpisokRyadomApp
        GoalListViewModel(listId, app.container.goalListRepository, app.container.productRepository, app.container.shopRepository, createSavedStateHandle(), GoalMessageDrafts.directory(app), DatabaseRevision.current(app))
    } } }
}

data class GoalImportUiState(val text: String = "", val preview: GoalListTransferPreview? = null, val busy: Boolean = false, val error: String? = null, val importedId: Long? = null)

class GoalImportViewModel(private val repository: GoalListRepository, private val savedState: SavedStateHandle = SavedStateHandle(), private val cacheDirectory: java.io.File? = null,
    currentRevision: String? = null, private val sessionGeneration: Long = ProductEditorTransactions.generation.get()) : ViewModel() {
    private val messages = GoalMessageDrafts(savedState, cacheDirectory)
    init { prepareGoalSavedState(savedState, messages, currentRevision) }
    private var importSession: String = savedState.get<String>("goal_import_session") ?: java.util.UUID.randomUUID().toString().also { savedState["goal_import_session"] = it }
    private val _state = MutableStateFlow(GoalImportUiState(text = messages.read("goal_import_text") ?: "", importedId = savedState["goal_import_completed_id"]))
    val uiState = _state.asStateFlow()
    fun text(value: String) {
        if (_state.value.busy) return
        if (value.toByteArray(Charsets.UTF_8).size > GoalListTransfer.MAX_INPUT_BYTES) { _state.update { it.copy(error = "Список больше 1 МБ") }; return }
        try { messages.write("goal_import_text", value); _state.update { it.copy(text = value, preview = null, error = null) } }
        catch (e: Exception) { _state.update { it.copy(error = e.message ?: "Не удалось сохранить сообщение") } }
    }
    fun preview() {
        if (_state.value.busy || _state.value.text.isBlank()) return
        val text = _state.value.text; _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try { val preview = withContext(Dispatchers.Default) { GoalListTransfer.parse(text) }; _state.update { it.copy(preview = preview) } }
            catch (e: Exception) { if (e is CancellationException) throw e; _state.update { it.copy(error = e.message ?: "Не удалось прочитать список") } }
            finally { _state.update { it.copy(busy = false) } }
        }
    }
    fun file(resolver: ContentResolver, uri: Uri) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null, preview = null) }
        viewModelScope.launch {
            try {
                val content = withContext(Dispatchers.IO) {
                    resolver.openInputStream(uri)?.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (output.size() <= GoalListTransfer.MAX_INPUT_BYTES) {
                            val count = input.read(buffer, 0, minOf(buffer.size, GoalListTransfer.MAX_INPUT_BYTES + 1 - output.size()))
                            if (count < 0) break
                            if (count == 0) continue
                            output.write(buffer, 0, count)
                        }
                        val bytes = output.toByteArray()
                        require(bytes.size <= GoalListTransfer.MAX_INPUT_BYTES) { "Список больше 1 МБ" }
                        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
                    } ?: error("Не удалось открыть файл")
                }
                messages.write("goal_import_text", content)
                val preview = withContext(Dispatchers.Default) { GoalListTransfer.parse(content) }
                _state.update { it.copy(text = content, preview = preview) }
            } catch (e: Exception) { if (e is CancellationException) throw e; _state.update { it.copy(error = if (e is java.nio.charset.CharacterCodingException) "Файл должен быть в кодировке UTF-8" else e.message ?: "Не удалось прочитать UTF-8 файл") } }
            finally { _state.update { it.copy(busy = false) } }
        }
    }
    fun confirm() {
        val preview = _state.value.preview ?: return
        if (_state.value.busy || _state.value.importedId != null || !preview.canImport) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try { withContext(NonCancellable) { repository.withSession(sessionGeneration) { val id = repository.importDocument(preview.document!!, importStableId = importSession); savedState["goal_import_completed_id"] = id; _state.update { it.copy(importedId = id, preview = null) }; runCatching { messages.write("goal_import_text", null) } } } }
            catch (e: Exception) { if (e is CancellationException) throw e; _state.update { it.copy(error = e.message ?: "Не удалось импортировать список") } }
            finally { _state.update { it.copy(busy = false) } }
        }
    }
    fun discard() { if (!_state.value.busy) { runCatching { messages.write("goal_import_text", null) }; savedState.remove<Long>("goal_import_completed_id"); importSession = java.util.UUID.randomUUID().toString(); savedState["goal_import_session"] = importSession; _state.value = GoalImportUiState() } }
    companion object { val Factory = viewModelFactory { initializer { val app = this[APPLICATION_KEY] as SpisokRyadomApp; GoalImportViewModel(app.container.goalListRepository, createSavedStateHandle(), GoalMessageDrafts.directory(app), DatabaseRevision.current(app)) } } }
}

/** A restored database may reuse local IDs; old drafts must never target the replacement. */
private fun prepareGoalSavedState(state: SavedStateHandle, messages: GoalMessageDrafts, revision: String?) {
    if (revision == null) return
    val previous = state.get<String>("goal_database_revision")
    if (previous != revision) {
        runCatching { messages.write("goal_share_text", null) }; runCatching { messages.write("goal_import_text", null) }
        runCatching { messages.write("goal_export_json", null) }
        state.keys().filter { it.startsWith("goal_editor_") }.forEach { state.remove<Any?>(it) }
        state.remove<String>("goal_import_session"); state.remove<Long>("goal_import_completed_id")
    }
    state["goal_database_revision"] = revision
}
