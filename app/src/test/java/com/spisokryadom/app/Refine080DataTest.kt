package com.spisokryadom.app

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.db.*
import com.spisokryadom.app.data.backup.*
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.ui.components.*
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35],application=Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class Refine080DataTest {
 private val app get()=ApplicationProvider.getApplicationContext<Application>()
 private lateinit var db:AppDatabase
 private lateinit var backup:BackupManager
 @Before fun setup(){db=Room.inMemoryDatabaseBuilder(app,AppDatabase::class.java).allowMainThreadQueries().build();RetailConstraints.install(db.openHelper.writableDatabase);backup=BackupManager(app,db)}
 @After fun close(){db.close()}
 private fun fixture(name:String)=File(app.cacheDir,name).also{file->javaClass.getResourceAsStream("/$name")!!.use{input->file.outputStream().use{input.copyTo(it)}}}
 @Test fun syntheticCatalogueRetainsAllFactsAndOnlyRequestedDictionaryChanges()=runBlocking{
  val before=fixture("synthetic-backup-before-080.zip");val after=fixture("synthetic-backup-after-080.zip")
  backup.importFile(before)
  val products=db.productDao().getAllSync().associateBy{it.id};val rows=db.shoppingListEntryDao().getAllSync().associateBy{it.id};val links=db.productShopLinkDao().getAllSync().associateBy{it.id}
  val shops=db.shopDao().getAllSync().associateBy{it.id};val branches=db.shopBranchDao().getAll();val available=db.shopBranchDao().allAvailability()
  assertEquals(120,products.size);assertEquals(21,rows.size);assertEquals(134,links.size)
  backup.importFile(after)
  assertEquals(products,db.productDao().getAllSync().associateBy{it.id});assertEquals(rows,db.shoppingListEntryDao().getAllSync().associateBy{it.id});assertEquals(links,db.productShopLinkDao().getAllSync().associateBy{it.id})
  assertEquals(shops,db.shopDao().getAllSync().associateBy{it.id});assertEquals(branches,db.shopBranchDao().getAll());assertEquals(available,db.shopBranchDao().allAvailability())
  assertEquals(21,db.shopDepartmentDao().types().size);assertEquals(47,db.shopDepartmentDao().getAllSync().size);assertEquals(12,db.unitDao().getAllSync().size)
  assertTrue(db.shopDepartmentDao().getByShopIdSync(12).isEmpty());assertTrue(db.shopDepartmentDao().getByShopIdSync(11).isEmpty())
  assertTrue(db.shopDepartmentDao().getByShopIdSync(8).any{it.name=="Заморозка"})
  assertEquals("canned",db.shopDepartmentDao().types().single{it.name=="Консервы"}.iconKey);assertEquals("nuts",db.shopDepartmentDao().types().single{it.name=="Орехи"}.iconKey)
  db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use{assertFalse(it.moveToFirst())}
  val exported=backup.exportFile();assertTrue(backup.inspect(exported).full);backup.importFile(exported);assertEquals(rows,db.shoppingListEntryDao().getAllSync().associateBy{it.id});backup.discard(exported)
 }
 @Test fun arbitraryRgbColorsAndNewIconSurviveFullBackup()=runBlocking{
  backup.importFile(fixture("synthetic-backup-after-080.zip"))
  val type=db.shopDepartmentDao().types().single{it.name=="Консервы"};db.shopDepartmentDao().updateType(type.copy(accent="#12ABEF",iconKey="canned"))
  val group=db.productGroupDao().getAllSync().first();db.productGroupDao().update(group.copy(accent="#FA51C8",iconKey="cheese"))
  val product=db.productDao().getAllSync().first();db.productDao().update(product.copy(accent="#010203"))
  val saved=backup.exportFile();db.shopDepartmentDao().updateType(type);backup.importFile(saved)
  assertEquals("#12ABEF",db.shopDepartmentDao().type(type.id)!!.accent);assertEquals("canned",db.shopDepartmentDao().type(type.id)!!.iconKey)
  assertEquals("#FA51C8",db.productGroupDao().getById(group.id)!!.accent);assertEquals("#010203",db.productDao().getById(product.id)!!.accent)
  backup.discard(saved)
 }
 @Test fun colorAndIconVocabularyAcceptsLegacyAndCustomValues(){
  assertEquals(0xFF12ABEF.toInt(),accentColor("#12abef")!!.toArgb());assertNotNull(accentColor("green"));assertNull(accentColor("#GG1122"));assertNull(accentColor("#80123456"))
  val all=appearanceOptions();assertEquals(60,all.size);assertEquals(60,all.map{it.first}.toSet().size)
  all.forEach{assertNotNull("Missing icon ${it.first}",appearanceIcon(it.first))}
  assertNotNull(appearanceIcon("canned"));assertNotNull(appearanceIcon("nuts"));assertNotNull(appearanceIcon("dental"));assertNull(appearanceIcon("unknown-future-icon"))
 }
}
