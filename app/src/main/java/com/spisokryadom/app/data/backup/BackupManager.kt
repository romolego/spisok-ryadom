package com.spisokryadom.app.data.backup
import android.content.Context
import android.util.Base64
import androidx.room.withTransaction
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.util.FuzzySearch
import com.spisokryadom.app.util.Quantities
import org.json.JSONObject
import org.json.JSONArray
import java.io.*
import java.util.UUID
import java.util.zip.*
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.spisokryadom.app.data.repository.ProductEditorTransactions
import com.spisokryadom.app.data.repository.MediaLifecycle
import com.spisokryadom.app.util.PhotoStore

/** Full backups are streaming ZIP snapshots; legacy JSON is a non-destructive catalogue merge. */
class BackupManager(private val context:Context,private val db:AppDatabase,private val archiveOutput:(File)->OutputStream = {it.outputStream()}){
 private val mutex=Mutex()
 private val photosDir get()=File(context.filesDir,"product_photos").also{it.mkdirs()}
 private val cacheDir get()=File(context.cacheDir,"backups").also{it.mkdirs()}
 data class Summary(val full:Boolean,val products:Int,val entries:Int,val cities:Int,val missingPhotos:Int=0,val goalLists:Int=0){
  val description:String get()=if(full)"Полная копия: товаров $products, покупок $entries, городов $cities. Каталог и все списки будут восстановлены из этой копии."+(if(missingPhotos>0)" Отсутствующих фотографий: $missingPhotos."else "")+(if(goalLists>0)" Целевых списков: $goalLists."else "")
   else "Старая копия каталога: товаров $products. Совпадения проверяются по идентичности и полным данным, а не только по названию. Неоднозначные записи добавляются отдельно; существующие покупки останутся на месте."
 }
 private suspend fun snapshot():BackupSnapshot {
  val cities=db.cityDao().getAllSync()
  return BackupSnapshot(db.productDao().getAllSync(),db.productGroupDao().getAllSync(),db.recipientDao().getAllSync(),db.shopDao().getAllSync(),
  db.shopDepartmentDao().getAllSync(),db.productShopLinkDao().getAllSync(),db.shoppingListEntryDao().getAllSync(),db.unitDao().getAllSync(),cities,preferences(cities),db.shopBranchDao().getAll(),db.shopBranchDao().allAvailability(),db.retailStructureDao().networks(),db.retailStructureDao().venues(),db.shopDepartmentDao().types(),db.retailStructureDao().departmentOrder(),db.retailStructureDao().productDepartments(),db.shopBranchDao().priorities(),db.goalListDao().getAllSync(),db.creationPresetDao().shops(),db.creationPresetDao().groups())
 }
 private fun preferences(cities:List<CityEntity>):JSONObject=JSONObject().apply{
  listOf("location_prefs","shopping_list_prefs","product_db_prefs").forEach{name->put(name,JSONObject().apply{
   context.getSharedPreferences(name,0).all.forEach{(key,value)->put(key,if(value is Set<*>)JSONArray(value.toList())else value)}
  })}
  // Older full copies could omit preferences. The persisted selection may then
  // outlive its city; serialize the same fallback LocationRepository displays.
  val location=getJSONObject("location_prefs");val selected=location.opt("active_city")
  if((selected is Int||selected is Long)&&cities.none{it.id==(selected as Number).toLong()})cities.firstOrNull()?.let{location.put("active_city",it.id)}
 }
 suspend fun exportFile():File=mutex.withLock {
  val target=File(cacheDir,"backup_${UUID.randomUUID()}.zip")
  var pinned=emptySet<String>()
  try{
    val s=MediaLifecycle.mutex.withLock {
     db.withTransaction{snapshot()}.also{snapshot->
      pinned=(snapshot.products.mapNotNull{it.photoUri}+snapshot.branches.mapNotNull{it.photoUri}+snapshot.shops.mapNotNull{it.logoUri}+snapshot.venues.mapNotNull{it.photoUri}).toSet()
      PhotoStore.pin(pinned)
     }
    }
    validate(s);val root=BackupJson.manifest(s)
    val rows=root.getJSONObject("data").getJSONArray("products")
    var photoBytes=0L;var photoCount=0
    ZipOutputStream(BufferedOutputStream(archiveOutput(target))).use{zip->
     zip.setLevel(1)
     s.products.forEachIndexed{i,p->p.photoUri?.let{path->
      val f=File(path)
      if(f.isFile){
       photoBytes+=f.length();require(++photoCount<MAX_ARCHIVE_ENTRIES&&photoBytes<=MAX_PHOTO_TOTAL){"Для одной копии слишком много фотографий"}
       require(f.length()<=MAX_PHOTO){"Фотография слишком большая: ${p.name}"}
       val name="photos/${p.stableId}.jpg";zip.putNextEntry(ZipEntry(name));f.inputStream().use{it.copyTo(zip)};zip.closeEntry();rows.getJSONObject(i).put("photo",name)
      }else rows.getJSONObject(i).put("photoUnavailable",true)
     }}
     val branchRows=root.getJSONObject("data").getJSONArray("shopBranches")
     s.branches.forEachIndexed{i,b->b.photoUri?.let{path->
      val f=File(path)
      if(f.isFile){
       photoBytes+=f.length();require(++photoCount<MAX_ARCHIVE_ENTRIES&&photoBytes<=MAX_PHOTO_TOTAL){"Для одной копии слишком много фотографий"}
       require(f.length()<=MAX_PHOTO){"Фотография слишком большая: ${b.address}"}
       val name="branch_photos/${b.id}.jpg";zip.putNextEntry(ZipEntry(name));f.inputStream().use{it.copyTo(zip)};zip.closeEntry();branchRows.getJSONObject(i).put("photo",name)
      }else branchRows.getJSONObject(i).put("photoUnavailable",true)
     }}
     val shopRows=root.getJSONObject("data").getJSONArray("shops")
     s.shops.forEachIndexed{i,shop->shop.logoUri?.let{path->
      val f=File(path)
      if(f.isFile){
       photoBytes+=f.length();require(++photoCount<MAX_ARCHIVE_ENTRIES&&photoBytes<=MAX_PHOTO_TOTAL){"Для одной копии слишком много изображений"}
       require(f.length()<=MAX_PHOTO){"Логотип слишком большой"}
       val name="shop_logos/${shop.id}.png";zip.putNextEntry(ZipEntry(name));f.inputStream().use{it.copyTo(zip)};zip.closeEntry();shopRows.getJSONObject(i).put("logo",name)
      }else shopRows.getJSONObject(i).put("photoUnavailable",true)
     }}
     val venueRows=root.getJSONObject("data").getJSONArray("venues")
     s.venues.forEachIndexed{i,venue->venue.photoUri?.let{path->
      val f=File(path)
      if(f.isFile){
       photoBytes+=f.length();require(++photoCount<MAX_ARCHIVE_ENTRIES&&photoBytes<=MAX_PHOTO_TOTAL){"Для одной копии слишком много фотографий"}
       require(f.length()<=MAX_PHOTO){"Фотография слишком большая"}
       val name="venue_photos/${venue.id}.jpg";zip.putNextEntry(ZipEntry(name));f.inputStream().use{it.copyTo(zip)};zip.closeEntry();venueRows.getJSONObject(i).put("photo",name)
      }else venueRows.getJSONObject(i).put("photoUnavailable",true)
     }}
     val manifest=root.toString().toByteArray(Charsets.UTF_8);require(manifest.size<=MAX_JSON){"Для одной копии слишком много данных"}
     zip.putNextEntry(ZipEntry("manifest.json"));zip.write(manifest);zip.closeEntry()
    }
   target
  }catch(e:Exception){target.delete();throw e}finally{PhotoStore.unpin(pinned)}
 }
 fun inspect(file:File):Summary {
  val root=readManifest(file)
  val rawVersion=root.get("version");require(rawVersion is Int||rawVersion is Long){"Версия копии должна быть целым числом"};val version=(rawVersion as Number).toLong();require(version in 1L..BACKUP_VERSION.toLong()){"Неподдерживаемая версия резервной копии: $version"}
  val d=root.getJSONObject("data")
  if(version>=4){require(root.optString("kind")=="full"){"Неизвестный вид копии"};listOf("products","productGroups","recipients","shops","shopDepartments","productShopLinks","shoppingListEntries","units","cities").forEach{require(d.optJSONArray(it)!=null){"В копии отсутствует раздел $it"}}}
  if(version>=7)listOf("retailNetworks","venues","departmentTypes","branchDepartmentOrder","productBranchDepartments").forEach{require(d.optJSONArray(it)!=null){"В копии отсутствует раздел $it"}}
  if(version>=12)listOf("shopCreationPresets","groupCreationPresets").forEach{require(d.optJSONArray(it)!=null){"В копии отсутствуют преднастройки $it"}}
  if(version>=11)require(d.optJSONArray("goalLists")!=null){"В копии отсутствуют целевые списки"}
  if(version>=5)listOf("shopBranches","productBranchAvailability").forEach{require(d.optJSONArray(it)!=null){"В копии отсутствует раздел $it"}}
  return Summary(root.optString("kind")=="full"&&version>=4,d.getJSONArray("products").length(),d.optJSONArray("shoppingListEntries")?.length()?:0,d.optJSONArray("cities")?.length()?:0,(0 until d.getJSONArray("products").length()).count{d.getJSONArray("products").getJSONObject(it).optBoolean("photoUnavailable")}+(d.optJSONArray("shopBranches")?.let{a->(0 until a.length()).count{a.getJSONObject(it).optBoolean("photoUnavailable")}}?:0)+(d.optJSONArray("shops")?.let{a->(0 until a.length()).count{a.getJSONObject(it).optBoolean("photoUnavailable")}}?:0)+(d.optJSONArray("venues")?.let{a->(0 until a.length()).count{a.getJSONObject(it).optBoolean("photoUnavailable")}}?:0),goalLists=d.optJSONArray("goalLists")?.length()?:0)
 }
 suspend fun importFile(file:File):Summary=mutex.withLock {
  val summary=inspect(file)
  // The stage can outlive the cleanup grace period on a slow/large import.
  // Register its whole tree atomically with discovery by MediaLifecycle.cleanup.
  val stage=MediaLifecycle.mutex.withLock {
   File(photosDir,"import_${UUID.randomUUID()}").also{require(it.mkdirs()){ "Не удалось подготовить фотографии" };PhotoStore.pin(setOf(it.absolutePath))}
  }
  var committed=false
  try{
   val root=if(isZip(file))readArchive(file,stage)else readJson(file.inputStream().use{bounded(it,MAX_JSON)})
   if(summary.full)require(!root.has("preferences")||root.get("preferences") is JSONObject){"Некорректный раздел настроек в копии"}
   if(root.getInt("version")>=14)listOf("productGroups","departmentTypes").forEach{key->val a=root.getJSONObject("data").getJSONArray(key);for(i in 0 until a.length()){val row=a.getJSONObject(i);require(row.has("isFavorite")&&row.has("favoriteOrder")){"Отсутствует оформление избранных групп"}}}
   val raw=BackupJson.read(root)
   if(root.getInt("version")<14)require(raw.groups.none{it.isFavorite||it.favoriteOrder!=0}&&raw.departmentTypes.none{it.isFavorite||it.favoriteOrder!=0}){"Избранные группы требуют копию новой версии"}
   if(root.getInt("version")<12)require(raw.shopPresets.isEmpty()&&raw.groupPresets.isEmpty()){"Преднастройки требуют копию новой версии"}
   if(root.getInt("version")<11)require(raw.goalLists.isEmpty()&&raw.entries.none{it.listId!=null||it.plannedUnitPrice!=null||it.customShopName!=null||it.customShopAddress!=null}){"Целевые списки требуют полную копию новой версии"}
   val sourceProducts=root.getJSONObject("data").getJSONArray("products")
   val withPhotos=raw.products.mapIndexed{i,p->
    val row=sourceProducts.getJSONObject(i);val name=row.optString("photo")
    val photo=if(name.isNotBlank()){
     require(name=="photos/${p.stableId}.jpg"){"Некорректное имя фотографии"}
     File(stage,name.removePrefix("photos/")).also{require(it.isFile){"В копии отсутствует фотография ${p.name}"}}
    }else if(!row.optString("photoBase64").isBlank()){
     val encoded=row.getString("photoBase64");require(encoded.length<=MAX_PHOTO*2){"Слишком большая фотография"}
     val bytes=Base64.decode(encoded,Base64.DEFAULT);require(bytes.size<=MAX_PHOTO)
     File(stage,"${UUID.randomUUID()}.jpg").also{it.writeBytes(bytes)}
    }else null
    p.copy(photoUri=photo?.absolutePath)
   }
   val oldBranches=if(summary.full&&root.getInt("version")<5)raw.shops.filter{!it.address.isNullOrBlank()}.map{ShopBranchEntity(it.id,it.id,it.address!!.trim())}else raw.branches
   val branchRows=root.getJSONObject("data").optJSONArray("shopBranches")
   val branchesWithPhotos=oldBranches.mapIndexed{i,b->
    val name=branchRows?.optJSONObject(i)?.optString("photo").orEmpty()
    val photo=if(name.isBlank())null else {
     require(root.getInt("version")>=6&&name=="branch_photos/${b.id}.jpg"){"Некорректное фото точки"}
     File(stage,name).also{require(it.isFile){"В копии отсутствует фото точки ${b.address}"}}
    }
    b.copy(photoUri=photo?.absolutePath)
   }
   val shopRows=root.getJSONObject("data").optJSONArray("shops")
   val shopsWithLogos=raw.shops.mapIndexed{i,shop->
    val name=shopRows?.optJSONObject(i)?.optString("logo").orEmpty()
    val logo=if(name.isBlank())null else {
     require(root.getInt("version")>=13&&name=="shop_logos/${shop.id}.png"){"Некорректный логотип магазина"}
     File(stage,name).also{require(it.isFile){"В копии отсутствует логотип ${shop.name}"}}
    }
    shop.copy(logoUri=logo?.absolutePath)
   }
   val venueRows=root.getJSONObject("data").optJSONArray("venues")
   val venuesWithPhotos=raw.venues.mapIndexed{i,venue->
    val name=venueRows?.optJSONObject(i)?.optString("photo").orEmpty()
    val photo=if(name.isBlank())null else{
     require(root.getInt("version")>=17&&name=="venue_photos/${venue.id}.jpg"){"Некорректное фото рынка или ТЦ"}
     File(stage,name).also{require(it.isFile){"В копии отсутствует фото рынка или ТЦ"}}
    }
    venue.copy(photoUri=photo?.absolutePath)
   }
   val adapted=raw.copy(products=withPhotos,venues=venuesWithPhotos,branches=branchesWithPhotos,shops=shopsWithLogos,entries=if(summary.full&&root.getInt("version")<5)raw.entries.map{it.copy(assignedBranchId=oldBranches.firstOrNull{b->b.shopId==it.assignedShopId}?.id)}else raw.entries)
   val incoming=if(root.getInt("version")<7)BackupJson.upgradeLegacy(adapted)else adapted
   if(summary.full){
    validate(incoming)
    ProductEditorTransactions.locked{
     // Once replacement starts, caller cancellation must not split SQLite commit
     // from media ownership, draft invalidation and the restored preferences.
     withContext(NonCancellable){
      db.withTransaction{restoreFull(incoming)}
      committed=true
      ProductEditorTransactions.generation.incrementAndGet()
      com.spisokryadom.app.util.DatabaseRevision.invalidate(context)
      context.getSharedPreferences("product_card_drafts",0).edit().clear().commit()
      runCatching{restorePreferences(incoming.preferences)}
      Unit
     }
    }
   }
   else {mergeLegacy(incoming);committed=true}
   context.getSharedPreferences("spisok_ryadom_prefs",0).edit().putBoolean("DEMO_DATA_LOADED",true).apply()
   // Only after a successful commit; fresh draft photos are deliberately retained.
   runCatching{MediaLifecycle.cleanup(context,db)}
   summary
  }catch(e:Exception){if(!committed)stage.deleteRecursively();throw e}
  finally{
   // A cleanup snapshot taken before commit must finish before the directory
   // lease hands ownership to the newly committed database references.
   withContext(NonCancellable){MediaLifecycle.mutex.withLock{PhotoStore.unpin(setOf(stage.absolutePath))}}
  }
 }
 private suspend fun restoreFull(s:BackupSnapshot){
  db.shoppingListEntryDao().deleteAll();db.goalListDao().deleteAll();db.productShopLinkDao().deleteAll();db.shopDepartmentDao().deleteAll();db.productDao().deleteAll();db.shopDao().deleteAll();db.productGroupDao().deleteAll();db.recipientDao().deleteAll();db.unitDao().deleteAll();db.shopDepartmentDao().clearTypes();db.retailStructureDao().clearVenues();db.retailStructureDao().clearNetworks();db.cityDao().deleteAll()
  s.goalLists.forEach{db.goalListDao().insert(it)};s.cities.forEach{db.cityDao().insert(it)};s.networks.forEach{db.retailStructureDao().insertNetwork(it)};s.venues.forEach{db.retailStructureDao().insertVenue(it)};s.departmentTypes.forEach{db.shopDepartmentDao().insertType(it)};s.groups.forEach{db.productGroupDao().insert(it)};s.recipients.forEach{db.recipientDao().insert(it)};s.units.forEach{db.unitDao().insert(it)};s.shops.forEach{db.shopDao().insert(it)};s.branches.forEach{db.shopBranchDao().insert(it)};s.departments.forEach{db.shopDepartmentDao().insert(it)};s.products.forEach{db.productDao().insert(it)};s.links.forEach{db.productShopLinkDao().insert(it)};s.availability.forEach{db.shopBranchDao().insertAvailability(it)};s.entries.forEach{db.shoppingListEntryDao().insert(it)};s.branchOrder.forEach{db.retailStructureDao().setDepartmentOrder(it)};s.branchDepartments.forEach{db.retailStructureDao().setProductDepartment(it)};s.branchPriorities.forEach{db.shopBranchDao().setPriority(it)};s.shopPresets.forEach{db.creationPresetDao().setShop(it)};s.groupPresets.forEach{db.creationPresetDao().setGroup(it)}
  context.getSharedPreferences("drafts",0).edit().remove("text_view_draft").remove("text_view_base").apply()
 }
 /** Candidates are ONLY rows present before this import, and each is consumed at most once.
  * This preserves distinct source IDs even when all their visible fields are identical. */
 private class Candidates<K,T>(rows:List<T>,key:(T)->K){
  private val byKey=rows.groupBy(key).mapValues{(_,values)->java.util.ArrayDeque(values)}
  fun take(key:K,allowed:(T)->Boolean={true}):T? {
   val queue=byKey[key]?:return null
   while(queue.isNotEmpty()){val row=queue.removeFirst();if(allowed(row))return row}
   return null
  }
 }
 private suspend fun mergeLegacy(s:BackupSnapshot)=db.withTransaction {
  validateLegacy(s)
  // Basket and preferences are not part of an old catalogue merge and need not be read.
  val before=BackupSnapshot(products=db.productDao().getAllSync(),groups=db.productGroupDao().getAllSync(),
   recipients=db.recipientDao().getAllSync(),shops=db.shopDao().getAllSync(),departments=db.shopDepartmentDao().getAllSync(),
   links=db.productShopLinkDao().getAllSync(),units=db.unitDao().getAllSync(),cities=db.cityDao().getAllSync())
  val active=context.getSharedPreferences("location_prefs",0).getLong("active_city",1)
   .let{id->before.cities.find{it.id==id}?.id?:before.cities.first().id}
  val groupMap=mutableMapOf<Long,Long>();val recipientMap=mutableMapOf<Long,Long>()
  val shopMap=mutableMapOf<Long,Long>();val deptMap=mutableMapOf<Long,Long>();val productMap=mutableMapOf<Long,Long>()
  // Use complete entity data, not just a name/address. A candidate is never overwritten
  // by this weaker form of matching, and rows inserted below never become candidates.
  val groups=Candidates(before.groups){it.copy(id=0)}
  s.groups.forEach{g->groupMap[g.id]=groups.take(g.copy(id=0))?.id?:db.productGroupDao().insert(g.copy(id=0))}
  val recipients=Candidates(before.recipients){it.copy(id=0)}
  s.recipients.forEach{r->recipientMap[r.id]=recipients.take(r.copy(id=0))?.id?:db.recipientDao().insert(r.copy(id=0))}
  val unitNames=before.units.map{FuzzySearch.normalize(it.name)}.toMutableSet()
  suspend fun ensureUnit(name:String,unit:UnitEntity?=null){
   if(name.isNotBlank()&&unitNames.add(FuzzySearch.normalize(name)))db.unitDao().insert(unit?.copy(id=0)?:UnitEntity(name=name))
  }
  s.units.forEach{ensureUnit(it.name,it)}
  val shops=Candidates(before.shops){it.copy(id=0)}
  s.shops.forEach{shop->
   val mapped=shop.copy(id=0,cityId=active)
   shopMap[shop.id]=shops.take(mapped)?.id?:db.shopDao().insert(mapped).also{id->if(!mapped.address.isNullOrBlank())db.shopBranchDao().insert(ShopBranchEntity(shopId=id,address=mapped.address.trim()))}
  }
  val departments=Candidates(before.departments){it.copy(id=0,typeId=0)}
  s.departments.forEach{dept->
   val mapped=dept.copy(id=0,shopId=shopMap.getValue(dept.shopId))
   deptMap[dept.id]=departments.take(mapped.copy(typeId=0))?.id?:db.shopDepartmentDao().insert(mapped.copy(typeId=0))
  }
  val sourceLinks=s.links.groupBy{it.productId}
  val previousLinks=before.links.groupBy{it.productId}
  fun linkKey(links:List<ProductShopLinkEntity>)=links.map{Triple(it.shopId,it.departmentId,it.priority)}
   .sortedWith(compareBy<Triple<Long,Long?,Int>>{it.first}.thenBy{it.second}.thenBy{it.third})
  fun fields(p:ProductEntity)=p.copy(id=0,stableId="",photoUri=null,isTemporary=false)
  val byStableId=before.products.associateBy{it.stableId}
  // Reserve identity matches before consuming any metadata matches, regardless of file order.
  val reserved=s.products.mapNotNull{byStableId[it.stableId]?.id}.toSet()
  val consumed=mutableSetOf<Long>()
  val exactProducts=Candidates(before.products.filter{!it.isTemporary&&it.photoUri==null}){
   fields(it) to linkKey(previousLinks[it.id].orEmpty())
  }
  s.products.forEach{product->
   val mapped=product.copy(id=0,
    productGroupId=product.productGroupId?.let{groupMap.getValue(it)},recipientId=product.recipientId?.let{recipientMap.getValue(it)},
    defaultQuantity=product.defaultQuantity?.takeIf{it.isFinite()&&it>0},isTemporary=false)
   val links=sourceLinks[product.id].orEmpty().map{
    it.copy(shopId=shopMap.getValue(it.shopId),departmentId=it.departmentId?.let{d->deptMap.getValue(d)})
   }
   val identityMatch=byStableId[product.stableId]
   val old=identityMatch?:if(mapped.photoUri==null)exactProducts.take(fields(mapped) to linkKey(links)){
    it.id !in reserved&&it.id !in consumed
   }else null
   require(old==null||consumed.add(old.id)){"Несколько товаров копии имеют одну идентичность"}
   val id=when{
    old==null->db.productDao().insert(mapped)
    identityMatch!=null->{
     db.productDao().update(mapped.copy(id=old.id,stableId=old.stableId,photoUri=mapped.photoUri?:old.photoUri));old.id
    }
    else->old.id // Exact metadata match: keep the existing record verbatim.
   }
   productMap[product.id]=id
   mapped.defaultUnit?.let{ensureUnit(it)}
  }
  val existingPairs=before.links.map{it.productId to it.shopId}.toMutableSet()
  s.links.forEach{link->
   val product=productMap.getValue(link.productId);val shop=shopMap.getValue(link.shopId)
   if(existingPairs.add(product to shop))db.productShopLinkDao().insert(link.copy(
    id=0,productId=product,shopId=shop,departmentId=link.departmentId?.let{deptMap.getValue(it)}))
  }
 }
 private fun validateLegacy(s:BackupSnapshot){
  require(s.goalLists.isEmpty()&&s.entries.none{it.listId!=null}){"Целевые списки переносятся полной копией"}
  require(s.branches.isEmpty()&&s.availability.isEmpty()&&s.branchPriorities.isEmpty()&&s.links.none{it.restrictBranches}){"Данные точек должны переноситься полной копией новой версии"}
  fun ids(values:List<Long>):Set<Long>{require(values.all{it>0}&&values.toSet().size==values.size){"Неверные или повторяющиеся ID в старой копии"};return values.toSet()}
  val products=ids(s.products.map{it.id});val shops=ids(s.shops.map{it.id})
  val groups=ids(s.groups.map{it.id});val recipients=ids(s.recipients.map{it.id})
  ids(s.departments.map{it.id});ids(s.links.map{it.id});ids(s.units.map{it.id})
  require(s.products.map{it.stableId}.toSet().size==s.products.size&&s.products.all{it.stableId.matches(Regex("[a-zA-Z0-9_-]{1,80}"))}){"Повторяющаяся или неверная идентичность товаров"}
  require((s.products.map{it.name}+s.shops.map{it.name}+s.groups.map{it.name}+s.recipients.map{it.name}+s.departments.map{it.name}+s.units.map{it.name}).all{it.isNotBlank()}){"Пустое название в старой копии"}
  s.products.forEach{p->require(p.productGroupId==null||p.productGroupId in groups);require(p.recipientId==null||p.recipientId in recipients)}
  val departments=s.departments.associateBy{it.id};s.departments.forEach{require(it.shopId in shops)}
  require(s.links.map{it.productId to it.shopId}.toSet().size==s.links.size){"Повторяющаяся привязка в старой копии"}
  s.links.forEach{l->require(l.productId in products&&l.shopId in shops);require(l.departmentId==null||departments[l.departmentId]?.shopId==l.shopId){"Отдел относится к другому магазину"}}
 }
 private fun validate(s:BackupSnapshot){
  fun ids(values:List<Long>,label:String):Set<Long>{require(values.all{it>0}&&values.distinct().size==values.size){"Повторяющиеся или неверные ID: $label"};return values.toSet()}
  val products=ids(s.products.map{it.id},"товары");val shops=ids(s.shops.map{it.id},"магазины");val groups=ids(s.groups.map{it.id},"группы");val recipients=ids(s.recipients.map{it.id},"получатели");val cities=ids(s.cities.map{it.id},"города")
  ids(s.units.map{it.id},"единицы");ids(s.entries.map{it.id},"покупки");ids(s.links.map{it.id},"привязки");ids(s.departments.map{it.id},"отделы")
  require(s.shopPresets.map{it.shopId}.distinct().size==s.shopPresets.size&&s.shopPresets.all{it.shopId in shops&&it.purchaseMode in com.spisokryadom.app.util.PurchaseModes.labels}){"Неверные преднастройки магазинов"}
  require(s.groupPresets.map{it.groupId}.distinct().size==s.groupPresets.size&&s.groupPresets.all{it.groupId in groups&&(it.recipientId==null||it.recipientId in recipients)}){"Неверные преднастройки товарных групп"}
  val goalLists=ids(s.goalLists.map{it.id},"целевые списки")
  require(s.goalLists.map{it.stableId}.distinct().size==s.goalLists.size&&s.goalLists.all{it.stableId.matches(Regex("[a-zA-Z0-9_-]{1,80}"))&&it.name.isNotBlank()&&it.name.length<=200&&'\u0000' !in it.name}){"Неверные целевые списки"}
  s.entries.forEach{e->
   require(e.listId==null||e.listId in goalLists){"Покупка ссылается на отсутствующий список"}
   if(e.listId!=null)require(java.math.BigDecimal.valueOf(e.quantity).stripTrailingZeros().scale()<=6){"В целевом списке количество должно содержать не более 6 знаков после запятой"}
   com.spisokryadom.app.data.repository.GoalListRepository.validPrice(e.plannedUnitPrice)
   e.plannedUnitPrice?.let{require((it*e.quantity).isFinite()){"Слишком большая сумма"}}
   require((e.customShopName?.length?:0)<=500&&(e.customShopAddress?.length?:0)<=1000){"Слишком длинное место покупки"}
   require(e.customShopName?.contains('\u0000')!=true&&e.customShopAddress?.contains('\u0000')!=true){"Некорректное место покупки"}
  }
  require(cities.isNotEmpty()){ "В полной копии нет городов" }
  validatePreferences(s.preferences,cities)
  require(s.products.map{it.stableId}.distinct().size==s.products.size&&s.products.all{it.stableId.matches(Regex("[a-zA-Z0-9_-]{1,80}"))}){"Некорректная идентичность товаров"}
  require((s.products.map{it.name}+s.shops.map{it.name}+s.cities.map{it.name}+s.groups.map{it.name}+s.recipients.map{it.name}).all{it.isNotBlank()}){"Пустые названия в копии"}
  s.products.forEach{p->require(p.productGroupId==null||p.productGroupId in groups);require(p.recipientId==null||p.recipientId in recipients);p.defaultQuantity?.let{Quantities.valid(it)}}
  val networks=ids(s.networks.map{it.id},"сети");val venues=s.venues.associateBy{it.id};ids(s.venues.map{it.id},"рынки и ТЦ")
  require(s.groups.all{it.favoriteOrder>=0}&&s.departmentTypes.all{it.favoriteOrder>=0})
  val types=s.departmentTypes.associateBy{it.id};ids(s.departmentTypes.map{it.id},"типы отделов")
  require(s.departmentTypes.map{it.normalizedName}.distinct().size==types.size)
  require(s.departmentTypes.all{it.name.isNotBlank()&&it.normalizedName==FuzzySearch.normalize(it.name)}){"Неверная общая группа"}
  require(s.networks.all{it.name.isNotBlank()}&&s.venues.all{it.name.isNotBlank()&&it.cityId in cities}){"Неверная сеть или торговый комплекс"}
  val shopMap=s.shops.associateBy{it.id}
  s.shops.forEach{require(it.cityId in cities){"Неизвестный город магазина"};require(it.networkId==null||it.networkId in networks){"Неизвестная сеть"};require(com.spisokryadom.app.util.PurchaseBackground.valid(it.purchaseBackground)){"Некорректный фон товаров магазина"};require(com.spisokryadom.app.util.AppearanceColors.validOptional(it.shoppingBackground)){"Некорректный цвет фона магазина"}}
  val linkMap=s.links.associateBy{it.productId to it.shopId}
  val branches=s.branches.associateBy{it.id};ids(s.branches.map{it.id},"точки")
  s.branches.forEach{require(it.shopId in shops&&(it.venueId!=null||it.address.isNotBlank()||it.label.isNotBlank())){"Неверная точка магазина"};require(it.venueId==null||venues[it.venueId]?.cityId==shopMap[it.shopId]?.cityId){"Торговый комплекс относится к другому городу"}}
  require(s.branches.map{it.shopId to com.spisokryadom.app.domain.OutletLocations.key(it)}.distinct().size==s.branches.size){"Повторяющийся адрес точки"}
  require(s.availability.distinct().size==s.availability.size){"Повторяющиеся точки товара"}
  s.availability.forEach{a->require(a.productId in products&&a.branchId in branches&&(a.shopId==branches[a.branchId]?.shopId&&linkMap[a.productId to a.shopId]?.restrictBranches==true)){"Точка товара не соответствует привязке"}}
  require(s.branchPriorities.map{it.productId to it.branchId}.distinct().size==s.branchPriorities.size){"Повторяющийся приоритет точки"}
  s.branchPriorities.forEach{p->require(p.displayOrder>=0&&branches[p.branchId]?.shopId==p.shopId&&linkMap.containsKey(p.productId to p.shopId)){"Приоритет точки не соответствует товару"}}
  val localGroups=s.departments.map{it.shopId to it.typeId}.toSet()
  val availablePairs=s.availability.map{it.productId to it.shopId}.toSet()
  require(s.branchOrder.map{it.branchId to it.typeId}.distinct().size==s.branchOrder.size)
  require(s.branchDepartments.map{it.productId to it.branchId}.distinct().size==s.branchDepartments.size)
  s.branchOrder.forEach{require(it.branchId in branches&&it.typeId in types&&(branches[it.branchId]?.shopId to it.typeId) in localGroups){"Неверный порядок групп точки"}}
  s.branchDepartments.forEach{d->require(branches[d.branchId]?.shopId==d.shopId&&linkMap.containsKey(d.productId to d.shopId)&&d.typeId in types&&(d.shopId to d.typeId) in localGroups){"Неверная группа товара в точке"}}
  s.links.filter{it.restrictBranches}.forEach{l->require((l.productId to l.shopId) in availablePairs){"Нет разрешённых точек товара"}}
  val departments=s.departments.associateBy{it.id};s.departments.forEach{require(it.shopId in shops&&types[it.typeId]?.name==it.name){"Неверное размещение группы"}}
  require(s.links.map{it.productId to it.shopId}.distinct().size==s.links.size){"Повторяющиеся привязки магазинов"}
  s.links.forEach{l->require(l.productId in products&&l.shopId in shops);require(l.departmentId==null||departments[l.departmentId]?.shopId==l.shopId){"Отдел не соответствует магазину"}}
  s.entries.forEach{e->require(e.assignedBranchId==null||branches[e.assignedBranchId]?.shopId==e.assignedShopId){"Адрес покупки не соответствует магазину"};require(e.productId in products);require(e.assignedShopId==null||e.assignedShopId in shops);require(e.assignedDepartmentId==null||departments[e.assignedDepartmentId]?.shopId==e.assignedShopId);Quantities.valid(e.quantity)}
 }
 /** A bad preference type must fail while the current database is still intact.
  * Unknown keys may carry primitive values for compatible older backups, but never
  * nested objects/null or mixed sets that SharedPreferences cannot represent. */
 private fun validatePreferences(root:JSONObject,cities:Set<Long>){
  root.optJSONObject("shopping_list_prefs")?.let{p->
   com.spisokryadom.app.util.FavoriteAppearance.colorKeys.forEach{key->if(p.has(key))require(p.get(key) is String&&com.spisokryadom.app.util.AppearanceColors.argb(p.getString(key))!=null){"Неверный цвет избранного"}}
   if(p.has("new_product_missing_quantity"))require(p.get("new_product_missing_quantity") is String&&Quantities.parse(p.getString("new_product_missing_quantity"))!=null){"Неверное количество по умолчанию"}
   if(p.has("new_product_purchase_type"))require(p.get("new_product_purchase_type") is String&&p.getString("new_product_purchase_type") in setOf("offline","online")){"Неверный тип покупки по умолчанию"}
   if(p.has("new_product_missing_unit"))require(p.get("new_product_missing_unit") is String&&p.getString("new_product_missing_unit").length<=80&&'\u0000' !in p.getString("new_product_missing_unit")){"Неверная единица по умолчанию"}
  }
  val booleans=mapOf(
   "shopping_list_prefs" to setOf("global_default_show_urgent_in_collapsed","global_default_boost_urgent_shop","global_default_save_new_product_to_db","remember_shop_priority_on_move","browse_products_on_add","autosave_product_cards","widget_show_departments","new_product_fill_missing")+com.spisokryadom.app.util.FavoriteAppearance.booleanKeys,
   "product_db_prefs" to setOf("favorites_only")
  )
  val strings=mapOf("shopping_list_prefs" to setOf("shopping_list_view_mode","new_product_purchase_type","new_product_missing_unit"),"product_db_prefs" to setOf("product_db_view_mode"))
  val sets=mapOf("shopping_list_prefs" to setOf("shopping_list_collapsed_shops","collapsed_other_groups"),"product_db_prefs" to setOf("collapsed_sections"))
  listOf("location_prefs","shopping_list_prefs","product_db_prefs").forEach{name->
   if(!root.has(name))return@forEach
   val values=root.get(name);require(values is JSONObject){"Некорректные настройки: $name"}
   values.keys().forEach{key->
    val value=values.get(key)
    val stringSet=value is JSONArray&&(0 until value.length()).all{value.get(it) is String}
    val valid=when{
     name=="location_prefs"&&key=="active_city"->(value is Int||value is Long)&&(value as Number).toLong() in cities
     name=="shopping_list_prefs"&&key=="widget_view_mode"->value is String&&com.spisokryadom.app.ui.shoppinglist.ViewMode.entries.any{it.name==value}
     name=="shopping_list_prefs"&&key=="widget_text_size"->(value is Int||value is Long)&&(value as Number).toLong() in 12L..22L
     key in booleans[name].orEmpty()->value is Boolean
     key in strings[name].orEmpty()->value is String
     key in sets[name].orEmpty()||(name=="product_db_prefs"&&key.startsWith("shop_points_"))->stringSet
     else->value is Boolean||value is Int||value is Long||value is String||stringSet
    }
    require(valid){"Некорректная настройка в копии: $name/$key"}
   }
  }
 }
 private fun restorePreferences(root:JSONObject){
  listOf("location_prefs","shopping_list_prefs","product_db_prefs").forEach{name->val values=root.optJSONObject(name)?:return@forEach
   val prefs=context.getSharedPreferences(name,0)
   val edit=prefs.edit()
   // clear() does not notify removed keys on API28; explicit removals keep live
   // screens in sync when an older backup lacks a newer preference.
   prefs.all.keys.forEach{edit.remove(it)}
   values.keys().forEach{key->when(val value=values.get(key)){is Boolean->edit.putBoolean(key,value);is Int->if(name=="location_prefs"&&key=="active_city")edit.putLong(key,value.toLong())else edit.putInt(key,value);is Long->edit.putLong(key,value);is String->edit.putString(key,value);is JSONArray->edit.putStringSet(key,(0 until value.length()).map{value.getString(it)}.toSet())}}
   edit.apply()
  }
 }
 private fun readManifest(file:File):JSONObject=if(isZip(file))ZipFile(file).use{zip->val entry=zip.getEntry("manifest.json")?:error("В архиве нет manifest.json");require(entry.size<=MAX_JSON);readJson(zip.getInputStream(entry).use{bounded(it,MAX_JSON)})}
  else readJson(file.inputStream().use{bounded(it,MAX_JSON)})
 private fun readArchive(file:File,stage:File):JSONObject {
  var root:JSONObject?=null;var total=0L;var count=0;val names=mutableSetOf<String>()
  ZipInputStream(BufferedInputStream(file.inputStream())).use{zip->while(true){val entry=zip.nextEntry?:break
   require(++count<=MAX_ARCHIVE_ENTRIES&&names.add(entry.name)){"Некорректный архив"}
   if(entry.name=="manifest.json")root=readJson(bounded(zip,MAX_JSON))
   else{
    require(entry.name.matches(Regex("((photos/[a-zA-Z0-9_-]+|branch_photos/[0-9]+|venue_photos/[0-9]+)[.]jpg|shop_logos/[0-9]+[.]png)"))){"Посторонний файл в архиве"}
    val target=File(stage,entry.name.removePrefix("photos/"));target.parentFile!!.mkdirs();val bytes=bounded(zip,MAX_PHOTO);total+=bytes.size;require(total<=MAX_PHOTO_TOTAL){"Слишком большой архив"};target.writeBytes(bytes)
   };zip.closeEntry()
  }}
  return root?:error("В архиве нет описания данных")
 }
 fun discard(file:File){if(file.canonicalPath.startsWith(cacheDir.canonicalPath+File.separator))file.delete()}
 companion object{
  const val BACKUP_VERSION=17;const val BACKUP_FILE_MIME="application/zip";const val BACKUP_FILE_NAME_PREFIX="spisok_ryadom_full"
  private const val MAX_JSON=32*1024*1024;private const val MAX_PHOTO=8*1024*1024
  private const val MAX_PHOTO_TOTAL=450L*1024*1024;private const val MAX_ARCHIVE_ENTRIES=20000
  private fun isZip(file:File)=file.inputStream().use{it.read()==0x50&&it.read()==0x4b}
  private fun readJson(bytes:ByteArray):JSONObject {
   val text=try{Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString()}
    catch(error:java.nio.charset.CharacterCodingException){throw IllegalArgumentException("Копия содержит некорректный UTF-8",error)}
   return JSONObject(text.removePrefix("\uFEFF"))
  }
  private fun bounded(input:InputStream,limit:Int):ByteArray{val out=ByteArrayOutputStream();val buf=ByteArray(16384);while(true){val n=input.read(buf);if(n<0)break;require(out.size()+n<=limit){"Файл слишком большой"};out.write(buf,0,n)};return out.toByteArray()}
 }
}
