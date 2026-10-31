package com.spisokryadom.app.ui.productcard

import android.content.Context
import android.content.SharedPreferences
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.data.repository.ProductRepository
import com.spisokryadom.app.util.Quantities
import org.json.JSONArray
import org.json.JSONObject

/** Raw editor values survive incomplete numbers and process death independently of valid catalogue rows. */
data class ProductEditDraft(val stableId:String,val productId:Long,val newSession:Boolean,val allowDuplicate:Boolean,val state:ProductCardUiState,val autoPersist:Boolean=true) {
 fun applyTo(base:ProductCardUiState):ProductCardUiState=state.copy(
  groups=base.groups,recipients=base.recipients,shops=base.shops,shopOrders=base.shopOrders,
  branches=base.branches,unitSuggestions=base.unitSuggestions,allDepartments=base.allDepartments,
  departmentTypes=base.departmentTypes,productContexts=base.productContexts,variantOptions=base.variantOptions,isLoading=false)
 suspend fun persist(repository:ProductRepository,defaults:com.spisokryadom.app.util.ProductCreationDefaults?=null):Long {
  val s=state
  val fallback=defaults?.takeIf{newSession&&it.fillMissing}
  val quantity=if(s.defaultQuantity.isBlank())fallback?.missingQuantity else Quantities.parse(s.defaultQuantity)?:error("Проверьте количество")
  val price=if(s.priceValue.isBlank())null else s.priceValue.replace(',','.').toDoubleOrNull()?.takeIf{it.isFinite()&&it>=0}?:error("Проверьте цену")
  val p=ProductEntity(id=productId.coerceAtLeast(0),stableId=stableId,name=s.name.trim(),productGroupId=s.productGroupId,recipientId=s.recipientId,
   defaultQuantity=quantity,defaultUnit=Quantities.unit(s.defaultUnit).ifBlank{fallback?.missingUnit?.ifBlank{null}},note=s.note.trim().ifBlank{null},purchaseType=s.purchaseType,
   sellerUrl=s.sellerUrl.trim().ifBlank{null},productUrl=s.productUrl.trim().ifBlank{null},photoUri=s.photoUri.ifBlank{null},priceValue=price,priceDate=s.priceDate.ifBlank{null},
   isTemporary=s.isTemporary,preferCityShopOrder=s.preferCityShopOrder,isFavorite=s.isFavorite,accent=s.accent)
  val links=if(newSession)s.pendingShopLinks.map{ProductShopLinkEntity(productId=0,shopId=it.shopId,priority=it.displayOrder,departmentId=it.departmentId)}else s.shopLinks.map{it.link}
  val points=repository.editorBranches()
  return repository.variants.transaction {
  val id=repository.saveEditorProduct(p,links,s.branchSelections.filterKeys{id->links.any{it.shopId==id}},s.pendingDepartments,
   s.branchDepartments.filterKeys{id->points.any{it.id==id&&links.any{l->l.shopId==it.shopId}}},newSession,allowDuplicate,s.branchPriorities.filterKeys{id->links.any{it.shopId==id}})
  repository.variants.savePhotos(id,s.photos.ifEmpty{p.photoUri?.let{listOf(ProductPhotoEntity(id,0,it))}.orEmpty()})
  id
  }
 }
 fun encode():String=JSONObject().apply {
  put("autoPersist",autoPersist);put("stableId",stableId);put("productId",productId);put("newSession",newSession);put("allowDuplicate",allowDuplicate)
  val s=state
  listOf("name" to s.name,"defaultUnit" to s.defaultUnit,"defaultQuantity" to s.defaultQuantity,"note" to s.note,"purchaseType" to s.purchaseType,"sellerUrl" to s.sellerUrl,"productUrl" to s.productUrl,"photoUri" to s.photoUri,"priceValue" to s.priceValue,"priceDate" to s.priceDate,"accent" to s.accent).forEach{(k,v)->put(k,v)}
  put("photos",JSONArray(s.photos.map{JSONObject().put("uri",it.uri).put("caption",it.caption)}))
  put("productGroupId",s.productGroupId?:JSONObject.NULL);put("recipientId",s.recipientId?:JSONObject.NULL)
  put("recipientManuallySet",s.recipientManuallySet);put("purchaseTypeManuallySet",s.purchaseTypeManuallySet);put("isFavorite",s.isFavorite);put("isTemporary",s.isTemporary);put("preferCityShopOrder",s.preferCityShopOrder)
  put("pendingLinks",JSONArray(s.pendingShopLinks.map{JSONObject().put("tempId",it.tempId).put("shopId",it.shopId).put("shopName",it.shopName).put("departmentId",it.departmentId?:JSONObject.NULL).put("departmentName",it.departmentName?:JSONObject.NULL).put("order",it.displayOrder)}))
  put("links",JSONArray(s.shopLinks.map{val l=it.link;JSONObject().put("id",l.id).put("shopId",l.shopId).put("shopName",it.shopName).put("departmentId",l.departmentId?:JSONObject.NULL).put("departmentName",it.departmentName?:JSONObject.NULL).put("order",l.priority).put("restrict",l.restrictBranches)}))
  put("departments",JSONArray(s.pendingDepartments.map{JSONObject().put("id",it.id).put("shopId",it.shopId).put("name",it.name).put("order",it.displayOrder).put("typeId",it.typeId)}))
  put("points",JSONObject().apply{s.branchSelections.forEach{(id,ids)->put(id.toString(),JSONArray(ids.sorted()))}})
  put("priorities",JSONObject().apply{s.branchPriorities.forEach{(id,ids)->put(id.toString(),JSONArray(ids))}})
  put("pointDepartments",JSONObject().apply{s.branchDepartments.forEach{(id,dep)->put(id.toString(),dep)}})
 }.toString()
 companion object {
  fun decode(raw:String):ProductEditDraft {
   val j=JSONObject(raw);fun nullable(o:JSONObject,k:String)=if(o.isNull(k))null else o.getLong(k)
   fun objects(k:String)=j.getJSONArray(k).let{a->(0 until a.length()).map{a.getJSONObject(it)}}
   val productId=j.getLong("productId");val fresh=j.getBoolean("newSession")
   val points=j.getJSONObject("points");val overrides=j.getJSONObject("pointDepartments")
   val s=ProductCardUiState(photos=j.optJSONArray("photos")?.let{a->(0 until a.length()).map{i->a.getJSONObject(i).let{ProductPhotoEntity(productId,i,it.getString("uri"),it.optString("caption"))}}}.orEmpty(),activeVariantId=productId.takeIf{it>0},recipientManuallySet=j.optBoolean("recipientManuallySet",!fresh||!j.isNull("recipientId")),purchaseTypeManuallySet=j.optBoolean("purchaseTypeManuallySet",true),isNew=fresh,isLoading=false,name=j.getString("name"),defaultUnit=j.getString("defaultUnit"),defaultQuantity=j.getString("defaultQuantity"),note=j.getString("note"),purchaseType=j.getString("purchaseType"),sellerUrl=j.getString("sellerUrl"),productUrl=j.getString("productUrl"),photoUri=j.getString("photoUri"),priceValue=j.getString("priceValue"),priceDate=j.getString("priceDate"),accent=j.getString("accent"),productGroupId=nullable(j,"productGroupId"),recipientId=nullable(j,"recipientId"),isFavorite=j.getBoolean("isFavorite"),isTemporary=j.getBoolean("isTemporary"),preferCityShopOrder=j.getBoolean("preferCityShopOrder"),
    pendingShopLinks=objects("pendingLinks").map{PendingShopLink(it.getLong("tempId"),it.getLong("shopId"),it.getString("shopName"),nullable(it,"departmentId"),if(it.isNull("departmentName"))null else it.getString("departmentName"),it.getInt("order"))},
    shopLinks=objects("links").map{ShopLinkUi(ProductShopLinkEntity(id=it.getLong("id"),productId=productId,shopId=it.getLong("shopId"),departmentId=nullable(it,"departmentId"),priority=it.getInt("order"),restrictBranches=it.getBoolean("restrict")),it.getString("shopName"),if(it.isNull("departmentName"))null else it.getString("departmentName"))},
    pendingDepartments=objects("departments").map{ShopDepartmentEntity(id=it.getLong("id"),shopId=it.getLong("shopId"),name=it.getString("name"),displayOrder=it.getInt("order"),typeId=it.getLong("typeId"))},
    branchPriorities=j.optJSONObject("priorities")?.let{o->o.keys().asSequence().associate{key->key.toLong() to o.getJSONArray(key).let{a->(0 until a.length()).map{a.getLong(it)}}}}?:emptyMap(),
    branchSelections=points.keys().asSequence().associate{key->key.toLong() to points.getJSONArray(key).let{a->(0 until a.length()).map{a.getLong(it)}.toSet()}},
    branchDepartments=overrides.keys().asSequence().associate{it.toLong() to overrides.getLong(it)})
   return ProductEditDraft(j.getString("stableId"),productId,fresh,j.optBoolean("allowDuplicate"),s,j.optBoolean("autoPersist",true))
  }
 }
}

