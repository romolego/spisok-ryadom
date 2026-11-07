package com.spisokryadom.app.data.dao

import androidx.room.*
import com.spisokryadom.app.data.entity.*
import kotlinx.coroutines.flow.Flow

@Dao interface ProductInputDao {
    @Query("SELECT * FROM product_input_settings WHERE id=1") suspend fun settings():ProductInputSettings?
    @Query("SELECT * FROM product_input_settings WHERE id=1") fun observeSettings():Flow<ProductInputSettings?>
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun settings(value:ProductInputSettings)
    @Query("SELECT * FROM product_default_departments WHERE productId=:id") suspend fun department(id:Long):ProductDefaultDepartment?
    @Query("SELECT * FROM product_default_departments") suspend fun departments():List<ProductDefaultDepartment>
    @Query("SELECT * FROM product_default_departments") fun observeDepartments():Flow<List<ProductDefaultDepartment>>
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun department(value:ProductDefaultDepartment)
    @Query("SELECT * FROM bulk_purchase_runs") suspend fun runs():List<BulkPurchaseRun>
    @Query("DELETE FROM product_input_settings") suspend fun clearSettings()
    @Query("DELETE FROM bulk_purchase_runs") suspend fun clearRuns()
    @Query("SELECT * FROM bulk_purchase_runs WHERE token=:token") suspend fun run(token:String):BulkPurchaseRun?
    @Insert suspend fun run(value:BulkPurchaseRun)
}
