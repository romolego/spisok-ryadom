package com.spisokryadom.app.data.dao
import androidx.room.*
import com.spisokryadom.app.data.entity.*
import kotlinx.coroutines.flow.Flow
@Dao abstract class ShopBranchDao {
 @Query("SELECT * FROM shop_branches ORDER BY displayOrder,id")abstract fun observeAll():Flow<List<ShopBranchEntity>>
 @Query("SELECT * FROM shop_branches ORDER BY displayOrder,id")abstract suspend fun getAll():List<ShopBranchEntity>
 @Query("SELECT * FROM shop_branches WHERE shopId=:shopId ORDER BY displayOrder,id")abstract suspend fun forShop(shopId:Long):List<ShopBranchEntity>
 @Query("SELECT * FROM shop_branches WHERE id=:id")abstract suspend fun get(id:Long):ShopBranchEntity?
 @Insert(onConflict=OnConflictStrategy.ABORT)abstract suspend fun insert(branch:ShopBranchEntity):Long
 @Update abstract suspend fun update(branch:ShopBranchEntity)
 @Query("DELETE FROM shop_branches WHERE id=:id")abstract suspend fun delete(id:Long)
 @Query("SELECT * FROM product_branch_availability")abstract fun observeAvailability():Flow<List<ProductBranchAvailabilityEntity>>
 @Query("SELECT * FROM product_branch_availability")abstract suspend fun allAvailability():List<ProductBranchAvailabilityEntity>
 @Query("SELECT * FROM product_branch_availability WHERE productId=:id")abstract suspend fun availability(id:Long):List<ProductBranchAvailabilityEntity>
 @Insert(onConflict=OnConflictStrategy.ABORT)abstract suspend fun insertAvailabilityRow(value:ProductBranchAvailabilityEntity)
 @Query("DELETE FROM product_branch_availability WHERE productId=:id")abstract suspend fun clearAvailability(id:Long)
 @Query("SELECT * FROM product_branch_priorities ORDER BY displayOrder,branchId")abstract suspend fun priorities():List<ProductBranchPriorityEntity>
 @Query("SELECT * FROM product_branch_priorities ORDER BY displayOrder,branchId")abstract fun observePriorities():Flow<List<ProductBranchPriorityEntity>>
 @Insert(onConflict=OnConflictStrategy.REPLACE)abstract suspend fun setPriority(value:ProductBranchPriorityEntity)
 @Query("DELETE FROM product_branch_priorities WHERE productId=:productId")abstract suspend fun clearPriorities(productId:Long)
 @Query("DELETE FROM product_branch_priorities WHERE productId=:productId AND shopId=:shopId")abstract suspend fun clearShopPriorities(productId:Long,shopId:Long)
 @Transaction open suspend fun insertAvailability(value:ProductBranchAvailabilityEntity) {
  val branch=get(value.branchId)?:error("Точка удалена")
  insertAvailabilityRow(value.copy(shopId=branch.shopId))
 }
}

