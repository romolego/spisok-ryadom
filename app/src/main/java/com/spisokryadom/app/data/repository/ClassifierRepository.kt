package com.spisokryadom.app.data.repository

import com.spisokryadom.app.data.dao.ProductGroupDao
import com.spisokryadom.app.data.dao.RecipientDao
import com.spisokryadom.app.data.dao.UnitDao
import com.spisokryadom.app.data.entity.ProductGroupEntity
import com.spisokryadom.app.data.entity.RecipientEntity
import com.spisokryadom.app.data.entity.UnitEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ClassifierRepository(
    private val productGroupDao: ProductGroupDao,
    private val recipientDao: RecipientDao,
    private val unitDao: UnitDao
) {
    fun getAllGroups(): Flow<List<ProductGroupEntity>> = productGroupDao.getAll()
    suspend fun getGroupById(id: Long): ProductGroupEntity? = productGroupDao.getById(id)
    suspend fun insertGroup(group: ProductGroupEntity): Long = productGroupDao.insert(group)
    suspend fun updateGroup(group: ProductGroupEntity) = productGroupDao.update(group)
    suspend fun deleteGroup(group: ProductGroupEntity) = productGroupDao.delete(group)
    suspend fun getMaxGroupDisplayOrder(): Int = productGroupDao.getMaxDisplayOrder()

    suspend fun swapGroupOrder(a: ProductGroupEntity, b: ProductGroupEntity) {
        val orderA = a.displayOrder
        val orderB = b.displayOrder
        productGroupDao.update(a.copy(displayOrder = orderB))
        productGroupDao.update(b.copy(displayOrder = orderA))
    }

    fun getAllRecipients(): Flow<List<RecipientEntity>> = recipientDao.getAll()
    suspend fun getRecipientById(id: Long): RecipientEntity? = recipientDao.getById(id)
    suspend fun insertRecipient(recipient: RecipientEntity): Long = recipientDao.insert(recipient)
    suspend fun updateRecipient(recipient: RecipientEntity) = recipientDao.update(recipient)
    suspend fun deleteRecipient(recipient: RecipientEntity) = recipientDao.delete(recipient)
    suspend fun getMaxRecipientDisplayOrder(): Int = recipientDao.getMaxDisplayOrder()

    suspend fun swapRecipientOrder(a: RecipientEntity, b: RecipientEntity) {
        val orderA = a.displayOrder
        val orderB = b.displayOrder
        recipientDao.update(a.copy(displayOrder = orderB))
        recipientDao.update(b.copy(displayOrder = orderA))
    }

    fun getAllUnits(): Flow<List<UnitEntity>> = unitDao.getAll()
    fun getOrderedUnitNames(): Flow<List<String>> = unitDao.getAll().map { list -> list.map { it.name } }
    suspend fun getUnitById(id: Long): UnitEntity? = unitDao.getById(id)
    suspend fun getUnitByName(name: String): UnitEntity? = unitDao.getByName(name)
    suspend fun insertUnit(unit: UnitEntity): Long = unitDao.insert(unit)
    suspend fun updateUnit(unit: UnitEntity) = unitDao.update(unit)
    suspend fun deleteUnit(unit: UnitEntity) = unitDao.delete(unit)
    suspend fun getMaxUnitDisplayOrder(): Int = unitDao.getMaxDisplayOrder()
    suspend fun getAllUnitsSync(): List<UnitEntity> = unitDao.getAllSync()
}
