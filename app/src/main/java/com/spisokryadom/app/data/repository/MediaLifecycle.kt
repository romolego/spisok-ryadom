package com.spisokryadom.app.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.util.PhotoStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Immutable file names + leases let ZIP I/O run without holding a database write transaction. */
object MediaLifecycle {
 val mutex=Mutex()
 suspend fun cleanup(context:Context,db:AppDatabase)=mutex.withLock {
  val retained=db.withTransaction {
   (db.productDao().getAllSync().mapNotNull{it.photoUri}+db.shopBranchDao().getAll().mapNotNull{it.photoUri}).toSet()
  }
  PhotoStore.cleanup(context,retained)
 }
}