object ProductDraftStore {
 const val PREFS="product_card_drafts"
 fun preferences(context:Context):SharedPreferences=context.getSharedPreferences(PREFS,0)
 fun find(preferences:SharedPreferences?,stableId:String):ProductEditDraft?=preferences?.all?.values?.mapNotNull{raw->runCatching{ProductEditDraft.decode(raw as String)}.getOrNull()}?.firstOrNull{it.stableId==stableId}
 fun write(preferences:SharedPreferences?,key:String,draft:ProductEditDraft){
  if(preferences==null)return
  val edit=preferences.edit()
  preferences.all.forEach{(k,v)->if(k!=key&&runCatching{JSONObject(v as String).getString("stableId")==draft.stableId}.getOrDefault(false))edit.remove(k)}
  edit.putString(key,draft.encode()).commit()
 }
 fun remove(preferences:SharedPreferences?,stableId:String){
  if(preferences==null)return
  val edit=preferences.edit();preferences.all.forEach{(key,raw)->if(runCatching{JSONObject(raw as String).getString("stableId")==stableId}.getOrDefault(false))edit.remove(key)};edit.commit()
 }
 suspend fun applyBatch(context:Context,repository:ProductRepository,ids:Set<Long>,change:com.spisokryadom.app.data.repository.ProductBatchChange,mutation:suspend()->Unit){
  com.spisokryadom.app.data.repository.ProductEditorTransactions.locked{
   // Once writing starts, a closing Activity must not leave an older recoverable draft behind.
   kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable){mutation();reconcileBatch(context,repository,ids,change)}
  }
 }
 suspend fun reconcileBatch(context:Context,repository:ProductRepository,ids:Set<Long>,change:com.spisokryadom.app.data.repository.ProductBatchChange){
  val prefs=preferences(context)
  prefs.all.forEach{(key,raw)->
   val draft=runCatching{ProductEditDraft.decode(raw as String)}.getOrNull()?:return@forEach
   val product=repository.getProductByStableId(draft.stableId)?:return@forEach
   if(product.id !in ids)return@forEach
   val old=draft.state
   val next=when(change){
    is com.spisokryadom.app.data.repository.ProductBatchChange.Recipient->old.copy(recipientId=change.id)
    is com.spisokryadom.app.data.repository.ProductBatchChange.Group->old.copy(productGroupId=change.id)
    is com.spisokryadom.app.data.repository.ProductBatchChange.Favorite->old.copy(isFavorite=change.value)
    else->{
     val links=repository.getShopLinksForProductSync(product.id)
     val changeShops=when(change){
      is com.spisokryadom.app.data.repository.ProductBatchChange.Shops->change.ids
      is com.spisokryadom.app.data.repository.ProductBatchChange.Department->setOf(change.shopId)
      else->emptySet()
     }
     val shopNames=repository.editorShops().associateBy{it.id}
     val departments=repository.editorDepartments().associateBy{it.id}
     val ui=old.shopLinks.toMutableList()
     val pending=old.pendingShopLinks.toMutableList()
     links.filter{it.shopId in changeShops}.forEach{l->
      val index=ui.indexOfFirst{it.link.shopId==l.shopId}
      val pendingIndex=pending.indexOfFirst{it.shopId==l.shopId}
      val allPoints=change is com.spisokryadom.app.data.repository.ProductBatchChange.Department
      if(index<0)ui.add(ShopLinkUi(l,shopNames[l.shopId]?.name.orEmpty(),departments[l.departmentId]?.name))
      else if(allPoints)ui[index]=ui[index].copy(link=ui[index].link.copy(departmentId=l.departmentId),departmentName=departments[l.departmentId]?.name)
      if(pendingIndex<0)pending.add(PendingShopLink(-l.shopId,l.shopId,shopNames[l.shopId]?.name.orEmpty(),l.departmentId,departments[l.departmentId]?.name,l.priority))
      else if(allPoints)pending[pendingIndex]=pending[pendingIndex].copy(departmentId=l.departmentId,departmentName=departments[l.departmentId]?.name)
     }
     val branches=repository.editorBranches().associateBy{it.id}
     val revised=old.branchDepartments.toMutableMap()
     if(change is com.spisokryadom.app.data.repository.ProductBatchChange.Department){
      revised.keys.filter{branches[it]?.shopId==change.shopId}.forEach{revised.remove(it)}
     }
     old.copy(shopLinks=ui,pendingShopLinks=pending,branchDepartments=revised)
    }
   }
   write(prefs,key,draft.copy(productId=product.id,state=next))
  }
 }
 fun photos(context:Context):Set<String> = preferences(context).all.values.flatMap{raw->runCatching{ProductEditDraft.decode(raw as String).state.let{it.photos.map{p->p.uri}+listOfNotNull(it.photoUri.takeIf{v->v.isNotBlank()})}}.getOrDefault(emptyList())}.toSet()

 suspend fun recover(context:Context,repository:ProductRepository,shops:com.spisokryadom.app.data.repository.ShopRepository){
  val preferences=preferences(context)
  preferences.all.forEach{(key,raw)->try{com.spisokryadom.app.data.repository.ProductEditorTransactions.locked{
   if(preferences.getString(key,null)==raw){val d=ProductEditDraft.decode(raw as String)
    if(!d.autoPersist||!com.spisokryadom.app.util.ProductEditingPreferences.enabled(com.spisokryadom.app.util.ProductEditingPreferences.preferences(context)))preferences.edit().remove(key).commit()
    else if(d.state.name.isNotBlank())d.persist(repository,com.spisokryadom.app.util.ProductCreationDefaults.read(com.spisokryadom.app.util.ProductEditingPreferences.preferences(context)))
   }
  }
  }catch(e:Exception){if(e is kotlinx.coroutines.CancellationException)throw e;android.util.Log.w("ProductDraft","Draft kept for editing",e)}}
 }
}
