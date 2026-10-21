package com.spisokryadom.app.data.repository

import com.spisokryadom.app.data.db.AppDatabase

/** Called inside the catalogue transaction. Keep placement IDs and product links stable.
 * Existing network-only groups are retained; only explicitly removed types are removed.
 * A network is independent of the venues of its physical points.
 */
internal suspend fun synchronizeNetworkDepartments(db: AppDatabase, sourceId: Long, removedTypes: Set<Long> = emptySet()) {
    val source = db.shopDao().getById(sourceId) ?: return
    val network = source.networkId ?: return
    val members = db.shopDao().getAllSync().filter { it.networkId == network }
        .sortedWith(compareBy<com.spisokryadom.app.data.entity.ShopEntity> { it.id != sourceId }.thenBy { it.id })
    val dao = db.shopDepartmentDao()
    val groups = members.flatMap { dao.getByShopIdSync(it.id).sortedWith(compareBy({ it.displayOrder }, { it.id })) }
        .filter { it.typeId !in removedTypes }.distinctBy { it.typeId }
    members.forEach { shop ->
        val old = dao.getByShopIdSync(shop.id).associateBy { it.typeId }
        old.values.filter { it.typeId in removedTypes }.forEach { dao.delete(it) }
        groups.forEachIndexed { index, group ->
            val value = group.copy(id = old[group.typeId]?.id ?: 0, shopId = shop.id, displayOrder = index + 1)
            if (value.id == 0L) dao.insert(value) else dao.update(value)
        }
    }
}
