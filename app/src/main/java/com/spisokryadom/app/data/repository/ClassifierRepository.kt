package com.spisokryadom.app.data.repository

import com.spisokryadom.app.data.dao.ProductGroupDao
import com.spisokryadom.app.data.dao.RecipientDao
import com.spisokryadom.app.data.dao.UnitDao
import com.spisokryadom.app.data.entity.ProductGroupEntity
import com.spisokryadom.app.data.entity.RecipientEntity
import com.spisokryadom.app.data.entity.UnitEntity
import kotlinx.coroutines.flow.Flow
import com.spisokryadom.app.util.FuzzySearch
import androidx.room.withTransaction
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.util.Quantities
import kotlinx.coroutines.flow.map

class ClassifierRepository(
    private val productGroupDao: ProductGroupDao,
    private val recipientDao: RecipientDao,
    private val unitDao: UnitDao,
    private val db:AppDatabase, private val catalogueEdits:CatalogueEdits?=null
) {
    suspend fun alphabetize(kind:String)=db.withTransaction {
        when(kind) {
            "group"->productGroupDao.updateAll(productGroupDao.getAllSync().sortedBy{FuzzySearch.normalize(it.name)}.mapIndexed{i,v->v.copy(displayOrder=i)})
            "recipient"->recipientDao.updateAll(recipientDao.getAllSync().sortedBy{FuzzySearch.normalize(it.name)}.mapIndexed{i,v->v.copy(displayOrder=i)})
            "unit"->unitDao.updateAll(unitDao.getAllSync().sortedBy{FuzzySearch.normalize(it.name)}.mapIndexed{i,v->v.copy(displayOrder=i)})
        }
    }
    suspend fun style(kind:String,id:Long,accent:String,icon:String)=db.withTransaction {
        when(kind) {
            "group"->productGroupDao.getById(id)?.let{productGroupDao.update(it.copy(accent=accent,iconKey=icon))}
            "recipient"->recipientDao.getById(id)?.let{recipientDao.update(it.copy(accent=accent,iconKey=icon))}
        }
    }
    suspend fun moveRecipient(id:Long,delta:Int)=db.withTransaction{val list=recipientDao.getAllSync().toMutableList();val i=list.indexOfFirst{it.id==id};val j=i+delta;if(i>=0&&j in list.indices){list.add(j,list.removeAt(i));recipientDao.updateAll(list.mapIndexed{n,v->v.copy(displayOrder=n)})}}
    suspend fun moveGroup(id:Long,delta:Int)=db.withTransaction{val list=productGroupDao.getAllSync().toMutableList();val i=list.indexOfFirst{it.id==id};val j=i+delta;if(i>=0&&j in list.indices){list.add(j,list.removeAt(i));productGroupDao.updateAll(list.mapIndexed{n,v->v.copy(displayOrder=n)})}}
    suspend fun moveUnit(id:Long,delta:Int)=db.withTransaction{val list=unitDao.getAllSync().toMutableList();val i=list.indexOfFirst{it.id==id};val j=i+delta;if(i>=0&&j in list.indices){list.add(j,list.removeAt(i));unitDao.updateAll(list.mapIndexed{n,v->v.copy(displayOrder=n)})}}
    suspend fun renameUnitAndProducts(unit:UnitEntity,name:String)= withCatalogueEdits(db,catalogueEdits) { db.withTransaction{
        val value=Quantities.unit(name);require(value.isNotBlank())
        require(unitDao.getAllSync().none{it.id!=unit.id&&Quantities.unit(it.name)==value}){"Такая единица уже есть"}
        val old=unitDao.getById(unit.id)?:error("Единица удалена")
        unitDao.update(old.copy(name=value));replaceUnitReferences(old.name,value)
    } }
    suspend fun deleteUnitAndProducts(unit:UnitEntity)= withCatalogueEdits(db,catalogueEdits) { db.withTransaction{
        val current=unitDao.getById(unit.id)?:return@withTransaction
        val remaining=unitDao.getAllSync().firstOrNull{it.id!=current.id&&Quantities.unit(it.name)==Quantities.unit(current.name)}
        replaceUnitReferences(current.name,remaining?.let{Quantities.unit(it.name)});unitDao.delete(current)
    } }
    // Old backups and earlier input contain aliases such as "КГ." and "килограмм".
    // A unit is a value, so every spelling of that value follows the same edit.
    private suspend fun replaceUnitReferences(oldName:String,newName:String?) {
        val canonical=Quantities.unit(oldName)
        db.productDao().getAllSync().filter{Quantities.unit(it.defaultUnit.orEmpty())==canonical}.mapNotNull{it.defaultUnit}.distinct().forEach{raw->
            if(newName==null)db.productDao().clearUnit(raw)else db.productDao().renameUnit(raw,newName)
        }
        val now=System.currentTimeMillis()
        // Target rows own the unit their planned price refers to. A catalogue
        // dictionary edit must not reinterpret a recipe/project's saved facts.
        db.shoppingListEntryDao().getAllSync().filter{it.listId==null&&!it.isBought&&Quantities.unit(it.unit)==canonical}.map{it.unit}.distinct().forEach{raw->
            db.shoppingListEntryDao().renamePendingUnit(raw,newName.orEmpty(),now)
        }
    }
    fun creationPresets()=db.creationPresetDao().observeGroups()
    suspend fun defaultRecipient(groupId:Long,recipientId:Long?)=db.withTransaction{
        require(productGroupDao.getById(groupId)!=null){"Группа удалена"}
        require(recipientId==null||recipientDao.getById(recipientId)!=null){"Получатель удалён"}
        db.creationPresetDao().setGroup(com.spisokryadom.app.data.entity.GroupCreationPreset(groupId,recipientId))
    }
    suspend fun addStandardCategories()=db.withTransaction{com.spisokryadom.app.data.db.StandardCategories.install(db.openHelper.writableDatabase)}
    fun getAllGroups(): Flow<List<ProductGroupEntity>> = productGroupDao.getAll()
    suspend fun getGroupById(id: Long): ProductGroupEntity? = productGroupDao.getById(id)
    suspend fun insertGroup(group: ProductGroupEntity): Long = productGroupDao.insert(group)
    suspend fun updateGroup(group: ProductGroupEntity) = productGroupDao.update(group)
    suspend fun deleteGroup(group: ProductGroupEntity) = withCatalogueEdits(db,catalogueEdits){db.withTransaction{productGroupDao.delete(group)}}
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
    suspend fun deleteRecipient(recipient: RecipientEntity) = withCatalogueEdits(db,catalogueEdits){db.withTransaction{recipientDao.delete(recipient)}}
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
    suspend fun insertUnit(unit: UnitEntity): Long = db.withTransaction {
        val name=Quantities.unit(unit.name);require(name.isNotBlank()){"Укажите единицу измерения"}
        unitDao.getAllSync().find{Quantities.unit(it.name)==name}?.id?:unitDao.insert(unit.copy(name=name))
    }
    suspend fun updateUnit(unit: UnitEntity) = unitDao.update(unit)
    suspend fun deleteUnit(unit: UnitEntity) = unitDao.delete(unit)
    suspend fun getMaxUnitDisplayOrder(): Int = unitDao.getMaxDisplayOrder()
    suspend fun getAllUnitsSync(): List<UnitEntity> = unitDao.getAllSync()
}
