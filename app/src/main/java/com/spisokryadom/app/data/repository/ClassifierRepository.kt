package com.spisokryadom.app.data.repository

import com.spisokryadom.app.data.dao.ProductGroupDao
import com.spisokryadom.app.data.dao.RecipientDao
import com.spisokryadom.app.data.entity.ProductGroupEntity
import com.spisokryadom.app.data.entity.RecipientEntity
import kotlinx.coroutines.flow.Flow

class ClassifierRepository(
    private val productGroupDao: ProductGroupDao,
    private val recipientDao: RecipientDao
) {
    fun getAllGroups(): Flow<List<ProductGroupEntity>> = productGroupDao.getAll()
    suspend fun getGroupById(id: Long): ProductGroupEntity? = productGroupDao.getById(id)
    suspend fun insertGroup(group: ProductGroupEntity): Long = productGroupDao.insert(group)
    suspend fun updateGroup(group: ProductGroupEntity) = productGroupDao.update(group)
    suspend fun deleteGroup(group: ProductGroupEntity) = productGroupDao.delete(group)

    fun getAllRecipients(): Flow<List<RecipientEntity>> = recipientDao.getAll()
    suspend fun getRecipientById(id: Long): RecipientEntity? = recipientDao.getById(id)
    suspend fun insertRecipient(recipient: RecipientEntity): Long = recipientDao.insert(recipient)
    suspend fun updateRecipient(recipient: RecipientEntity) = recipientDao.update(recipient)
    suspend fun deleteRecipient(recipient: RecipientEntity) = recipientDao.delete(recipient)
}
