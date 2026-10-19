package com.spisokryadom.app.data.repository

import com.spisokryadom.app.data.db.AppDatabase

/** Optional boundary for synchronizing durable editor drafts after an external catalogue command. */
interface CatalogueEdits {
 suspend fun <T> change(db:AppDatabase,block:suspend()->T):T
}
suspend fun <T> withCatalogueEdits(db:AppDatabase,edits:CatalogueEdits?,block:suspend()->T):T =
 if(edits==null)block()else edits.change(db,block)
