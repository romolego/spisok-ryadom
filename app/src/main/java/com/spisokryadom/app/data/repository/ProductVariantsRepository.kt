package com.spisokryadom.app.data.repository

import androidx.room.withTransaction
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.entity.*
import kotlinx.coroutines.flow.combine

data class ProductVariantOption(val product:ProductEntity,val familyId:Long,val label:String,val enabled:Boolean=true,val order:Int=0)

class ProductVariantsRepository(private val db:AppDatabase,private val edits:CatalogueEdits?=null){
 private val dao get()=db.productVariantDao()
 fun observeMetadata()=combine(dao.observeVariants(),dao.observeCities()){a,b->a to b}
 suspend fun familyId(id:Long)=dao.variant(id)?.familyId?:id
 suspend fun options(id:Long,cityId:Long):List<ProductVariantOption> = db.withTransaction{
  val meta=dao.variant(id)
  if(meta==null)return@withTransaction listOfNotNull(db.productDao().getById(id)?.let{ProductVariantOption(it,id,"Основной вариант")})
  val metadata=dao.members(meta.familyId)
  val rules=dao.rules(meta.familyId,cityId).associateBy{it.productId}
  metadata.mapNotNull{v->db.productDao().getById(v.productId)?.let{ProductVariantOption(it,meta.familyId,v.label,rules[v.productId]?.enabled?:true,rules[v.productId]?.displayOrder?:v.displayOrder)}}
   .sortedWith(compareBy<ProductVariantOption>{it.order}.thenBy{it.product.id})
 }
 fun observeOptions(id:Long,cityId:Long)=combine(db.productDao().getAllIncludingTemporary(),dao.observeVariants(),dao.observeCities()){_,_,_->options(id,cityId)}
 suspend fun resolve(id:Long,cityId:Long,shopId:Long?=null,branchId:Long?=null):ProductEntity{
  val options=options(id,cityId).filter{it.enabled}
  if(options.isEmpty())require(db.productDao().getById(id)!=null){"Товар удалён"}
  require(options.isNotEmpty()){"В этом городе нет доступных вариантов товара"}
  if(options.size==1)return options.single().product
  val shops=db.shopDao().getAllSync().filter{it.cityId==cityId&&(shopId==null||it.id==shopId)}.map{it.id}.toSet()
  val byProduct=options.associate{it.product.id to db.productShopLinkDao().getLinksForProductSync(it.product.id)}
  val point=branchId?.takeIf{it>0}
  if(shopId!=null&&point!=null){
   // An explicitly opened physical point takes precedence over a family default.
   val local=options.firstOrNull{option->
    val link=byProduct.getValue(option.product.id).find{it.shopId==shopId}
    link!=null&&(!link.restrictBranches||db.shopBranchDao().availability(option.product.id).any{it.branchId==point&&it.shopId==shopId})
   }
   if(local!=null)return local.product
  }
  val links=options.filter{v->byProduct.getValue(v.product.id).any{it.shopId in shops}}.map{it.product.id}.toSet()
  val configured=dao.rules(options.first().familyId,cityId).isNotEmpty()
  return (if(shopId==null&&configured)options.first() else options.firstOrNull{it.product.id in links}?:options.first()).product
 }
 suspend fun create(id:Long,label:String):Long=withCatalogueEdits(db,edits){db.withTransaction{
  require(label.isNotBlank()&&label.length<=200&&'\u0000' !in label){"Название варианта: от 1 до 200 символов"}
  val family=familyId(id);val base=db.productDao().getById(family)?:error("Товар удалён");require(!base.isTemporary){"Сначала сохраните разовую позицию в базе товаров"}
  val rows=dao.variants().filter{it.familyId==family}
  require(rows.ifEmpty{listOf(ProductVariantEntity(family,family,"Основной вариант"))}.none{com.spisokryadom.app.util.FuzzySearch.normalize(it.label)==com.spisokryadom.app.util.FuzzySearch.normalize(label)}){"Такой вариант уже есть"}
  if(rows.isEmpty())dao.insert(ProductVariantEntity(family,family,"Основной вариант",0))
  // A new variant has its own shops and media, never foreign branch IDs copied by accident.
  val newId=db.productDao().insert(base.copy(id=0,stableId=java.util.UUID.randomUUID().toString(),photoUri=null,priceValue=null,priceDate=null,sellerUrl=null,productUrl=null,note=null))
  dao.insert(ProductVariantEntity(newId,family,label.trim(),(rows.maxOfOrNull{it.displayOrder}?:0)+1))
  newId
 }}
 suspend fun rename(id:Long,label:String)=withCatalogueEdits(db,edits){db.withTransaction{
  require(label.isNotBlank()&&label.length<=200&&'\u0000' !in label){"Название варианта: от 1 до 200 символов"}
  val row=dao.variant(id)?:error("Вариант удалён")
  require(dao.variants().none{it.familyId==row.familyId&&it.productId!=id&&com.spisokryadom.app.util.FuzzySearch.normalize(it.label)==com.spisokryadom.app.util.FuzzySearch.normalize(label)}){"Такой вариант уже есть"}
  dao.update(row.copy(label=label.trim()))
 }}
 suspend fun configureCity(id:Long,cityId:Long,ordered:List<Pair<Long,Boolean>>)=withCatalogueEdits(db,edits){db.withTransaction{
  require(db.cityDao().getAllSync().any{it.id==cityId}){"Город удалён"}
  val options=options(id,cityId)
  require(ordered.map{it.first}.toSet()==options.map{it.product.id}.toSet()&&ordered.size==options.size){"Состав вариантов изменился. Откройте настройку заново"}
  require(ordered.any{it.second}){"Оставьте хотя бы один доступный вариант"}
  ordered.forEachIndexed{i,(product,enabled)->dao.setCity(ProductVariantCityEntity(product,cityId,enabled,i))}
 }}
 suspend fun configureCities(id:Long,values:Map<Long,List<Pair<Long,Boolean>>>)=withCatalogueEdits(db,edits){db.withTransaction{
  require(values.isNotEmpty()){"Выберите город"}
  values.forEach{(city,rows)->configureCity(id,city,rows)}
 }}
 suspend fun rememberChoice(id:Long,cityId:Long){
  val options=options(id,cityId);if(options.size<=1)return
  configureCity(id,cityId,options.sortedBy{if(it.product.id==id)0 else 1}.map{it.product.id to it.enabled})
 }
 suspend fun photos(id:Long)=dao.photos(id).ifEmpty{db.productDao().getById(id)?.photoUri?.takeIf{it.isNotBlank()}?.let{listOf(ProductPhotoEntity(id,0,it))}.orEmpty()}
 suspend fun savePhotos(id:Long,photos:List<ProductPhotoEntity>)=db.withTransaction{
  require(photos.size<=5&&photos.map{it.uri}.distinct().size==photos.size&&photos.all{it.uri.isNotBlank()&&it.caption.length<=1000&&'\u0000' !in it.caption}){"Можно добавить до пяти разных фотографий, подпись — до 1000 символов"}
  val product=db.productDao().getById(id)?:error("Товар удалён")
  dao.clearPhotos(id);photos.forEachIndexed{i,p->dao.insertPhoto(p.copy(productId=id,position=i))}
  db.productDao().update(product.copy(photoUri=photos.firstOrNull()?.uri))
 }
 suspend fun label(id:Long)=dao.variant(id)?.label
 suspend fun remove(id:Long)=withCatalogueEdits(db,edits){db.withTransaction{
  val variant=dao.variant(id)?:error("Вариант удалён")
  require(variant.productId!=variant.familyId){"Первый вариант сохраняет идентичность товара. Можно отключить его для города"}
  val product=db.productDao().getById(id)?:error("Товар удалён")
  val name=com.spisokryadom.app.domain.variantNames(listOf(product),dao.variants()).single().name
  dao.deleteVariant(id)
  if(db.shoppingListEntryDao().getAllSync().any{it.productId==id})db.productDao().update(product.copy(name=name,isTemporary=true,isFavorite=false))
  else db.productDao().delete(product)
  db.cityDao().getAllSync().forEach{city->val remaining=options(variant.familyId,city.id);if(remaining.none{it.enabled})dao.setCity(ProductVariantCityEntity(variant.familyId,city.id,true,0))}
 }}
 suspend fun <T> transaction(block:suspend()->T):T=db.withTransaction{block()}
}
