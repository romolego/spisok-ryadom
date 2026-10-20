package com.spisokryadom.app.data.dao
import androidx.room.*
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.util.FuzzySearch
import kotlinx.coroutines.flow.Flow

@Dao abstract class ShopDepartmentDao {
 @Query("SELECT d.id,d.shopId,t.name,d.displayOrder,d.typeId FROM shop_departments d JOIN department_types t ON t.id=d.typeId WHERE d.id=:id") abstract suspend fun getById(id:Long):ShopDepartmentEntity?
 @Query("SELECT d.id,d.shopId,t.name,d.displayOrder,d.typeId FROM shop_departments d JOIN department_types t ON t.id=d.typeId ORDER BY d.shopId,d.displayOrder,d.id") abstract fun getAll():Flow<List<ShopDepartmentEntity>>
 @Query("SELECT d.id,d.shopId,t.name,d.displayOrder,d.typeId FROM shop_departments d JOIN department_types t ON t.id=d.typeId WHERE d.shopId=:shopId ORDER BY d.displayOrder,t.name,d.id") abstract suspend fun getByShopIdSync(shopId:Long):List<ShopDepartmentEntity>
 @Query("SELECT d.id,d.shopId,t.name,d.displayOrder,d.typeId FROM shop_departments d JOIN department_types t ON t.id=d.typeId ORDER BY d.shopId,d.displayOrder,d.id") abstract suspend fun getAllSync():List<ShopDepartmentEntity>
 @Query("SELECT * FROM department_types ORDER BY displayOrder,name,id") abstract fun observeTypes():Flow<List<DepartmentTypeEntity>>
 @Query("SELECT * FROM department_types ORDER BY displayOrder,name,id") abstract suspend fun types():List<DepartmentTypeEntity>
 @Query("SELECT * FROM department_types WHERE id=:id") abstract suspend fun type(id:Long):DepartmentTypeEntity?
 @Query("SELECT * FROM department_types WHERE normalizedName=:key LIMIT 1") abstract suspend fun typeByKey(key:String):DepartmentTypeEntity?
 @Insert(onConflict=OnConflictStrategy.ABORT) abstract suspend fun insertType(value:DepartmentTypeEntity):Long
 @Update abstract suspend fun updateType(value:DepartmentTypeEntity)
 @Query("DELETE FROM department_types WHERE id=:id") abstract suspend fun deleteType(id:Long)
 @Query("DELETE FROM department_types") abstract suspend fun clearTypes()
 @Query("SELECT name FROM department_types ORDER BY displayOrder,name,id") abstract fun getDistinctNames():Flow<List<String>>
 @Query("DELETE FROM shop_departments") abstract suspend fun deleteAll()
 @Insert(onConflict=OnConflictStrategy.ABORT) abstract suspend fun insertPlacement(value:ShopDepartmentPlacementEntity):Long
 @Update abstract suspend fun updatePlacement(value:ShopDepartmentPlacementEntity)
 @Query("DELETE FROM shop_departments WHERE id=:id") abstract suspend fun deletePlacement(id:Long)

 @Transaction open suspend fun ensureType(name:String):Long {
  val key=FuzzySearch.normalize(name);require(key.isNotBlank()) { "Укажите название группы" }
  return typeByKey(key)?.id ?: insertType(DepartmentTypeEntity(name=name.trim(),normalizedName=key,displayOrder=(types().maxOfOrNull{it.displayOrder}?:-1)+1))
 }
 @Transaction open suspend fun insert(value:ShopDepartmentEntity):Long {
  val typeId=if(value.typeId>0){require(type(value.typeId)!=null);value.typeId}else ensureType(value.name)
  return insertPlacement(ShopDepartmentPlacementEntity(value.id,value.shopId,typeId,value.displayOrder))
 }
 @Transaction open suspend fun update(value:ShopDepartmentEntity) {
  val old=getById(value.id)?:error("Группа удалена")
  // A shared group's name has one identity even when renamed in a shop's editor.
  if(value.name!=old.name)renameType(old.typeId,value.name)
  updatePlacement(ShopDepartmentPlacementEntity(value.id,value.shopId,old.typeId,value.displayOrder))
 }
 @Transaction open suspend fun updateAll(values:List<ShopDepartmentEntity>){values.forEach{update(it)}}
 open suspend fun delete(value:ShopDepartmentEntity)=deletePlacement(value.id)
 @Transaction open suspend fun renameType(id:Long,name:String) {
  val old=type(id)?:error("Группа удалена");val key=FuzzySearch.normalize(name);require(key.isNotBlank())
  require(typeByKey(key)?.id.let{it==null||it==id}) { "Группа с таким названием уже есть" }
  updateType(old.copy(name=name.trim(),normalizedName=key))
 }
}
