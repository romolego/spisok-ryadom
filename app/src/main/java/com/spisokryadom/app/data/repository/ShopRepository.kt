package com.spisokryadom.app.data.repository

import com.spisokryadom.app.data.dao.ShopDao
import com.spisokryadom.app.data.dao.ShopDepartmentDao
import com.spisokryadom.app.data.entity.ShopDepartmentEntity
import com.spisokryadom.app.data.entity.ShopEntity
import kotlinx.coroutines.flow.Flow
import androidx.room.withTransaction
import com.spisokryadom.app.data.db.AppDatabase

class ShopRepository(
    private val shopDao: ShopDao,
    private val departmentDao: ShopDepartmentDao,
    val locations: LocationRepository,
    private val db: AppDatabase
) {
    suspend fun saveWithDepartments(shop:ShopEntity, departments:List<ShopDepartmentEntity>):Long = db.withTransaction {
        require(shop.name.isNotBlank()) { "Укажите название магазина" }
        require(db.cityDao().getById(shop.cityId)!=null) { "Город удалён" }
        val id=if(shop.id==0L)shopDao.insert(shop) else { require(shopDao.getById(shop.id)!=null);shopDao.update(shop);shop.id }
        val retained=departments.filter{it.id>0}.map{it.id}.toSet()
        departmentDao.getByShopIdSync(id).filter{it.id !in retained}.forEach{departmentDao.delete(it)}
        departments.forEachIndexed{index,dept->
            require(dept.name.isNotBlank())
            val value=dept.copy(id=dept.id.coerceAtLeast(0),shopId=id,displayOrder=index+1,name=dept.name.trim())
            if(value.id==0L)departmentDao.insert(value) else { require(departmentDao.getById(value.id)?.shopId==id);departmentDao.update(value) }
        }
        id
    }
    suspend fun addDepartmentInCity(name:String,city:Long)=db.withTransaction {
        require(name.isNotBlank())
        val shops=shopDao.getAllSync().filter{it.cityId==city};require(shops.isNotEmpty()){ "Сначала добавьте магазин этого города" }
        shops.forEach{shop->val deps=departmentDao.getByShopIdSync(shop.id);if(deps.none{com.spisokryadom.app.util.FuzzySearch.normalize(it.name)==com.spisokryadom.app.util.FuzzySearch.normalize(name)})departmentDao.insert(ShopDepartmentEntity(shopId=shop.id,name=name.trim(),displayOrder=(deps.maxOfOrNull{it.displayOrder}?:0)+1))}
    }
    suspend fun moveShop(id:Long,delta:Int)=db.withTransaction{
        val shop=shopDao.getById(id)?:return@withTransaction
        val list=shopDao.getAllSync().filter{it.cityId==shop.cityId}.sortedWith(compareBy<ShopEntity>{it.displayOrder}.thenBy{it.name}).toMutableList()
        val i=list.indexOfFirst{it.id==id};val j=i+delta
        if(i>=0&&j in list.indices){list.add(j,list.removeAt(i));shopDao.updateAll(list.mapIndexed{idx,s->s.copy(displayOrder=idx,manualOrderOverride=if(s.id==id||s.id==list[i].id)true else s.manualOrderOverride)})}
    }
    suspend fun markManualOrderOverride(id: Long)=shopDao.markManualOrderOverride(id)
    fun getCityShops(): Flow<List<ShopEntity>> = locations.cityShops
    fun getAllShops(): Flow<List<ShopEntity>> = shopDao.getAllOrdered()
    suspend fun getAllShopsSync(): List<ShopEntity> = shopDao.getAllSync()
    suspend fun getShopById(id: Long): ShopEntity? = shopDao.getById(id)
    suspend fun insertShop(shop: ShopEntity): Long = shopDao.insert(shop)
    suspend fun updateShop(shop: ShopEntity) = shopDao.update(shop)
    suspend fun deleteShop(shop: ShopEntity) = shopDao.delete(shop)

    fun getDepartments(shopId: Long): Flow<List<ShopDepartmentEntity>> =
        departmentDao.getByShopId(shopId)

    fun getAllDepartments(): Flow<List<ShopDepartmentEntity>> = departmentDao.getAll()

    suspend fun getDepartmentById(id: Long): ShopDepartmentEntity? = departmentDao.getById(id)
    suspend fun getDepartmentsByShopIdSync(shopId: Long): List<ShopDepartmentEntity> =
        departmentDao.getByShopIdSync(shopId)
    suspend fun insertDepartment(dept: ShopDepartmentEntity): Long = departmentDao.insert(dept)
    suspend fun updateDepartment(dept: ShopDepartmentEntity) = departmentDao.update(dept)
    suspend fun deleteDepartment(dept: ShopDepartmentEntity) = departmentDao.delete(dept)
    fun getDistinctDepartmentNames(): Flow<List<String>> = departmentDao.getDistinctNames()

    suspend fun updateDepartments(departments: List<ShopDepartmentEntity>) =
        departmentDao.updateAll(departments)

    suspend fun updateShops(shops: List<ShopEntity>) = shopDao.updateAll(shops)

    suspend fun renameDepartmentGlobally(oldName: String, newName: String) =
        departmentDao.renameAll(oldName, newName)

    suspend fun deleteDepartmentGlobally(name: String) =
        departmentDao.deleteAllByName(name)
}
