package com.spisokryadom.app.data.repository

import com.spisokryadom.app.data.dao.ProductGroupDao
import com.spisokryadom.app.data.dao.RecipientDao
import com.spisokryadom.app.data.dao.UnitDao
import com.spisokryadom.app.data.entity.ProductGroupEntity
import com.spisokryadom.app.data.entity.RecipientEntity
import com.spisokryadom.app.data.entity.UnitEntity
import kotlinx.coroutines.flow.Flow
import androidx.room.withTransaction
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.util.Quantities
import kotlinx.coroutines.flow.map

class ClassifierRepository(
    private val productGroupDao: ProductGroupDao,
    private val recipientDao: RecipientDao,
    private val unitDao: UnitDao,
    private val db:AppDatabase
) {
    suspend fun moveRecipient(id:Long,delta:Int)=db.withTransaction{val list=recipientDao.getAllSync().toMutableList();val i=list.indexOfFirst{it.id==id};val j=i+delta;if(i>=0&&j in list.indices){list.add(j,list.removeAt(i));recipientDao.updateAll(list.mapIndexed{n,v->v.copy(displayOrder=n)})}}
    suspend fun moveGroup(id:Long,delta:Int)=db.withTransaction{val list=productGroupDao.getAllSync().toMutableList();val i=list.indexOfFirst{it.id==id};val j=i+delta;if(i>=0&&j in list.indices){list.add(j,list.removeAt(i));productGroupDao.updateAll(list.mapIndexed{n,v->v.copy(displayOrder=n)})}}
    suspend fun moveUnit(id:Long,delta:Int)=db.withTransaction{val list=unitDao.getAllSync().toMutableList();val i=list.indexOfFirst{it.id==id};val j=i+delta;if(i>=0&&j in list.indices){list.add(j,list.removeAt(i));unitDao.updateAll(list.mapIndexed{n,v->v.copy(displayOrder=n)})}}
    suspend fun renameUnitAndProducts(unit:UnitEntity,name:String)=db.withTransaction{
        val value=Quantities.unit(name);require(value.isNotBlank())
        require(unitDao.getAllSync().none{it.id!=unit.id&&Quantities.unit(it.name)==value}){"Такая единица уже есть"}
        val old=unitDao.getById(unit.id)?:error("Единица удалена")
        unitDao.update(old.copy(name=value));db.productDao().renameUnit(old.name,value)
    }
    suspend fun deleteUnitAndProducts(unit:UnitEntity)=db.withTransaction{db.productDao().clearUnit(unit.name);unitDao.delete(unit)}
    fun getAllGroups(): Flow<List<ProductGroupEntity>> = productGroupDao.getAll()
    suspend fun getGroupById(id: Long): ProductGroupEntity? = productGroupDao.getById(id)
    suspend fun insertGroup(group: ProductGroupEntity): Long = productGroupDao.insert(group)
    suspend fun updateGroup(group: ProductGroupEntity) = productGroupDao.update(group)
    suspend fun deleteGroup(group: ProductGroupEntity) = productGroupDao.delete(group)
    suspend fun getMaxGroupDisplayOrder(): Int = productGroupDao.getMaxDisplayOrder()

    suspend fun swapGroupOrder(a: ProductGroupEntity, b: ProductGroupEntity) = db.withTransaction {
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

    suspend fun swapRecipientOrder(a: RecipientEntity, b: RecipientEntity) = db.withTransaction {
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
