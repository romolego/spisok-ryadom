package com.spisokryadom.app.data.dao

import androidx.room.*
import com.spisokryadom.app.data.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductVariantDao {
 @Query("SELECT * FROM product_variants ORDER BY displayOrder,productId") fun observeVariants():Flow<List<ProductVariantEntity>>
 @Query("SELECT * FROM product_variants ORDER BY displayOrder,productId") suspend fun variants():List<ProductVariantEntity>
 @Query("SELECT * FROM product_variants WHERE productId=:id") suspend fun variant(id:Long):ProductVariantEntity?
 @Query("SELECT * FROM product_variants WHERE familyId=:familyId ORDER BY displayOrder,productId") suspend fun members(familyId:Long):List<ProductVariantEntity>
 @Query("SELECT * FROM product_variant_cities WHERE cityId=:cityId AND productId IN (SELECT productId FROM product_variants WHERE familyId=:familyId)") suspend fun rules(familyId:Long,cityId:Long):List<ProductVariantCityEntity>
 @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insert(value:ProductVariantEntity)
 @Update suspend fun update(value:ProductVariantEntity)
 @Query("DELETE FROM product_variants WHERE productId=:id AND productId!=familyId") suspend fun deleteVariant(id:Long)
 @Query("SELECT * FROM product_variant_cities") fun observeCities():Flow<List<ProductVariantCityEntity>>
 @Query("SELECT * FROM product_variant_cities") suspend fun cities():List<ProductVariantCityEntity>
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun setCity(value:ProductVariantCityEntity)
 @Query("SELECT * FROM product_photos ORDER BY productId,position") suspend fun photos():List<ProductPhotoEntity>
 @Query("SELECT * FROM product_photos WHERE productId=:id ORDER BY position") suspend fun photos(id:Long):List<ProductPhotoEntity>
 @Query("SELECT * FROM product_photos ORDER BY productId,position") fun observePhotos():Flow<List<ProductPhotoEntity>>
 @Query("DELETE FROM product_photos WHERE productId=:id") suspend fun clearPhotos(id:Long)
 @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insertPhoto(value:ProductPhotoEntity)
}
