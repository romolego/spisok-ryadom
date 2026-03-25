package com.spisokryadom.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.spisokryadom.app.data.entity.ProductShopLinkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductShopLinkDao {
    @Query("SELECT * FROM product_shop_links WHERE productId = :productId ORDER BY priority ASC")
    fun getLinksForProduct(productId: Long): Flow<List<ProductShopLinkEntity>>

    @Query("SELECT * FROM product_shop_links WHERE productId = :productId ORDER BY priority ASC LIMIT 1")
    suspend fun getPriorityLink(productId: Long): ProductShopLinkEntity?

    @Query("SELECT * FROM product_shop_links WHERE shopId = :shopId ORDER BY priority ASC")
    fun getLinksForShop(shopId: Long): Flow<List<ProductShopLinkEntity>>

    @Query("SELECT * FROM product_shop_links WHERE productId = :productId ORDER BY priority ASC")
    suspend fun getLinksForProductSync(productId: Long): List<ProductShopLinkEntity>

    @Query("SELECT * FROM product_shop_links WHERE id = :id")
    suspend fun getById(id: Long): ProductShopLinkEntity?

    @Query("SELECT * FROM product_shop_links")
    suspend fun getAllSync(): List<ProductShopLinkEntity>

    @Query("DELETE FROM product_shop_links WHERE productId = :productId")
    suspend fun deleteAllForProduct(productId: Long)

    @Query("DELETE FROM product_shop_links")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(link: ProductShopLinkEntity): Long

    @Update
    suspend fun update(link: ProductShopLinkEntity)

    @Delete
    suspend fun delete(link: ProductShopLinkEntity)
}
