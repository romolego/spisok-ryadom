package com.spisokryadom.app

import android.app.Application
import android.content.SharedPreferences
import android.os.Looper
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.spisokryadom.app.data.backup.BackupManager
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.db.RetailConstraints
import com.spisokryadom.app.data.entity.CityEntity
import com.spisokryadom.app.data.entity.ProductEntity
import com.spisokryadom.app.data.repository.MediaLifecycle
import com.spisokryadom.app.util.PhotoStore
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.annotation.SQLiteMode
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,35],application=Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class AuditStorage071Test {
 private val app get()=ApplicationProvider.getApplicationContext<Application>()
 private lateinit var db:AppDatabase
 private lateinit var backup:BackupManager
 private var product=0L
 private val names=listOf("location_prefs","shopping_list_prefs","product_db_prefs")
 @Before fun setup()=runBlocking {
  names.forEach{app.getSharedPreferences(it,0).edit().clear().commit()}
  db=Room.inMemoryDatabaseBuilder(app,AppDatabase::class.java).allowMainThreadQueries().build()
  RetailConstraints.install(db.openHelper.writableDatabase)
  db.cityDao().insert(CityEntity(1,"First"));db.cityDao().insert(CityEntity(2,"Second"))
  product=db.productDao().insert(ProductEntity(name="Original",isFavorite=true))
  app.getSharedPreferences("location_prefs",0).edit().putLong("active_city",2).commit()
  app.getSharedPreferences("product_db_prefs",0).edit().putBoolean("favorites_only",true).putStringSet("collapsed_sections",setOf("group_7")).commit()
  app.getSharedPreferences("shopping_list_prefs",0).edit().putBoolean("global_default_show_urgent_in_collapsed",false).commit()
  backup=BackupManager(app,db)
 }
 @After fun close(){db.close()}
 private fun manifest(file:File)=ZipFile(file).use{z->JSONObject(z.getInputStream(z.getEntry("manifest.json")).reader().readText())}
 private fun archive(root:JSONObject):File=File(app.cacheDir,"audit_storage_${UUID.randomUUID()}.zip").also{file->
  ZipOutputStream(file.outputStream()).use{zip->zip.putNextEntry(ZipEntry("manifest.json"));zip.write(root.toString().toByteArray(Charsets.UTF_8));zip.closeEntry()}
 }
 private suspend fun originalManifest()=backup.exportFile().let{file->try{manifest(file)}finally{backup.discard(file)}}
 @Test fun malformedPreferencesAreRejectedBeforeDatabaseOrSettingsReplacement()=runBlocking {
  val source=originalManifest().toString()
  val original=db.productDao().getById(product)
  val before=names.associateWith{app.getSharedPreferences(it,0).all.toMap()}
  val changes=listOf<(JSONObject)->Unit>(
   {it.getJSONObject("preferences").getJSONObject("location_prefs").put("active_city","2")},
   {it.getJSONObject("preferences").getJSONObject("location_prefs").put("active_city",2.5)},
   {it.getJSONObject("preferences").getJSONObject("location_prefs").put("active_city",999)},
   {it.getJSONObject("preferences").getJSONObject("product_db_prefs").put("favorites_only","true")},
   {it.getJSONObject("preferences").getJSONObject("product_db_prefs").put("collapsed_sections",JSONArray().put("group_7").put(7))},
   {it.getJSONObject("preferences").getJSONObject("product_db_prefs").put("shop_points_4",true)},
   {it.getJSONObject("preferences").getJSONObject("shopping_list_prefs").put("shopping_list_view_mode",false)},
   {it.getJSONObject("preferences").getJSONObject("shopping_list_prefs").put("global_default_show_urgent_in_collapsed","false")},
   {it.getJSONObject("preferences").put("product_db_prefs",JSONArray())},
   {it.getJSONObject("preferences").getJSONObject("product_db_prefs").put("unknown_object",JSONObject().put("x",1))},
   {it.put("preferences",JSONArray())}
  )
  changes.forEachIndexed{index,change->
   val root=JSONObject(source);root.getJSONObject("data").getJSONArray("products").getJSONObject(0).put("name","Replacement")
   change(root);val file=archive(root)
   try{backup.importFile(file);fail("Invalid preference case $index must be rejected")}
   catch(expected:IllegalArgumentException){}
   finally{file.delete()}
   shadowOf(Looper.getMainLooper()).idle()
   assertEquals("Database changed in case $index",original,db.productDao().getById(product))
   assertEquals("Preferences changed in case $index",before,names.associateWith{app.getSharedPreferences(it,0).all.toMap()})
  }
 }
 @Test fun restoringMissingNewKeysNotifiesTheirRemovalOnBothAndroidVersions()=runBlocking {
  val root=originalManifest()
  names.forEach{root.getJSONObject("preferences").put(it,JSONObject())}
  val notifications=mutableSetOf<String>()
  val listeners=names.associateWith{name->SharedPreferences.OnSharedPreferenceChangeListener{_,key->if(key!=null)notifications.add("$name/$key")}}
  listeners.forEach{(name,listener)->app.getSharedPreferences(name,0).registerOnSharedPreferenceChangeListener(listener)}
  val file=archive(root)
  try {
   backup.importFile(file);shadowOf(Looper.getMainLooper()).idle()
   assertTrue(app.getSharedPreferences("product_db_prefs",0).all.isEmpty())
   assertTrue(app.getSharedPreferences("shopping_list_prefs",0).all.isEmpty())
   assertTrue(app.getSharedPreferences("location_prefs",0).all.isEmpty())
   assertTrue(notifications.containsAll(setOf("location_prefs/active_city","product_db_prefs/favorites_only","product_db_prefs/collapsed_sections","shopping_list_prefs/global_default_show_urgent_in_collapsed")))
  }finally{listeners.forEach{(name,listener)->app.getSharedPreferences(name,0).unregisterOnSharedPreferenceChangeListener(listener)};file.delete()}
 }
 @Test fun olderFullCopyWithoutPreferencesRemainsImportable()=runBlocking {
  val root=originalManifest().put("version",7);root.remove("preferences")
  val row=root.getJSONObject("data").getJSONArray("products").getJSONObject(0)
  row.put("name","Restored old copy");row.remove("isFavorite");row.remove("accent")
  val file=archive(root)
  try{assertTrue(backup.importFile(file).full);assertEquals("Restored old copy",db.productDao().getById(product)?.name);assertFalse(db.productDao().getById(product)!!.isFavorite)}finally{file.delete()}
  assertEquals(2L,app.getSharedPreferences("location_prefs",0).getLong("active_city",1))
 }
 @Test fun olderCopyWithoutSelectionCanBeExportedAgainWhenCityIdsChanged()=runBlocking {
  val root=originalManifest();root.remove("preferences")
  root.getJSONObject("data").put("cities",JSONArray().put(JSONObject().put("id",1).put("name","Only restored city")))
  val file=archive(root)
  try{backup.importFile(file)}finally{file.delete()}
  assertEquals(2L,app.getSharedPreferences("location_prefs",0).getLong("active_city",1))
  val exported=backup.exportFile()
  try {
   assertEquals(1L,manifest(exported).getJSONObject("preferences").getJSONObject("location_prefs").getLong("active_city"))
   assertTrue(backup.importFile(exported).full)
   assertEquals(1L,app.getSharedPreferences("location_prefs",0).getLong("active_city",2))
  }finally{backup.discard(exported)}
 }
 @Test fun longRunningImportKeepsAgedStagedPhotosUntilCommitAndReleasesDirectoryLease()=runBlocking {
  val photos=File(app.filesDir,"product_photos").also{it.mkdirs()}
  val bytes=byteArrayOf(9,8,7,6)
  val original=File(photos,"source_${UUID.randomUUID()}.jpg").also{it.writeBytes(bytes)}
  val entity=db.productDao().getById(product)!!
  db.productDao().update(entity.copy(photoUri=original.absolutePath))
  val archive=backup.exportFile()
  val previous=photos.listFiles().orEmpty().map{it.absolutePath}.toSet()
  val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
  val blocker=async(Dispatchers.IO){db.withTransaction{entered.complete(Unit);release.await()}}
  try {
   withTimeout(10_000){entered.await()}
   val restoring=async(Dispatchers.IO){backup.importFile(archive)}
   try {
    val staged=withTimeout(10_000){
     var found:File?=null
     while(found==null){
      found=photos.listFiles().orEmpty().filter{it.isDirectory&&it.name.startsWith("import_")&&it.absolutePath !in previous}
       .map{File(it,"${entity.stableId}.jpg")}.firstOrNull{it.isFile&&it.length()==bytes.size.toLong()}
      if(found==null)delay(10)
     }
     found
    }
    assertTrue(staged.setLastModified(System.currentTimeMillis()-600_000))
    // This is cleanup's filesystem phase with the snapshot taken before import.
    // The held Room transaction guarantees that the stage is not committed yet.
    MediaLifecycle.mutex.withLock{PhotoStore.cleanup(app,emptySet())}
    assertTrue("Cleanup removed an active import photograph",staged.isFile)
    assertFalse("The database transaction gate must still block import",restoring.isCompleted)
    release.complete(Unit);blocker.await()
    assertTrue(withTimeout(10_000){restoring.await()}.full)
    val restored=File(db.productDao().getById(product)!!.photoUri!!)
    assertEquals(staged.absolutePath,restored.absolutePath);assertArrayEquals(bytes,restored.readBytes())
    val orphan=File(staged.parentFile,"unused_after_import.jpg").also{it.writeBytes(bytes);assertTrue(it.setLastModified(System.currentTimeMillis()-600_000))}
    MediaLifecycle.cleanup(app,db)
    assertTrue("Committed media must be retained",restored.isFile)
    assertFalse("Successful import left its directory leased",orphan.exists())
    assertArrayEquals(bytes,restored.readBytes())
   }finally{withContext(NonCancellable){release.complete(Unit);restoring.cancelAndJoin()}}
  }finally{withContext(NonCancellable){release.complete(Unit);blocker.await();backup.discard(archive)}}
 }
 @Test fun validIntegerCityAndStringSetsRoundTripWithoutTypeCoercion()=runBlocking {
  val root=originalManifest()
  root.getJSONObject("preferences").getJSONObject("location_prefs").put("active_city",1)
  root.getJSONObject("preferences").getJSONObject("product_db_prefs").put("favorites_only",false).put("product_db_view_mode","BY_GROUPS").put("shop_points_9",JSONArray().put("point_3"))
  val file=archive(root)
  try{backup.importFile(file);shadowOf(Looper.getMainLooper()).idle()}finally{file.delete()}
  assertEquals(1L,app.getSharedPreferences("location_prefs",0).getLong("active_city",2))
  val prefs=app.getSharedPreferences("product_db_prefs",0)
  assertFalse(prefs.getBoolean("favorites_only",true));assertEquals("BY_GROUPS",prefs.getString("product_db_view_mode",null));assertEquals(setOf("point_3"),prefs.getStringSet("shop_points_9",emptySet()))
 }
}
