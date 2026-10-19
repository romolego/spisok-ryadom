package com.spisokryadom.app.ui.productcard

import android.content.SharedPreferences
import androidx.room.withTransaction
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.*
import com.spisokryadom.app.util.FuzzySearch
import com.spisokryadom.app.util.Quantities
import kotlinx.coroutines.*
import kotlin.coroutines.*

/** Applies only the canonical fields changed by a successful external command.
 * Invalid, unrelated raw input stays intact. DB and draft completion share the editor lock. */
class ProductDraftSynchronizer(private val preferences:SharedPreferences):CatalogueEdits {
 private class Active(val db:AppDatabase):AbstractCoroutineContextElement(Key)
 private object Key:CoroutineContext.Key<Active>
 override suspend fun <T> change(db:AppDatabase,block:suspend()->T):T {
  if(currentCoroutineContext()[Key]?.db===db)return block()
  return ProductEditorTransactions.locked {
   withContext(Dispatchers.IO+NonCancellable+Active(db)){
    val before=db.withTransaction{read(db)}
    val result=block()
    val after=db.withTransaction{read(db)}
    reconcile(before,after)
    result
   }
  }
 }
 private data class State(val products:List<ProductEntity>,val links:List<ProductShopLinkEntity>,val shops:List<ShopEntity>,val branches:List<ShopBranchEntity>,val availability:List<ProductBranchAvailabilityEntity>,val priorities:List<ProductBranchPriorityEntity>,val overrides:List<ProductBranchDepartmentEntity>,val departments:List<ShopDepartmentEntity>,val types:List<DepartmentTypeEntity>,val recipients:Set<Long>,val groups:Set<Long>,val units:List<UnitEntity>)
 private suspend fun read(db:AppDatabase)=State(db.productDao().getAllSync(),db.productShopLinkDao().getAllSync(),db.shopDao().getAllSync(),db.shopBranchDao().getAll(),db.shopBranchDao().allAvailability(),db.shopBranchDao().priorities(),db.retailStructureDao().productDepartments(),db.shopDepartmentDao().getAllSync(),db.shopDepartmentDao().types(),db.recipientDao().getAllSync().map{it.id}.toSet(),db.productGroupDao().getAllSync().map{it.id}.toSet(),db.unitDao().getAllSync())
 private fun reconcile(before:State,after:State){
  val previous=before.products.associateBy{it.stableId};val current=after.products.associateBy{it.stableId}
  val shops=after.shops.associateBy{it.id};val branches=after.branches.associateBy{it.id};val departments=after.departments.associateBy{it.id}
  val edit=preferences.edit();var changed=false
  preferences.all.forEach{(key,raw)->
   val draft=runCatching{ProductEditDraft.decode(raw as String)}.getOrNull()?:return@forEach
   val old=previous[draft.stableId];val p=current[draft.stableId]
   if(old!=null&&p==null){edit.remove(key);changed=true;return@forEach}
   var state=draft.state
   if(old!=null&&p!=null){
    state=state.copy(
     name=if(old.name!=p.name)p.name else state.name,
     recipientId=if(old.recipientId!=p.recipientId&&state.recipientId==old.recipientId)p.recipientId else state.recipientId,
     productGroupId=if(old.productGroupId!=p.productGroupId&&state.productGroupId==old.productGroupId)p.productGroupId else state.productGroupId,
     defaultUnit=if(old.defaultUnit!=p.defaultUnit&&Quantities.unit(state.defaultUnit)==Quantities.unit(old.defaultUnit.orEmpty()))p.defaultUnit.orEmpty()else state.defaultUnit,
     isFavorite=if(old.isFavorite!=p.isFavorite)p.isFavorite else state.isFavorite,
     accent=if(old.accent!=p.accent)p.accent else state.accent,
     preferCityShopOrder=if(old.preferCityShopOrder!=p.preferCityShopOrder)p.preferCityShopOrder else state.preferCityShopOrder)
    val linksBefore=before.links.filter{it.productId==p.id}.associateBy{it.shopId};val linksAfter=after.links.filter{it.productId==p.id}.associateBy{it.shopId}
    val orderChanged=old.preferCityShopOrder!=p.preferCityShopOrder||linksBefore.any{(shop,link)->linksAfter[shop]?.let{it.priority!=link.priority}==true}
    val removed=linksBefore.keys-linksAfter.keys
    val ui=state.shopLinks.filter{it.link.shopId !in removed&&it.link.shopId in shops}.toMutableList()
    val pending=state.pendingShopLinks.filter{it.shopId !in removed&&it.shopId in shops}.toMutableList()
    val points=state.branchSelections.toMutableMap();val priorities=state.branchPriorities.toMutableMap();val overrides=state.branchDepartments.toMutableMap()
    linksAfter.forEach{(shop,next)->
     val prior=linksBefore[shop]
     val i=ui.indexOfFirst{it.link.shopId==shop};val j=pending.indexOfFirst{it.shopId==shop}
     val rawLink=ui.getOrNull(i)?.link?:pending.getOrNull(j)?.let{ProductShopLinkEntity(productId=p.id,shopId=shop,departmentId=it.departmentId,priority=it.displayOrder,restrictBranches=shop in points)}
     // A newly materialized canonical link may already be configured in an
     // unfinished raw card. Plain link addition must not erase those choices.
     fun patch(link:ProductShopLinkEntity)=if(prior==null&&rawLink!=null)link.copy(priority=if(orderChanged)next.priority else link.priority)else link.copy(departmentId=if(prior==null||(prior.departmentId!=next.departmentId&&link.departmentId==prior.departmentId))next.departmentId else link.departmentId,priority=if(prior==null||prior.priority!=next.priority)next.priority else link.priority,restrictBranches=if(prior==null||prior.restrictBranches!=next.restrictBranches)next.restrictBranches else link.restrictBranches)
     if(prior==null){
      val added=patch(rawLink?:next)
      if(i<0)ui.add(ShopLinkUi(added,shops[shop]?.name.orEmpty(),departments[added.departmentId]?.name))
      if(j<0)pending.add(PendingShopLink(-shop,shop,shops[shop]?.name.orEmpty(),added.departmentId,departments[added.departmentId]?.name,added.priority))
     }
     if(i>=0){val value=patch(ui[i].link);ui[i]=ui[i].copy(link=value,shopName=shops[shop]?.name.orEmpty(),departmentName=departments[value.departmentId]?.name)}
     if(j>=0){val value=patch(ProductShopLinkEntity(productId=p.id,shopId=shop,departmentId=pending[j].departmentId,priority=pending[j].displayOrder));pending[j]=pending[j].copy(departmentId=value.departmentId,displayOrder=value.priority,shopName=shops[shop]?.name.orEmpty(),departmentName=departments[value.departmentId]?.name)}
     val a=before.availability.filter{it.productId==p.id&&it.shopId==shop}.map{it.branchId}.toSet();val b=after.availability.filter{it.productId==p.id&&it.shopId==shop}.map{it.branchId}.toSet()
     if(prior==null||prior.restrictBranches!=next.restrictBranches||a!=b){
      if(prior==null&&rawLink!=null){if(next.restrictBranches&&points.containsKey(shop))points[shop]=points.getValue(shop)+b}
      else if(!next.restrictBranches)points.remove(shop)
      else if(prior?.restrictBranches!=true)points[shop]=b
      else if(points.containsKey(shop))points[shop]=points.getValue(shop)-(a-b)+(b-a)
     }
     fun order(s:State)=s.priorities.filter{it.productId==p.id&&it.shopId==shop}.sortedBy{it.displayOrder}.map{it.branchId}
     if(order(before)!=order(after))priorities[shop]=order(after)
    }
    val a=before.overrides.filter{it.productId==p.id}.associateBy{it.branchId};val b=after.overrides.filter{it.productId==p.id}.associateBy{it.branchId}
    (a.keys+b.keys).forEach{branch->if(a[branch]!=b[branch]){
     val value=b[branch]?.let{row->after.departments.find{it.shopId==row.shopId&&it.typeId==row.typeId}?.id}
     if(value==null)overrides.remove(branch)else overrides[branch]=value
    }}
    state=state.copy(shopLinks=ui,pendingShopLinks=pending,branchSelections=points.filterKeys{it !in removed},branchPriorities=priorities.filterKeys{it !in removed},branchDepartments=overrides)
   }
   // Deleted references must also be removed from raw-only choices. A resolved
   // negative draft department must not recreate a type deliberately deleted later.
   val oldPlacements=before.departments.associateBy{it.shopId to it.typeId}
   val types=after.types.map{it.id}.toSet();val deletedNames=before.types.filter{it.id !in types}.map{FuzzySearch.normalize(it.name)}.toSet()
   val pendingDepartments=state.pendingDepartments.mapNotNull{dep->
    val oldPlacement=if(dep.typeId>0)oldPlacements[dep.shopId to dep.typeId]else before.departments.find{it.shopId==dep.shopId&&FuzzySearch.normalize(it.name)==FuzzySearch.normalize(dep.name)}
    if(dep.shopId !in shops||(dep.typeId>0&&dep.typeId !in types)||FuzzySearch.normalize(dep.name)in deletedNames||(oldPlacement!=null&&oldPlacement.id !in departments))null
    else oldPlacement?.let{departments[it.id]}?.let{dep.copy(name=it.name,typeId=it.typeId)}?:dep
   }
   val validDrafts=pendingDepartments.map{it.id}.toSet()
   fun validDepartment(id:Long?)=id?.takeIf{it in departments||it in validDrafts}
   fun departmentName(id:Long?)=departments[id]?.name?:pendingDepartments.find{it.id==id}?.name
   val links=state.shopLinks.filter{it.link.shopId in shops}.map{val id=validDepartment(it.link.departmentId);it.copy(link=it.link.copy(departmentId=id),departmentName=departmentName(id))}
   val pending=state.pendingShopLinks.filter{it.shopId in shops}.map{val id=validDepartment(it.departmentId);it.copy(departmentId=id,departmentName=departmentName(id))}
   val linkShops=(links.map{it.link.shopId}+pending.map{it.shopId}).toSet()
   // The presence of an empty set means an unfinished restricted choice. Removing
   // its key would silently allow every remaining point on the next recovery.
   val points=state.branchSelections.filterKeys{it in linkShops}.mapValues{(shop,ids)->ids.filter{branches[it]?.shopId==shop}.toSet()}
   var unit=state.defaultUnit
   before.units.forEach{oldUnit->
    val replacement=after.units.find{it.id==oldUnit.id}
    if(replacement?.name!=oldUnit.name&&Quantities.unit(unit)==Quantities.unit(oldUnit.name))unit=replacement?.name?:after.units.find{Quantities.unit(it.name)==Quantities.unit(oldUnit.name)}?.name.orEmpty()
   }
   state=state.copy(recipientId=state.recipientId?.takeIf{it in after.recipients},productGroupId=state.productGroupId?.takeIf{it in after.groups},defaultUnit=unit,pendingDepartments=pendingDepartments,shopLinks=links,pendingShopLinks=pending,branchSelections=points,
    branchPriorities=state.branchPriorities.filterKeys{it in linkShops}.mapValues{(shop,ids)->ids.filter{branches[it]?.shopId==shop}},branchDepartments=state.branchDepartments.filter{(branch,dep)->branch in branches&&branches[branch]?.shopId in linkShops&&validDepartment(dep)!=null})
   val updated=draft.copy(state=state).encode()
   if(updated!=raw){edit.putString(key,updated);changed=true}
  }
  if(changed)check(edit.commit()){ "Не удалось обновить черновики" }
 }
}
