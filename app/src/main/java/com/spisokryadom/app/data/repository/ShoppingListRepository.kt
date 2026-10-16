package com.spisokryadom.app.data.repository
import androidx.room.withTransaction
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.dao.ShoppingListEntryDao
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.util.Quantities
import com.spisokryadom.app.util.FuzzySearch
import kotlinx.coroutines.flow.Flow

enum class ShopChoice { AUTO, NONE, EXPLICIT }
class ShoppingListRepository(private val entryDao: ShoppingListEntryDao, private val db: AppDatabase, private val locations: LocationRepository) {
    fun getAllEntries(): Flow<List<ShoppingListEntryEntity>> = entryDao.getAll()
    fun getEntriesByShop(shopId: Long) = entryDao.getByShopId(shopId)
    suspend fun getEntryById(id: Long) = entryDao.getById(id)
    suspend fun resolveShop(productId: Long, cityId: Long, choice: ShopChoice, explicitId: Long?): Pair<Long?,Long?> {
        if (choice==ShopChoice.NONE) return null to null
        val links=db.productShopLinkDao().getLinksForProductSync(productId)
        val shops=db.shopDao().getAllSync().filter { it.cityId==cityId }.associateBy { it.id }
        val shopId=if (choice==ShopChoice.EXPLICIT) {
            require(explicitId in shops) { "Магазин недоступен в выбранном городе" }; explicitId
        } else links.filter { it.shopId in shops }.minWithOrNull(compareBy<ProductShopLinkEntity> { it.priority }.thenBy { shops[it.shopId]?.displayOrder })?.shopId
        val dept=links.find { it.shopId==shopId }?.departmentId?.let { id -> db.shopDepartmentDao().getById(id)?.takeIf { it.shopId==shopId }?.id }
        return shopId to dept
    }
    suspend fun addProduct(productId: Long, quantity: Double?=null, unit: String?=null, choice: ShopChoice=ShopChoice.AUTO, shopId: Long?=null): Long {
        val cityId=locations.currentId()
        return db.withTransaction {
            val product=db.productDao().getById(productId) ?: error("Товар удалён")
            val target=resolveShop(productId,cityId,choice,shopId)
            entryDao.addOrMerge(ShoppingListEntryEntity(productId=productId,
                quantity=Quantities.valid(quantity ?: product.defaultQuantity?.takeIf { it>0 && it.isFinite() } ?: 1.0),
                unit=Quantities.unit(unit ?: product.defaultUnit.orEmpty()),assignedShopId=target.first,assignedDepartmentId=target.second))
        }
    }
    suspend fun addNewProduct(name: String, quantity: Double, unit: String, permanent: Boolean, choice: ShopChoice=ShopChoice.AUTO, shopId: Long?=null): Long {
        val cityId=locations.currentId(); require(name.isNotBlank()) { "Введите название товара" }
        return db.withTransaction {
            val product=ProductEntity(name=name.trim(), defaultUnit=Quantities.unit(unit).ifBlank { null },isTemporary=!permanent)
            val id=db.productDao().insert(product)
            if (choice==ShopChoice.EXPLICIT) {
                val shop=db.shopDao().getById(shopId ?: -1)
                require(shop?.cityId==cityId) { "Магазин недоступен в выбранном городе" }
                if(permanent) db.productShopLinkDao().insert(ProductShopLinkEntity(productId=id,shopId=shopId!!))
            }
            entryDao.insert(ShoppingListEntryEntity(productId=id,quantity=Quantities.valid(quantity),unit=Quantities.unit(unit),assignedShopId=if(choice==ShopChoice.EXPLICIT)shopId else null))
        }
    }
    suspend fun addEntry(entry: ShoppingListEntryEntity): Long = addOrMergeEntry(entry)
    suspend fun addOrMergeEntry(entry: ShoppingListEntryEntity): Long = db.withTransaction {
        validateTarget(entry)
        entryDao.addOrMerge(entry.copy(quantity=Quantities.valid(entry.quantity),unit=Quantities.unit(entry.unit)))
    }
    private suspend fun validateTarget(entry: ShoppingListEntryEntity) {
        require(db.productDao().getById(entry.productId)!=null) { "Товар удалён" }
        if(entry.assignedShopId!=null) require(db.shopDao().getById(entry.assignedShopId)!=null) { "Магазин удалён" }
        if(entry.assignedDepartmentId!=null) require(db.shopDepartmentDao().getById(entry.assignedDepartmentId)?.shopId==entry.assignedShopId) { "Группа относится к другому магазину" }
    }
    suspend fun findActiveByProductId(productId: Long): ShoppingListEntryEntity? {
        val city=locations.currentId();val shops=db.shopDao().getAllSync().filter { it.cityId==city }.map{it.id}.toSet()
        return entryDao.getAllSync().firstOrNull { it.productId==productId && !it.isBought && (it.assignedShopId==null || it.assignedShopId in shops) }
    }
    suspend fun adjustQuantity(id: Long, delta: Double) = db.withTransaction {
        require(delta.isFinite())
        val entry=entryDao.getById(id) ?: return@withTransaction
        if(entry.isBought)return@withTransaction
        val quantity=entry.quantity+delta
        if(quantity<=0.0000001)entryDao.deleteById(id)
        else entryDao.update(entry.copy(quantity=Quantities.valid(quantity),updatedAt=System.currentTimeMillis()))
        db.productDao().deleteUnusedTemporary()
    }
    suspend fun updateQuantityByProductId(productId: Long, newQuantity: Double): Boolean {
        val entry=findActiveByProductId(productId) ?: return false
        if(newQuantity<=0)removeEntry(entry.id) else updateQuantity(entry.id,newQuantity)
        return true
    }
    suspend fun updateQuantity(id: Long, quantity: Double)=db.withTransaction {
        val entry=entryDao.getById(id) ?: return@withTransaction
        entryDao.update(entry.copy(quantity=Quantities.valid(quantity),updatedAt=System.currentTimeMillis()))
    }
    suspend fun removeByProductId(productId: Long): Boolean { val e=findActiveByProductId(productId) ?: return false;removeEntry(e.id);return true }
    suspend fun updateEntry(entry: ShoppingListEntryEntity)=db.withTransaction { validateTarget(entry);entryDao.update(entry.copy(quantity=Quantities.valid(entry.quantity))) }
    suspend fun removeEntry(id: Long)=db.withTransaction { entryDao.deleteById(id);db.productDao().deleteUnusedTemporary() }
    suspend fun markBought(id: Long)=entryDao.markBought(id)
    suspend fun markNotBought(id: Long)=entryDao.markNotBought(id)
    suspend fun toggleUrgent(id: Long)=entryDao.toggleUrgency(id)
    suspend fun clearBoughtEntries()=db.withTransaction {entryDao.clearBought();db.productDao().deleteUnusedTemporary()}
    suspend fun clearBoughtByShop(shopId: Long)=db.withTransaction {entryDao.clearBoughtByShop(shopId);db.productDao().deleteUnusedTemporary()}
    suspend fun clearBoughtWithoutShop()=db.withTransaction {entryDao.clearBoughtWithoutShop();db.productDao().deleteUnusedTemporary()}
    suspend fun movePendingToCurrentCity() {
        val city=locations.currentId()
        db.withTransaction {
            val shops=db.shopDao().getAllSync().associateBy{it.id}
            entryDao.getAllSync().filter { !it.isBought && it.assignedShopId!=null && shops[it.assignedShopId]?.cityId!=city }.forEach { entry ->
                val target=resolveShop(entry.productId,city,ShopChoice.AUTO,null)
                entryDao.update(entry.copy(assignedShopId=target.first,assignedDepartmentId=target.second,updatedAt=System.currentTimeMillis()))
            }
        }
    }
    data class PurchaseRequest(val productId:Long?,val name:String,val quantity:Double,val unit:String,val permanent:Boolean=false,val choice:ShopChoice=ShopChoice.AUTO,val shopId:Long?=null)
    data class BatchReceipt(val before:Map<Long,ShoppingListEntryEntity>,val after:Map<Long,ShoppingListEntryEntity>,val count:Int)
    suspend fun addBatch(requests:List<PurchaseRequest>,expectedCityId:Long):BatchReceipt {
        require(requests.isNotEmpty()) { "Выберите хотя бы одну строку" }
        require(requests.size<=200)
        require(locations.currentId()==expectedCityId) { "Город изменился. Проверьте список ещё раз" }
        return db.withTransaction {
            val before=entryDao.getAllSync().associateBy{it.id};val affected=mutableSetOf<Long>()
            val created=mutableMapOf<Triple<String,String,Boolean>,Long>()
            requests.forEach{request->
                val key=Triple(FuzzySearch.normalize(request.name),Quantities.unit(request.unit),request.permanent)
                val existing=request.productId ?: created[key]
                val id=if(existing!=null)addProduct(existing,request.quantity,request.unit,request.choice,request.shopId)
                else addNewProduct(request.name,request.quantity,request.unit,request.permanent,request.choice,request.shopId).also{entryId->created[key]=entryDao.getById(entryId)!!.productId}
                affected.add(id)
            }
            BatchReceipt(before.filterKeys{it in affected},entryDao.getAllSync().filter{it.id in affected}.associateBy{it.id},requests.size)
        }
    }
    suspend fun undoBatch(receipt:BatchReceipt)=db.withTransaction {
        receipt.after.forEach{(id,after)->require(entryDao.getById(id)==after){"Покупки уже изменены; автоматическая отмена недоступна"}}
        receipt.after.keys.forEach{id->val old=receipt.before[id];if(old==null)entryDao.deleteById(id)else entryDao.update(old)}
        db.productDao().deleteUnusedTemporary()
    }
}
