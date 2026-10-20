package com.spisokryadom.app.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.db.StandardCategories
import com.spisokryadom.app.data.entity.CityEntity
import com.spisokryadom.app.util.DatabaseRevision
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import java.io.File

enum class ResetMode(val title:String,val explanation:String) {
 BUILT_INS("Восстановить готовые группы","Вернёт встроенным товарным группам и группам магазинов исходные названия, значки и оформление, добавит недостающие. Пользовательские значения, товары, покупки и связи сохранятся."),
 DICTIONARIES("Только исходные выпадающие списки","Удалит пользовательские группы, получателей и единицы из справочников и снимет их связи. Товары, магазины, покупки и записанные в покупках количества/единицы сохранятся. Готовые группы вернутся к исходному составу."),
 APPLICATION("Сбросить всё приложение","Удалит все товары, магазины, покупки, целевые списки, фотографии, внутренние резервные копии и настройки. Приложение станет как при первом запуске. Файлы, ранее сохранённые вне приложения, не затрагиваются.")
}

class ResetRepository(private val context:Context,private val db:AppDatabase,private val edits:CatalogueEdits?=null) {
 suspend fun reset(mode:ResetMode)=MediaLifecycle.mutex.withLock { ProductEditorTransactions.locked { withContext(NonCancellable) {
  withCatalogueEdits(db,edits){db.withTransaction {
   if(mode==ResetMode.APPLICATION){
    db.shoppingListEntryDao().deleteAll();db.goalListDao().deleteAll();db.productShopLinkDao().deleteAll();db.shopDepartmentDao().deleteAll()
    db.productDao().deleteAll();db.shopDao().deleteAll();db.productGroupDao().deleteAll();db.recipientDao().deleteAll();db.unitDao().deleteAll();db.shopDepartmentDao().clearTypes()
    db.retailStructureDao().clearVenues();db.retailStructureDao().clearNetworks();db.cityDao().deleteAll();db.cityDao().insert(CityEntity(1,"Мой город"))
   }else if(mode==ResetMode.DICTIONARIES){
    db.shopDepartmentDao().deleteAll();db.shopDepartmentDao().clearTypes();db.productGroupDao().deleteAll();db.recipientDao().deleteAll();db.unitDao().deleteAll()
   }
   StandardCategories.install(db.openHelper.writableDatabase)
   if(mode==ResetMode.BUILT_INS)restoreBuiltIns()
  }}
  if(mode!=ResetMode.BUILT_INS){ProductEditorTransactions.generation.incrementAndGet();DatabaseRevision.invalidate(context)}
  if(mode==ResetMode.APPLICATION){
   listOf("shopping_list_prefs","product_db_prefs","location_prefs","product_card_drafts","spisok_ryadom_prefs","drafts").forEach{name->
    val prefs=context.getSharedPreferences(name,0);val edit=prefs.edit();prefs.all.keys.forEach{edit.remove(it)};check(edit.commit())
   }
   listOf("product_photos","branch_photos","shop_logos","recovery").forEach{removeOwnedDirectory(context.filesDir,it)}
   listOf("backups","goal-list-messages","goal-list-shares").forEach{removeOwnedDirectory(context.cacheDir,it)}
  }
 } } }
 private suspend fun restoreBuiltIns(){
  val groups=db.productGroupDao().getAllSync();val types=db.shopDepartmentDao().types()
  fun same(value:String,category:StandardCategories.Category)=com.spisokryadom.app.util.FuzzySearch.normalize(value) in (category.aliases+category.name).map(com.spisokryadom.app.util.FuzzySearch::normalize)
  StandardCategories.groups.forEachIndexed{i,c->val g=groups.firstOrNull{it.name==c.name}?:groups.firstOrNull{same(it.name,c)}?:return@forEachIndexed
   db.productGroupDao().update(g.copy(name=c.name,iconKey=c.icon,accent="",isFavorite=false,favoriteOrder=0,displayOrder=i))}
  StandardCategories.departments.forEachIndexed{i,c->val t=types.firstOrNull{it.name==c.name}?:types.firstOrNull{same(it.name,c)}?:return@forEachIndexed
   db.shopDepartmentDao().updateType(t.copy(name=c.name,normalizedName=com.spisokryadom.app.util.FuzzySearch.normalize(c.name),iconKey=c.icon,accent="",isFavorite=false,favoriteOrder=0,displayOrder=i))}
 }
 private fun removeOwnedDirectory(root:File,name:String){val parent=root.canonicalFile;val target=File(parent,name).canonicalFile;require(target.parentFile==parent);check(!target.exists()||target.deleteRecursively()){ "Не удалось удалить внутренние файлы" }}
}
