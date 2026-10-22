package com.spisokryadom.app

// Keep Room open until repository observers have removed their invalidation callbacks.
fun com.spisokryadom.app.data.repository.LocationRepository.closeForTest() =
    kotlinx.coroutines.runBlocking { closeAndJoin() }

/** Fixtures use the same writer as AddToListScreen, with their original explicit no-shop choice. */
internal suspend fun com.spisokryadom.app.data.repository.ShoppingListRepository.addGoalCatalogueFixture(
    listId:Long,productId:Long,quantity:Double?=null,unit:String?=null,plannedUnitPrice:Double?=null,
    choice:com.spisokryadom.app.data.repository.ShopChoice=com.spisokryadom.app.data.repository.ShopChoice.NONE,
    shopId:Long?=null,branchId:Long?=null
):Long=addProduct(productId,quantity,unit,choice,shopId,branchId,listId,plannedUnitPrice)

/** Exercise the production card save instead of maintaining a second public favorite command. */
internal suspend fun com.spisokryadom.app.data.repository.ShopRepository.saveFavoriteFixture(id:Long,value:Boolean){
    val shop=getShopById(id)?:error("Магазин удалён")
    saveWithDepartments(shop.copy(isFavorite=value),getDepartmentsByShopIdSync(id))
}
