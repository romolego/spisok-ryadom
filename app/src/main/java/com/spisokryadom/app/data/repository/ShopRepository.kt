package com.spisokryadom.app.data.repository

import com.spisokryadom.app.data.dao.ShopDao
import com.spisokryadom.app.data.dao.ShopDepartmentDao
import com.spisokryadom.app.data.entity.ShopDepartmentEntity
import com.spisokryadom.app.data.entity.ShopEntity
import kotlinx.coroutines.flow.Flow

class ShopRepository(
    private val shopDao: ShopDao,
    private val departmentDao: ShopDepartmentDao
) {
    fun getAllShops(): Flow<List<ShopEntity>> = shopDao.getAllOrdered()
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
    suspend fun updateShops(shops: List<ShopEntity>) = shopDao.updateAll(shops)
}
