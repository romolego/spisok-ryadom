package com.spisokryadom.app.data.repository

import com.spisokryadom.app.data.dao.ProductDao
import com.spisokryadom.app.data.dao.ProductShopLinkDao
import com.spisokryadom.app.data.entity.ProductEntity
import com.spisokryadom.app.data.entity.ProductShopLinkEntity
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import com.spisokryadom.app.util.FuzzySearch

class ProductRepository(
    private val productDao: ProductDao,
    private val linkDao: ProductShopLinkDao,
    private val db: com.spisokryadom.app.data.db.AppDatabase
) {
    fun getAllProducts(): Flow<List<ProductEntity>> = productDao.getAll()
    fun getAllProductsIncludingTemporary(): Flow<List<ProductEntity>> = productDao.getAllIncludingTemporary()
    fun searchProducts(query: String): Flow<List<ProductEntity>> = productDao.getAll().map { FuzzySearch.rank(it, query, { p -> p.name }) }.flowOn(Dispatchers.Default)
    fun getDistinctUnits(): Flow<List<String>> = productDao.getDistinctUnits()
    fun getProductsByRecipientId(recipientId: Long): Flow<List<ProductEntity>> =
        productDao.getByRecipientId(recipientId)
    fun getProductsByGroupId(groupId: Long): Flow<List<ProductEntity>> =
        productDao.getByGroupId(groupId)
    fun getProductsByUnit(unit: String): Flow<List<ProductEntity>> = productDao.getByUnit(unit)
    suspend fun renameUnit(oldUnit: String, newUnit: String) = productDao.renameUnit(oldUnit, newUnit)
    suspend fun clearUnit(unit: String) = productDao.clearUnit(unit)
    suspend fun getProductById(id: Long): ProductEntity? = productDao.getById(id)
    suspend fun insertProduct(product: ProductEntity): Long = productDao.insert(product)
    suspend fun updateProduct(product: ProductEntity) = productDao.update(product)
    suspend fun deleteProduct(product: ProductEntity) = productDao.delete(product)

    fun getAllShopLinks(): Flow<List<ProductShopLinkEntity>> = linkDao.getAll()

    fun getShopLinksForProduct(productId: Long): Flow<List<ProductShopLinkEntity>> =
        linkDao.getLinksForProduct(productId)

    fun getShopLinksForShop(shopId: Long): Flow<List<ProductShopLinkEntity>> =
        linkDao.getLinksForShop(shopId)

    suspend fun getShopLinksForProductSync(productId: Long): List<ProductShopLinkEntity> =
        linkDao.getLinksForProductSync(productId)

    suspend fun getPriorityLink(productId: Long): ProductShopLinkEntity? =
        linkDao.getPriorityLink(productId)

    suspend fun insertShopLink(link: ProductShopLinkEntity): Long = linkDao.insert(link)
    suspend fun updateShopLink(link: ProductShopLinkEntity) = linkDao.update(link)
    suspend fun deleteShopLink(link: ProductShopLinkEntity) = linkDao.delete(link)
    suspend fun deleteAllShopLinks(productId: Long) = linkDao.deleteAllForProduct(productId)
    suspend fun toggleShopLink(productId:Long,shopId:Long)=db.withTransaction {
        val links=linkDao.getLinksForProductSync(productId)
        val old=links.find{it.shopId==shopId}
        if(old!=null)linkDao.delete(old) else {
            require(db.productDao().getById(productId)!=null&&db.shopDao().getById(shopId)!=null)
            linkDao.insert(ProductShopLinkEntity(productId=productId,shopId=shopId,priority=(links.maxOfOrNull{it.priority}?:0)+1))
        }
    }

    suspend fun saveWithLinks(product: ProductEntity, links: List<ProductShopLinkEntity>): Long = db.withTransaction {
        require(product.name.isNotBlank()) { "Введите название товара" }
        product.defaultQuantity?.let { com.spisokryadom.app.util.Quantities.valid(it) }
        product.priceValue?.let { require(it.isFinite() && it>=0) { "Проверьте цену" } }
        val id=if(product.id==0L)productDao.insert(product) else { require(productDao.getById(product.id)!=null) { "Товар удалён" };productDao.update(product);product.id }
        require(links.map{it.shopId}.distinct().size==links.size) { "Магазин добавлен дважды" }
        links.forEach { link ->
            require(db.shopDao().getById(link.shopId)!=null) { "Магазин удалён" }
            if(link.departmentId!=null)require(db.shopDepartmentDao().getById(link.departmentId)?.shopId==link.shopId) { "Группа относится к другому магазину" }
        }
        linkDao.deleteAllForProduct(id)
        links.sortedBy{it.priority}.forEachIndexed { i,link ->linkDao.insert(link.copy(id=0,productId=id,priority=i+1)) }
        id
    }
}
