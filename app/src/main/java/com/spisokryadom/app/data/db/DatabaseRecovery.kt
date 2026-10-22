package com.spisokryadom.app.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

/** One private recovery checkpoint before the first schema upgrade, including media.
 * This runs before Room opens any connection. An incomplete copy is never marked ready. */
object DatabaseRecovery {
 fun preserveBeforeUpgrade(context:Context,targetVersion:Int) {
  val source=context.getDatabasePath("spisok_ryadom.db")
  if(!source.isFile)return
  val version=SQLiteDatabase.openDatabase(source.path,null,SQLiteDatabase.OPEN_READONLY).use{it.version}
  if(version>=targetVersion)return
  val destination=File(context.filesDir,"recovery/schema-$version-before-$targetVersion")
  val ready=File(destination,"READY")
  if(ready.isFile)return
  destination.mkdirs()
  listOf("","-wal","-shm").forEach{suffix->File(source.path+suffix).takeIf{it.isFile}?.copyTo(File(destination,source.name+suffix),overwrite=true)}
  listOf("product_photos","branch_photos","shop_logos","venue_photos").forEach{name->
   File(context.filesDir,name).takeIf{it.isDirectory}?.copyRecursively(File(destination,name),overwrite=true)
  }
  val preferences=File(context.applicationInfo.dataDir,"shared_prefs")
  if(preferences.isDirectory)preferences.copyRecursively(File(destination,"shared_prefs"),overwrite=true)
  ready.writeText("Schema $version before $targetVersion. Original database, WAL, media and preferences. Restore only with the application stopped.")
 }
}
