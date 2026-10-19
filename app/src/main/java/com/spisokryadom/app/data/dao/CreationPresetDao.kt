package com.spisokryadom.app.data.dao

import androidx.room.*
import com.spisokryadom.app.data.entity.*
import kotlinx.coroutines.flow.Flow

@Dao interface CreationPresetDao {
 @Query("SELECT * FROM shop_creation_presets") fun observeShops():Flow<List<ShopCreationPreset>>
 @Query("SELECT * FROM group_creation_presets") fun observeGroups():Flow<List<GroupCreationPreset>>
 @Query("SELECT * FROM shop_creation_presets") suspend fun shops():List<ShopCreationPreset>
 @Query("SELECT * FROM group_creation_presets") suspend fun groups():List<GroupCreationPreset>
 @Query("SELECT purchaseMode FROM shop_creation_presets WHERE shopId=:id") suspend fun shopMode(id:Long):String?
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun setShop(value:ShopCreationPreset)
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun setGroup(value:GroupCreationPreset)
}
