package com.spisokryadom.app.data.repository

import com.spisokryadom.app.data.dao.ProductDao
import com.spisokryadom.app.data.dao.ProductShopLinkDao
import com.spisokryadom.app.data.entity.ProductEntity
import com.spisokryadom.app.data.entity.ProductShopLinkEntity
import kotlinx.coroutines.flow.Flow

class ProductRepository(
    private val productDao: ProductDao,
    private val linkDao: ProductShopLinkDao
) {
    fun getAllProducts(): Flow<List<ProductEntity>> = productDao.getAll()
    fun searchProducts(query: String): Flow<List<ProductEntity>> = productDao.searchByName(query)
    fun getDistinctUnits(): Flow<List<String>> = productDao.getDistinctUnits()
    suspend fun getProductById(id: Long): ProductEntity? = productDao.getById(id)
    suspend fun insertProduct(product: ProductEntity): Long = productDao.insert(product)
    suspend fun updateProduct(product: ProductEntity) = productDao.update(product)
    suspend fun deleteProduct(product: ProductEntity) = productDao.delete(product)

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
}
