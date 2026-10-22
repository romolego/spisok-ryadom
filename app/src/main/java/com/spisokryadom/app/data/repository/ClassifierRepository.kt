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
        require(kind in setOf("recipient","unit"))
        when(kind) {
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
    suspend fun favoriteGroup(id:Long,value:Boolean)=db.withTransaction {
        val current=productGroupDao.getById(id)?:error("Группа удалена")
        if(current.isFavorite==value)return@withTransaction
        val favorites=productGroupDao.getAllSync().filter{it.isFavorite}
            .sortedWith(compareBy<ProductGroupEntity>{it.favoriteOrder}.thenBy{FuzzySearch.normalize(it.name)}.thenBy{it.id})
        val order=com.spisokryadom.app.util.nextFavoriteOrder(favorites.map{it.favoriteOrder})
        if(value&&order.compact)productGroupDao.updateAll(favorites.mapIndexed{i,v->v.copy(favoriteOrder=i)})
        productGroupDao.update(current.copy(isFavorite=value,favoriteOrder=if(value)order.next else 0))
    }
    suspend fun moveGroup(id:Long,delta:Int)=db.withTransaction {
        require(delta in -1..1)
        val list=productGroupDao.getAllSync().filter{it.isFavorite}.sortedWith(compareBy<ProductGroupEntity>{it.favoriteOrder}.thenBy{FuzzySearch.normalize(it.name)}.thenBy{it.id}).toMutableList()
        val i=list.indexOfFirst{it.id==id};val j=i+delta
        if(i>=0&&j in list.indices){list.add(j,list.removeAt(i));productGroupDao.updateAll(list.mapIndexed{n,v->v.copy(favoriteOrder=n)})}
    }
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
    fun getAllGroups(): Flow<List<ProductGroupEntity>> = productGroupDao.getAll().map{com.spisokryadom.app.util.alphabetical(it){v->v.name}}
    suspend fun getGroupById(id: Long): ProductGroupEntity? = productGroupDao.getById(id)
    suspend fun insertGroup(group: ProductGroupEntity): Long = db.withTransaction {
        val rows=productGroupDao.getAllSync()
        val order=com.spisokryadom.app.util.appendOrder(rows.map{it.displayOrder})
        if(group.displayOrder==Int.MIN_VALUE&&order.ranks.isNotEmpty()){
            productGroupDao.updateAll(rows.map{it.copy(displayOrder=order.ranks.getValue(it.displayOrder))})
            productGroupDao.insert(group.copy(displayOrder=order.next))
        }else productGroupDao.insert(group)
    }
    suspend fun renameGroup(id:Long,name:String)=db.withTransaction{productGroupDao.getById(id)?.let{productGroupDao.update(it.copy(name=name))}}
    suspend fun deleteGroup(group: ProductGroupEntity) = withCatalogueEdits(db,catalogueEdits){db.withTransaction{productGroupDao.delete(group)}}
    suspend fun getMaxGroupDisplayOrder(): Int = productGroupDao.getMaxDisplayOrder()


    fun getAllRecipients(): Flow<List<RecipientEntity>> = recipientDao.getAll()
    suspend fun getRecipientById(id: Long): RecipientEntity? = recipientDao.getById(id)
    suspend fun insertRecipient(recipient: RecipientEntity): Long = db.withTransaction {
        val rows=recipientDao.getAllSync()
        val order=com.spisokryadom.app.util.appendOrder(rows.map{it.displayOrder})
        if(recipient.displayOrder==Int.MIN_VALUE&&order.ranks.isNotEmpty()){
            recipientDao.updateAll(rows.map{it.copy(displayOrder=order.ranks.getValue(it.displayOrder))})
            recipientDao.insert(recipient.copy(displayOrder=order.next))
        }else recipientDao.insert(recipient)
    }
    suspend fun updateRecipient(recipient: RecipientEntity) = recipientDao.update(recipient)
    suspend fun deleteRecipient(recipient: RecipientEntity) = withCatalogueEdits(db,catalogueEdits){db.withTransaction{recipientDao.delete(recipient)}}
    suspend fun getMaxRecipientDisplayOrder(): Int = recipientDao.getMaxDisplayOrder()


    fun getAllUnits(): Flow<List<UnitEntity>> = unitDao.getAll()
    fun getOrderedUnitNames(): Flow<List<String>> = unitDao.getAll().map { list -> list.map { it.name } }
    suspend fun getUnitById(id: Long): UnitEntity? = unitDao.getById(id)
    suspend fun insertUnit(unit: UnitEntity): Long = db.withTransaction {
        val name=Quantities.unit(unit.name);require(name.isNotBlank()){"Укажите единицу измерения"}
        val rows=unitDao.getAllSync()
        rows.find{Quantities.unit(it.name)==name}?.id?:run {
            val order=com.spisokryadom.app.util.appendOrder(rows.map{it.displayOrder})
            val value=if(unit.displayOrder==Int.MIN_VALUE&&order.ranks.isNotEmpty()){
                unitDao.updateAll(rows.map{it.copy(displayOrder=order.ranks.getValue(it.displayOrder))})
                unit.copy(name=name,displayOrder=order.next)
            }else unit.copy(name=name)
            unitDao.insert(value)
        }
    }
    suspend fun getMaxUnitDisplayOrder(): Int = unitDao.getMaxDisplayOrder()
}
