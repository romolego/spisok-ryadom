package com.spisokryadom.app.ui.productcard

import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject

/** Rebase external raw-draft changes onto newer local input, preserving unrelated edits. */
internal fun ProductEditDraft.rebaseExternal(base: ProductEditDraft?, remote: ProductEditDraft?): ProductEditDraft {
    if (base == null || remote == null || base.stableId != stableId || remote.stableId != stableId) return this
    val merged = mergeDraftObject(JSONObject(encode()), JSONObject(base.encode()), JSONObject(remote.encode()), "")
    return ProductEditDraft.decode(merged.toString()).copy(
        stableId = stableId, productId = productId, newSession = newSession, allowDuplicate = allowDuplicate
    )
}

private fun sameDraftValue(a: Any?, b: Any?): Boolean = when {
    a is JSONObject && b is JSONObject -> a.keys().asSequence().toSet() == b.keys().asSequence().toSet() && a.keys().asSequence().all { sameDraftValue(a.opt(it), b.opt(it)) }
    a is JSONArray && b is JSONArray -> a.length() == b.length() && (0 until a.length()).all { sameDraftValue(a.opt(it), b.opt(it)) }
    else -> a == b
}

private fun mergeDraftObject(local: JSONObject, base: JSONObject, remote: JSONObject, path: String): JSONObject {
    (base.keys().asSequence().toSet() + remote.keys().asSequence().toSet()).forEach { key ->
        if (!remote.has(key)) local.remove(key)
        else if (!base.has(key)) local.put(key, remote.get(key))
        // Removing this key locally means "all points". An external expansion of
        // the older subset already fits that choice and must not restrict it again.
        else if (path == "points" && !local.has(key)) Unit
        else if (!sameDraftValue(base.get(key), remote.get(key))) {
            val here = if (path.isBlank()) key else "$path/$key"
            local.put(key, mergeDraftValue(local.opt(key), base.get(key), remote.get(key), here))
        }
    }
    return local
}

private fun mergeDraftValue(local: Any?, base: Any?, remote: Any?, path: String): Any? = when {
    local is JSONObject && base is JSONObject && remote is JSONObject -> mergeDraftObject(local, base, remote, path)
    local is JSONArray && base is JSONArray && remote is JSONArray && path in setOf("links", "pendingLinks", "departments") -> {
        val key = if (path == "departments") "id" else "shopId"
        fun indexed(array: JSONArray) = (0 until array.length()).map { array.getJSONObject(it) }.associateBy { it.getLong(key) }
        val previous = indexed(base)
        val current = indexed(remote)
        val present = mutableSetOf<Long>()
        JSONArray().also { result ->
            (0 until local.length()).forEach { index ->
                val value = local.getJSONObject(index)
                val id = value.getLong(key)
                if (!(id in previous && id !in current)) {
                    present.add(id)
                    val before = previous[id]
                    val after = current[id]
                    result.put(if (before != null && after != null) mergeDraftObject(value, before, after, "$path/$id") else after?.takeIf { before == null } ?: value)
                }
            }
            current.forEach { (id, value) -> if (id !in present && id !in previous) result.put(value) }
        }
    }
    local is JSONArray && base is JSONArray && remote is JSONArray && path.startsWith("points/") -> {
        fun ids(array: JSONArray) = (0 until array.length()).map { array.getLong(it) }.toSet()
        val before = ids(base)
        val after = ids(remote)
        JSONArray(((ids(local) - (before - after)) + (after - before)).sorted())
    }
    else -> remote
}

/** Keep live loading/search/error/save state while applying only serialized editable values. */
internal fun ProductEditDraft.editableOn(current: ProductCardUiState): ProductCardUiState = current.copy(
    name = state.name, productGroupId = state.productGroupId, recipientId = state.recipientId,
    defaultUnit = state.defaultUnit, defaultQuantity = state.defaultQuantity, note = state.note,
    purchaseType = state.purchaseType, sellerUrl = state.sellerUrl, productUrl = state.productUrl,
    photoUri = state.photoUri, priceValue = state.priceValue, priceDate = state.priceDate,
    isTemporary = state.isTemporary, isFavorite = state.isFavorite, accent = state.accent,
    preferCityShopOrder = state.preferCityShopOrder, pendingShopLinks = state.pendingShopLinks,
    shopLinks = state.shopLinks, pendingDepartments = state.pendingDepartments,
    branchSelections = state.branchSelections, branchDepartments = state.branchDepartments,
    branchPriorities = state.branchPriorities
)

/** A cleared ViewModel still completes its last raw write under the shared editor lock. */
internal object ActiveProductDraftWrites {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    fun complete(block: suspend () -> Unit) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                com.spisokryadom.app.data.repository.ProductEditorTransactions.locked {
                    withContext(NonCancellable) { block() }
                }
            } catch (error: Exception) {
                android.util.Log.w("ProductDraft", "Last raw draft write did not finish", error)
            }
        }
    }
}
