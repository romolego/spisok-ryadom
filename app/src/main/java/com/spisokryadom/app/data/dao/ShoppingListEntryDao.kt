package com.spisokryadom.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.spisokryadom.app.data.entity.ShoppingListEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingListEntryDao {
    @Query("""
        SELECT * FROM shopping_list_entries
        ORDER BY isBought ASC, isUrgent DESC, createdAt DESC
    """)
    fun getAll(): Flow<List<ShoppingListEntryEntity>>

    @Query("SELECT * FROM shopping_list_entries WHERE id = :id")
    suspend fun getById(id: Long): ShoppingListEntryEntity?

    @Query("""
        SELECT * FROM shopping_list_entries
        WHERE assignedShopId = :shopId
        ORDER BY isBought ASC, isUrgent DESC, createdAt DESC
    """)
    fun getByShopId(shopId: Long): Flow<List<ShoppingListEntryEntity>>

    @Query("UPDATE shopping_list_entries SET isBought = 1, updatedAt = :now WHERE id = :id")
    suspend fun markBought(id: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE shopping_list_entries SET isBought = 0, updatedAt = :now WHERE id = :id")
    suspend fun markNotBought(id: Long, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM shopping_list_entries WHERE isBought = 1")
    suspend fun clearBought()

    @Query("DELETE FROM shopping_list_entries WHERE isBought = 1 AND assignedShopId = :shopId")
    suspend fun clearBoughtByShop(shopId: Long)

    @Query("DELETE FROM shopping_list_entries WHERE isBought = 1 AND assignedShopId IS NULL")
    suspend fun clearBoughtWithoutShop()

    @Query("SELECT * FROM shopping_list_entries ORDER BY createdAt ASC")
    suspend fun getAllSync(): List<ShoppingListEntryEntity>

    @Query("UPDATE shopping_list_entries SET unit=:newUnit,updatedAt=:now WHERE unit=:oldUnit AND isBought=0")
    suspend fun renamePendingUnit(oldUnit:String,newUnit:String,now:Long)

    @Query("DELETE FROM shopping_list_entries")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: ShoppingListEntryEntity): Long

    @Update
    suspend fun update(entry: ShoppingListEntryEntity)

    @Delete
    suspend fun delete(entry: ShoppingListEntryEntity)

    @Query("DELETE FROM shopping_list_entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM shopping_list_entries WHERE productId=:productId AND isBought=0 AND assignedShopId IS :shopId ORDER BY id")
    suspend fun getMergeCandidates(productId: Long, shopId: Long?): List<ShoppingListEntryEntity>
    @Query("UPDATE shopping_list_entries SET isUrgent=CASE WHEN isUrgent=1 THEN 0 ELSE 1 END, updatedAt=:now WHERE id=:id")
    suspend fun toggleUrgency(id: Long, now: Long = System.currentTimeMillis())
    /**
     * Atomically adds a new entry or merges quantity with existing active entry.
     */
    @Transaction
    suspend fun addOrMerge(entry: ShoppingListEntryEntity): Long {
        val canonicalUnit = com.spisokryadom.app.util.Quantities.unit(entry.unit)
        // Compare old persisted spellings as well as new input. This also works for an
        // existing schema-8 database or a restored snapshot, without rewriting history.
        val candidates = if (entry.isBought) emptyList() else
            getMergeCandidates(entry.productId, entry.assignedShopId).filter {
                com.spisokryadom.app.util.Quantities.unit(it.unit) == canonicalUnit &&
                    it.assignedDepartmentId == entry.assignedDepartmentId && it.assignedBranchId == entry.assignedBranchId &&
                    it.note == entry.note && it.comment == entry.comment
            }
        // Legacy records have no known intent. They adopt one unambiguous new
        // choice, without bridging explicit no-shop and automatic city contexts.
        fun intent(row:ShoppingListEntryEntity):Pair<Boolean,Long?>?=when {
            row.assignedShopId!=null->null
            row.explicitNoShop->true to null
            row.awaitingShopCityId!=null->false to row.awaitingShopCityId
            else->null
        }
        val targetIntent=intent(entry)?:candidates.mapNotNull(::intent).distinct().singleOrNull()
        val compatible=candidates.filter{intent(it)==null||intent(it)==targetIntent}
        if (compatible.isEmpty()) return insert(entry.copy(unit = canonicalUnit))
        val first = compatible.first()
        // Validate before any update/delete: an overflowing merge leaves every row intact.
        val quantity = com.spisokryadom.app.util.Quantities.valid(
            compatible.sumOf { it.quantity } + entry.quantity
        )
        update(first.copy(quantity = quantity, unit = canonicalUnit,explicitNoShop=targetIntent?.first?:false,awaitingShopCityId=targetIntent?.second,
            isUrgent = entry.isUrgent || compatible.any { it.isUrgent },
            updatedAt = System.currentTimeMillis()))
        compatible.drop(1).forEach { deleteById(it.id) }
        return first.id
    }
}
