package com.spisokryadom.app.data.dao
import androidx.room.*
import com.spisokryadom.app.data.entity.*
import kotlinx.coroutines.flow.Flow

@Dao interface RetailStructureDao {
 @Query("SELECT * FROM retail_networks ORDER BY name,id") fun observeNetworks():Flow<List<RetailNetworkEntity>>
 @Query("SELECT * FROM retail_networks ORDER BY name,id") suspend fun networks():List<RetailNetworkEntity>
 @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insertNetwork(value:RetailNetworkEntity):Long
 @Update suspend fun updateNetwork(value:RetailNetworkEntity)
 @Query("DELETE FROM retail_networks") suspend fun clearNetworks()
 @Query("SELECT * FROM venues ORDER BY name,id") fun observeVenues():Flow<List<VenueEntity>>
 @Query("SELECT * FROM venues ORDER BY name,id") suspend fun venues():List<VenueEntity>
 @Query("SELECT * FROM venues WHERE id=:id") suspend fun venue(id:Long):VenueEntity?
 @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insertVenue(value:VenueEntity):Long
 @Update suspend fun updateVenue(value:VenueEntity)
 @Query("DELETE FROM venues WHERE id=:id") suspend fun deleteVenue(id:Long)
 @Query("DELETE FROM venues") suspend fun clearVenues()

 @Query("SELECT * FROM branch_department_order") suspend fun departmentOrder():List<BranchDepartmentOrderEntity>
 @Query("SELECT * FROM product_branch_departments") fun observeProductDepartments():Flow<List<ProductBranchDepartmentEntity>>
 @Query("SELECT * FROM product_branch_departments") suspend fun productDepartments():List<ProductBranchDepartmentEntity>
 @Query("SELECT * FROM product_branch_departments WHERE productId=:productId AND branchId=:branchId") suspend fun productDepartment(productId:Long,branchId:Long):ProductBranchDepartmentEntity?
 @Query("DELETE FROM product_branch_departments WHERE productId=:productId AND branchId=:branchId") suspend fun removeProductDepartment(productId:Long,branchId:Long)
 @Query("DELETE FROM product_branch_departments WHERE productId=:productId") suspend fun clearProductDepartments(productId:Long)
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun setDepartmentOrder(value:BranchDepartmentOrderEntity)
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun setProductDepartment(value:ProductBranchDepartmentEntity)
}
