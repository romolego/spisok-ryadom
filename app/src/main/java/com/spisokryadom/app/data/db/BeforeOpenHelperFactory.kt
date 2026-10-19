package com.spisokryadom.app.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory

/** Defer recovery I/O until Room opens on its query/transaction executor.
 * Constructing the Application/container never copies photos on the UI thread. */
class BeforeOpenHelperFactory(private val beforeOpen:()->Unit):SupportSQLiteOpenHelper.Factory {
 override fun create(configuration:SupportSQLiteOpenHelper.Configuration):SupportSQLiteOpenHelper {
  val delegate=FrameworkSQLiteOpenHelperFactory().create(configuration)
  return object:SupportSQLiteOpenHelper by delegate {
   @Volatile private var prepared=false
   private val lock=Any()
   private fun prepare(){
    if(prepared)return
    synchronized(lock){if(!prepared){beforeOpen();prepared=true}}
   }
   override val writableDatabase:SupportSQLiteDatabase get(){prepare();return delegate.writableDatabase}
   override val readableDatabase:SupportSQLiteDatabase get(){prepare();return delegate.readableDatabase}
  }
 }
}
