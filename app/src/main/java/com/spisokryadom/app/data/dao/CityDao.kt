package com.spisokryadom.app.data.dao
import androidx.room.*
import com.spisokryadom.app.data.entity.CityEntity
import kotlinx.coroutines.flow.Flow
@Dao
interface CityDao {
    @Query("SELECT * FROM cities ORDER BY id") fun observeAll(): Flow<List<CityEntity>>
    @Query("SELECT * FROM cities ORDER BY id") suspend fun getAllSync(): List<CityEntity>
    @Query("SELECT * FROM cities WHERE id=:id") suspend fun getById(id: Long): CityEntity?
    @Insert suspend fun insert(city: CityEntity): Long
    @Update suspend fun update(city: CityEntity)
    @Query("DELETE FROM cities WHERE id=:id AND id NOT IN (SELECT cityId FROM shops)") suspend fun deleteEmpty(id: Long)
    @Query("DELETE FROM cities") suspend fun deleteAll()
}
