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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Full backups are streaming ZIP snapshots; legacy JSON is a non-destructive catalogue merge. */
class BackupManager(private val context:Context,private val db:AppDatabase){
 private val mutex=Mutex()
 private val photosDir get()=File(context.filesDir,"product_photos").also{it.mkdirs()}
 private val cacheDir get()=File(context.cacheDir,"backups").also{it.mkdirs()}
 data class Summary(val full:Boolean,val products:Int,val entries:Int,val cities:Int,val missingPhotos:Int=0){
  val description:String get()=if(full)"Полная копия: товаров $products, покупок $entries, городов $cities. Каталог и текущий список будут восстановлены из этой копии."+(if(missingPhotos>0)" Отсутствующих фотографий: $missingPhotos."else "")
   else "Старая копия каталога: товаров $products. Товары и магазины будут добавлены или сопоставлены по названию; существующие покупки останутся на месте."
 }
 private suspend fun snapshot()=BackupSnapshot(db.productDao().getAllSync(),db.productGroupDao().getAllSync(),db.recipientDao().getAllSync(),db.shopDao().getAllSync(),
  db.shopDepartmentDao().getAllSync(),db.productShopLinkDao().getAllSync(),db.shoppingListEntryDao().getAllSync(),db.unitDao().getAllSync(),db.cityDao().getAllSync(),preferences())
 private fun preferences():JSONObject=JSONObject().apply{
  listOf("location_prefs","shopping_list_prefs","product_db_prefs").forEach{name->put(name,JSONObject().apply{
   context.getSharedPreferences(name,0).all.forEach{(key,value)->put(key,if(value is Set<*>)JSONArray(value.toList())else value)}
  })}
 }
 suspend fun exportFile():File=mutex.withLock {
  val target=File(cacheDir,"backup_${UUID.randomUUID()}.zip")
  try{
   db.withTransaction{
    val s=snapshot();validate(s);val root=BackupJson.manifest(s)
    val rows=root.getJSONObject("data").getJSONArray("products")
    var photoBytes=0L;var photoCount=0
    ZipOutputStream(BufferedOutputStream(target.outputStream())).use{zip->
     zip.setLevel(1)
     s.products.forEachIndexed{i,p->p.photoUri?.let{path->
      val f=File(path)
      if(f.isFile){
       photoBytes+=f.length();require(++photoCount<MAX_ARCHIVE_ENTRIES&&photoBytes<=MAX_PHOTO_TOTAL){"Для одной копии слишком много фотографий"}
       require(f.length()<=MAX_PHOTO){"Фотография слишком большая: ${p.name}"}
       val name="photos/${p.stableId}.jpg";zip.putNextEntry(ZipEntry(name));f.inputStream().use{it.copyTo(zip)};zip.closeEntry();rows.getJSONObject(i).put("photo",name)
      }else rows.getJSONObject(i).put("photoUnavailable",true)
     }}
     val manifest=root.toString().toByteArray(Charsets.UTF_8);require(manifest.size<=MAX_JSON){"Для одной копии слишком много данных"}
     zip.putNextEntry(ZipEntry("manifest.json"));zip.write(manifest);zip.closeEntry()
    }
   };target
  }catch(e:Exception){target.delete();throw e}
 }
 fun inspect(file:File):Summary {
  val root=readManifest(file)
  val version=root.getInt("version");require(version in 1..BACKUP_VERSION){"Неподдерживаемая версия резервной копии: $version"}
  val d=root.getJSONObject("data")
  if(version==4){require(root.optString("kind")=="full"){"Неизвестный вид копии"};listOf("products","productGroups","recipients","shops","shopDepartments","productShopLinks","shoppingListEntries","units","cities").forEach{require(d.optJSONArray(it)!=null){"В копии отсутствует раздел $it"}}}
  return Summary(root.optString("kind")=="full"&&version>=4,d.getJSONArray("products").length(),d.optJSONArray("shoppingListEntries")?.length()?:0,d.optJSONArray("cities")?.length()?:0,(0 until d.getJSONArray("products").length()).count{d.getJSONArray("products").getJSONObject(it).optBoolean("photoUnavailable")})
 }
 suspend fun importFile(file:File):Summary=mutex.withLock {
  val summary=inspect(file)
  val stage=File(photosDir,"import_${UUID.randomUUID()}").also{it.mkdirs()}
  var committed=false
  try{
   val root=if(isZip(file))readArchive(file,stage)else JSONObject(file.inputStream().use{bounded(it,MAX_JSON)}.toString(Charsets.UTF_8))
   val raw=BackupJson.read(root)
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
   val incoming=raw.copy(products=withPhotos)
   if(summary.full){validate(incoming);db.withTransaction{restoreFull(incoming)};committed=true;runCatching{restorePreferences(incoming.preferences)}}
   else {mergeLegacy(incoming);committed=true}
   context.getSharedPreferences("spisok_ryadom_prefs",0).edit().putBoolean("DEMO_DATA_LOADED",true).apply()
   // Only after a successful commit; fresh draft photos are deliberately retained.
   runCatching{cleanupPhotos(db.productDao().getAllSync().mapNotNull{it.photoUri}.toSet())}
   summary
  }catch(e:Exception){if(!committed)stage.deleteRecursively();throw e}
 }
 private suspend fun restoreFull(s:BackupSnapshot){
  db.shoppingListEntryDao().deleteAll();db.productShopLinkDao().deleteAll();db.shopDepartmentDao().deleteAll();db.productDao().deleteAll();db.shopDao().deleteAll();db.productGroupDao().deleteAll();db.recipientDao().deleteAll();db.unitDao().deleteAll();db.cityDao().deleteAll()
  s.cities.forEach{db.cityDao().insert(it)};s.groups.forEach{db.productGroupDao().insert(it)};s.recipients.forEach{db.recipientDao().insert(it)};s.units.forEach{db.unitDao().insert(it)};s.shops.forEach{db.shopDao().insert(it)};s.departments.forEach{db.shopDepartmentDao().insert(it)};s.products.forEach{db.productDao().insert(it)};s.links.forEach{db.productShopLinkDao().insert(it)};s.entries.forEach{db.shoppingListEntryDao().insert(it)}
 }
 private suspend fun mergeLegacy(s:BackupSnapshot)=db.withTransaction {
  fun key(s:String)=FuzzySearch.normalize(s)
  val cities=db.cityDao().getAllSync();val active=context.getSharedPreferences("location_prefs",0).getLong("active_city",1).let{id->cities.find{it.id==id}?.id?:cities.first().id}
  val groupMap=mutableMapOf<Long,Long>();val recipientMap=mutableMapOf<Long,Long>();val shopMap=mutableMapOf<Long,Long>();val deptMap=mutableMapOf<Long,Long>();val productMap=mutableMapOf<Long,Long>()
  s.groups.forEach{g->val old=db.productGroupDao().getAllSync().firstOrNull{key(it.name)==key(g.name)};groupMap[g.id]=old?.id?:db.productGroupDao().insert(g.copy(id=0))}
  s.recipients.forEach{g->val old=db.recipientDao().getAllSync().firstOrNull{key(it.name)==key(g.name)};recipientMap[g.id]=old?.id?:db.recipientDao().insert(g.copy(id=0))}
  s.units.forEach{u->if(db.unitDao().getAllSync().none{key(it.name)==key(u.name)})db.unitDao().insert(u.copy(id=0))}
  s.shops.forEach{shop->
   require(shop.name.isNotBlank())
   val old=db.shopDao().getAllSync().firstOrNull{it.cityId==active&&key(it.name)==key(shop.name)&&key(it.address.orEmpty())==key(shop.address.orEmpty())}
   shopMap[shop.id]=old?.id?:db.shopDao().insert(shop.copy(id=0,cityId=active))
  }
  s.departments.forEach{dept->val shop=shopMap[dept.shopId]?:error("В копии неизвестный магазин")
   val old=db.shopDepartmentDao().getByShopIdSync(shop).firstOrNull{key(it.name)==key(dept.name)}
   deptMap[dept.id]=old?.id?:db.shopDepartmentDao().insert(dept.copy(id=0,shopId=shop))
  }
  s.products.forEach{product->
   require(product.name.isNotBlank())
   val all=db.productDao().getAllSync();val old=all.find{it.stableId==product.stableId} ?: all.filter{!it.isTemporary&&key(it.name)==key(product.name)&&Quantities.unit(it.defaultUnit.orEmpty())==Quantities.unit(product.defaultUnit.orEmpty())}.singleOrNull()
   val mapped=product.copy(id=old?.id?:0,stableId=old?.stableId?:product.stableId,productGroupId=product.productGroupId?.let{groupMap[it]},recipientId=product.recipientId?.let{recipientMap[it]},
    defaultQuantity=product.defaultQuantity?.takeIf{it.isFinite()&&it>0},isTemporary=false,photoUri=product.photoUri?:old?.photoUri)
   val id=if(old==null)db.productDao().insert(mapped)else{db.productDao().update(mapped);old.id};productMap[product.id]=id
   mapped.defaultUnit?.takeIf{it.isNotBlank()}?.let{unit->if(db.unitDao().getAllSync().none{key(it.name)==key(unit)})db.unitDao().insert(UnitEntity(name=unit))}
  }
  s.links.forEach{link->val product=productMap[link.productId]?:error("В копии привязка неизвестного товара");val shop=shopMap[link.shopId]?:error("В копии неизвестный магазин")
   val dept=link.departmentId?.let{deptMap[it]}
   if(dept!=null)require(db.shopDepartmentDao().getById(dept)?.shopId==shop){"Группа относится к другому магазину"}
   if(db.productShopLinkDao().getLinksForProductSync(product).none{it.shopId==shop})db.productShopLinkDao().insert(link.copy(id=0,productId=product,shopId=shop,departmentId=dept))
  }
 }
 private fun validate(s:BackupSnapshot){
  fun ids(values:List<Long>,label:String):Set<Long>{require(values.all{it>0}&&values.distinct().size==values.size){"Повторяющиеся или неверные ID: $label"};return values.toSet()}
  val products=ids(s.products.map{it.id},"товары");val shops=ids(s.shops.map{it.id},"магазины");val groups=ids(s.groups.map{it.id},"группы");val recipients=ids(s.recipients.map{it.id},"получатели");val cities=ids(s.cities.map{it.id},"города")
  ids(s.units.map{it.id},"единицы");ids(s.entries.map{it.id},"покупки");ids(s.links.map{it.id},"привязки");ids(s.departments.map{it.id},"отделы")
  require(cities.isNotEmpty()){ "В полной копии нет городов" }
  require(s.products.map{it.stableId}.distinct().size==s.products.size&&s.products.all{it.stableId.matches(Regex("[a-zA-Z0-9_-]{1,80}"))}){"Некорректная идентичность товаров"}
  require((s.products.map{it.name}+s.shops.map{it.name}+s.cities.map{it.name}+s.groups.map{it.name}+s.recipients.map{it.name}).all{it.isNotBlank()}){"Пустые названия в копии"}
  s.products.forEach{p->require(p.productGroupId==null||p.productGroupId in groups);require(p.recipientId==null||p.recipientId in recipients);p.defaultQuantity?.let{Quantities.valid(it)}}
  s.shops.forEach{require(it.cityId in cities){"Неизвестный город магазина"}}
  val departments=s.departments.associateBy{it.id};s.departments.forEach{require(it.shopId in shops)}
  require(s.links.map{it.productId to it.shopId}.distinct().size==s.links.size){"Повторяющиеся привязки магазинов"}
  s.links.forEach{l->require(l.productId in products&&l.shopId in shops);require(l.departmentId==null||departments[l.departmentId]?.shopId==l.shopId){"Отдел не соответствует магазину"}}
  s.entries.forEach{e->require(e.productId in products);require(e.assignedShopId==null||e.assignedShopId in shops);require(e.assignedDepartmentId==null||departments[e.assignedDepartmentId]?.shopId==e.assignedShopId);Quantities.valid(e.quantity)}
 }
 private fun restorePreferences(root:JSONObject){
  listOf("location_prefs","shopping_list_prefs","product_db_prefs").forEach{name->val values=root.optJSONObject(name)?:return@forEach
   val edit=context.getSharedPreferences(name,0).edit().clear()
   values.keys().forEach{key->when(val value=values.get(key)){is Boolean->edit.putBoolean(key,value);is Int->if(key=="active_city")edit.putLong(key,value.toLong())else edit.putInt(key,value);is Long->edit.putLong(key,value);is String->edit.putString(key,value);is JSONArray->edit.putStringSet(key,(0 until value.length()).map{value.getString(it)}.toSet())}}
   edit.apply()
  }
 }
 private fun readManifest(file:File):JSONObject=if(isZip(file))ZipFile(file).use{zip->val entry=zip.getEntry("manifest.json")?:error("В архиве нет manifest.json");require(entry.size<=MAX_JSON);JSONObject(zip.getInputStream(entry).use{bounded(it,MAX_JSON)}.toString(Charsets.UTF_8))}
  else JSONObject(file.inputStream().use{bounded(it,MAX_JSON)}.toString(Charsets.UTF_8))
 private fun readArchive(file:File,stage:File):JSONObject {
  var root:JSONObject?=null;var total=0L;var count=0;val names=mutableSetOf<String>()
  ZipInputStream(BufferedInputStream(file.inputStream())).use{zip->while(true){val entry=zip.nextEntry?:break
   require(++count<=MAX_ARCHIVE_ENTRIES&&names.add(entry.name)){"Некорректный архив"}
   if(entry.name=="manifest.json")root=JSONObject(bounded(zip,MAX_JSON).toString(Charsets.UTF_8))
   else{
    require(entry.name.matches(Regex("photos/[a-zA-Z0-9_-]+[.]jpg"))){"Посторонний файл в архиве"}
    val target=File(stage,entry.name.removePrefix("photos/"));val bytes=bounded(zip,MAX_PHOTO);total+=bytes.size;require(total<=MAX_PHOTO_TOTAL){"Слишком большой архив"};target.writeBytes(bytes)
   };zip.closeEntry()
  }}
  return root?:error("В архиве нет описания данных")
 }
 private fun cleanupPhotos(retained:Set<String>){
  val now=System.currentTimeMillis()
  photosDir.walkTopDown().filter{it.isFile&&it.absolutePath !in retained&&now-it.lastModified()>300_000}.forEach{it.delete()}
 }
 fun discard(file:File){if(file.canonicalPath.startsWith(cacheDir.canonicalPath+File.separator))file.delete()}
 companion object{
  const val BACKUP_VERSION=4;const val BACKUP_FILE_MIME="application/zip";const val BACKUP_FILE_NAME_PREFIX="spisok_ryadom_full"
  private const val MAX_JSON=32*1024*1024;private const val MAX_PHOTO=8*1024*1024
  private const val MAX_PHOTO_TOTAL=450L*1024*1024;private const val MAX_ARCHIVE_ENTRIES=20000
  private fun isZip(file:File)=file.inputStream().use{it.read()==0x50&&it.read()==0x4b}
  private fun bounded(input:InputStream,limit:Int):ByteArray{val out=ByteArrayOutputStream();val buf=ByteArray(16384);while(true){val n=input.read(buf);if(n<0)break;require(out.size()+n<=limit){"Файл слишком большой"};out.write(buf,0,n)};return out.toByteArray()}
 }
}
