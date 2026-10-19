package com.spisokryadom.app.data.backup
import com.spisokryadom.app.data.entity.*
import org.json.JSONObject
import org.json.JSONArray
internal data class BackupSnapshot(
 val products:List<ProductEntity> = emptyList(),
 val groups:List<ProductGroupEntity> = emptyList(),
 val recipients:List<RecipientEntity> = emptyList(),
 val shops:List<ShopEntity> = emptyList(),
 val departments:List<ShopDepartmentEntity> = emptyList(),
 val links:List<ProductShopLinkEntity> = emptyList(),
 val entries:List<ShoppingListEntryEntity> = emptyList(),
 val units:List<UnitEntity> = emptyList(),
 val cities:List<CityEntity> = emptyList(),
 val preferences:JSONObject = JSONObject(),
 val branches:List<ShopBranchEntity> = emptyList(),
 val availability:List<ProductBranchAvailabilityEntity> = emptyList(),
 val networks:List<RetailNetworkEntity> = emptyList(),
 val venues:List<VenueEntity> = emptyList(),
 val departmentTypes:List<DepartmentTypeEntity> = emptyList(),
 val branchOrder:List<BranchDepartmentOrderEntity> = emptyList(),
 val branchDepartments:List<ProductBranchDepartmentEntity> = emptyList(),
 val branchPriorities:List<ProductBranchPriorityEntity> = emptyList()
)
internal object BackupJson {
 fun encode(value:ProductEntity):JSONObject = JSONObject().apply {
  put("id",value.id)
  put("name",value.name)
  put("productGroupId",value.productGroupId ?: JSONObject.NULL)
  put("recipientId",value.recipientId ?: JSONObject.NULL)
  put("defaultUnit",value.defaultUnit ?: JSONObject.NULL)
  put("defaultQuantity",value.defaultQuantity ?: JSONObject.NULL)
  put("note",value.note ?: JSONObject.NULL)
  put("purchaseType",value.purchaseType)
  put("sellerUrl",value.sellerUrl ?: JSONObject.NULL)
  put("productUrl",value.productUrl ?: JSONObject.NULL)
  put("priceValue",value.priceValue ?: JSONObject.NULL)
  put("priceDate",value.priceDate ?: JSONObject.NULL)
  put("isTemporary",value.isTemporary)
  put("stableId",value.stableId)
  put("preferCityShopOrder",value.preferCityShopOrder)
  put("isFavorite",value.isFavorite);put("accent",value.accent)
 }
 fun products(o:JSONObject):ProductEntity = ProductEntity(
  preferCityShopOrder=o.optBoolean("preferCityShopOrder",false),
  isFavorite=o.optBoolean("isFavorite",false),accent=o.optString("accent",""),
  id=if(o.has("id")&&!o.isNull("id"))o.getLong("id") else 0,
  name=if(o.has("name")&&!o.isNull("name"))o.getString("name") else "",
  productGroupId=if(o.has("productGroupId")&&!o.isNull("productGroupId"))o.getLong("productGroupId") else null,
  recipientId=if(o.has("recipientId")&&!o.isNull("recipientId"))o.getLong("recipientId") else null,
  defaultUnit=if(o.has("defaultUnit")&&!o.isNull("defaultUnit"))o.getString("defaultUnit") else null,
  defaultQuantity=if(o.has("defaultQuantity")&&!o.isNull("defaultQuantity"))o.getDouble("defaultQuantity") else null,
  note=if(o.has("note")&&!o.isNull("note"))o.getString("note") else null,
  purchaseType=if(o.has("purchaseType")&&!o.isNull("purchaseType"))o.getString("purchaseType") else "offline",
  sellerUrl=if(o.has("sellerUrl")&&!o.isNull("sellerUrl"))o.getString("sellerUrl") else null,
  productUrl=if(o.has("productUrl")&&!o.isNull("productUrl"))o.getString("productUrl") else null,
  photoUri=null,
  priceValue=if(o.has("priceValue")&&!o.isNull("priceValue"))o.getDouble("priceValue") else null,
  priceDate=if(o.has("priceDate")&&!o.isNull("priceDate"))o.getString("priceDate") else null,
  isTemporary=if(o.has("isTemporary")&&!o.isNull("isTemporary"))o.getBoolean("isTemporary") else false,
  stableId=if(o.has("stableId")&&!o.isNull("stableId"))o.getString("stableId") else legacyProductIdentity(o)
 )
 // Old files have no database identity. Re-reading the same source row must nevertheless
 // be idempotent. Include its source ID and all metadata, including an embedded photo;
 // a changed old row is conservatively a new identity, never a name-only overwrite.
 private fun legacyProductIdentity(o:JSONObject):String = java.util.UUID.nameUUIDFromBytes(
  ("spisok-ryadom-legacy-product-v1:"+canonical(o)).toByteArray(Charsets.UTF_8)
 ).toString()
 private fun canonical(value:Any?):String=when(value){
  is JSONObject->value.keys().asSequence().toList().sorted().joinToString(prefix="{",postfix="}"){
   JSONObject.quote(it)+":"+canonical(value.get(it))
  }
  is JSONArray->(0 until value.length()).joinToString(prefix="[",postfix="]"){canonical(value.get(it))}
  null,JSONObject.NULL->"null"
  is String->JSONObject.quote(value)
  else->value.toString()
 }
 fun encode(value:ProductGroupEntity):JSONObject = JSONObject().apply {
  put("accent",value.accent);put("iconKey",value.iconKey)
  put("id",value.id)
  put("name",value.name)
  put("displayOrder",value.displayOrder)
 }
 fun groups(o:JSONObject):ProductGroupEntity = ProductGroupEntity(
  accent=o.optString("accent",""),iconKey=o.optString("iconKey",""),
  id=if(o.has("id")&&!o.isNull("id"))o.getLong("id") else 0,
  name=if(o.has("name")&&!o.isNull("name"))o.getString("name") else "",
  displayOrder=if(o.has("displayOrder")&&!o.isNull("displayOrder"))o.getInt("displayOrder") else 0
 )
 fun encode(value:RecipientEntity):JSONObject = JSONObject().apply {
  put("accent",value.accent);put("iconKey",value.iconKey)
  put("id",value.id)
  put("name",value.name)
  put("displayOrder",value.displayOrder)
 }
 fun recipients(o:JSONObject):RecipientEntity = RecipientEntity(
  accent=o.optString("accent",""),iconKey=o.optString("iconKey",""),
  id=if(o.has("id")&&!o.isNull("id"))o.getLong("id") else 0,
  name=if(o.has("name")&&!o.isNull("name"))o.getString("name") else "",
  displayOrder=if(o.has("displayOrder")&&!o.isNull("displayOrder"))o.getInt("displayOrder") else 0
 )
 fun encode(value:ShopEntity):JSONObject = JSONObject().apply {
  put("id",value.id)
  put("name",value.name)
  put("address",value.address ?: JSONObject.NULL)
  put("note",value.note ?: JSONObject.NULL)
  put("displayOrder",value.displayOrder)
  put("showUrgentInCollapsed",value.showUrgentInCollapsed ?: JSONObject.NULL)
  put("boostUrgentShop",value.boostUrgentShop ?: JSONObject.NULL)
  put("manualOrderOverride",value.manualOrderOverride)
  put("cityId",value.cityId)
  put("networkId",value.networkId?:JSONObject.NULL)
 }
 fun shops(o:JSONObject):ShopEntity = ShopEntity(
  id=if(o.has("id")&&!o.isNull("id"))o.getLong("id") else 0,
  name=if(o.has("name")&&!o.isNull("name"))o.getString("name") else "",
  address=if(o.has("address")&&!o.isNull("address"))o.getString("address") else null,
  note=if(o.has("note")&&!o.isNull("note"))o.getString("note") else null,
  displayOrder=if(o.has("displayOrder")&&!o.isNull("displayOrder"))o.getInt("displayOrder") else 0,
  showUrgentInCollapsed=if(o.has("showUrgentInCollapsed")&&!o.isNull("showUrgentInCollapsed"))o.getBoolean("showUrgentInCollapsed") else null,
  boostUrgentShop=if(o.has("boostUrgentShop")&&!o.isNull("boostUrgentShop"))o.getBoolean("boostUrgentShop") else null,
  manualOrderOverride=if(o.has("manualOrderOverride")&&!o.isNull("manualOrderOverride"))o.getBoolean("manualOrderOverride") else false,
  cityId=if(o.has("cityId")&&!o.isNull("cityId"))o.getLong("cityId") else 1,
  networkId=if(o.has("networkId")&&!o.isNull("networkId"))o.getLong("networkId")else null
 )
 fun encode(value:ShopDepartmentEntity):JSONObject = JSONObject().apply {
  put("id",value.id)
  put("shopId",value.shopId)
  put("typeId",value.typeId)
  put("name",value.name)
  put("displayOrder",value.displayOrder)
 }
 fun departments(o:JSONObject):ShopDepartmentEntity = ShopDepartmentEntity(
  typeId=o.optLong("typeId",0),
  id=if(o.has("id")&&!o.isNull("id"))o.getLong("id") else 0,
  shopId=if(o.has("shopId")&&!o.isNull("shopId"))o.getLong("shopId") else 0,
  name=if(o.has("name")&&!o.isNull("name"))o.getString("name") else "",
  displayOrder=if(o.has("displayOrder")&&!o.isNull("displayOrder"))o.getInt("displayOrder") else 0
 )
 fun encode(value:ProductShopLinkEntity):JSONObject = JSONObject().apply {
  put("id",value.id)
  put("productId",value.productId)
  put("shopId",value.shopId)
  put("restrictBranches",value.restrictBranches)
  put("priority",value.priority)
  put("departmentId",value.departmentId ?: JSONObject.NULL)
 }
 fun links(o:JSONObject):ProductShopLinkEntity = ProductShopLinkEntity(
  restrictBranches=o.optBoolean("restrictBranches",false),
  id=if(o.has("id")&&!o.isNull("id"))o.getLong("id") else 0,
  productId=if(o.has("productId")&&!o.isNull("productId"))o.getLong("productId") else 0,
  shopId=if(o.has("shopId")&&!o.isNull("shopId"))o.getLong("shopId") else 0,
  priority=if(o.has("priority")&&!o.isNull("priority"))o.getInt("priority") else 1,
  departmentId=if(o.has("departmentId")&&!o.isNull("departmentId"))o.getLong("departmentId") else null
 )
 fun encode(value:ShoppingListEntryEntity):JSONObject = JSONObject().apply {
  put("awaitingShopCityId",value.awaitingShopCityId?:JSONObject.NULL);put("explicitNoShop",value.explicitNoShop)
  put("id",value.id)
  put("productId",value.productId)
  put("quantity",value.quantity)
  put("unit",value.unit)
  put("assignedBranchId",value.assignedBranchId ?: JSONObject.NULL)
  put("assignedShopId",value.assignedShopId ?: JSONObject.NULL)
  put("assignedDepartmentId",value.assignedDepartmentId ?: JSONObject.NULL)
  put("isBought",value.isBought)
  put("isUrgent",value.isUrgent)
  put("note",value.note ?: JSONObject.NULL)
  put("comment",value.comment ?: JSONObject.NULL)
  put("createdAt",value.createdAt)
  put("updatedAt",value.updatedAt)
 }
 fun entries(o:JSONObject):ShoppingListEntryEntity = ShoppingListEntryEntity(
  explicitNoShop=o.optBoolean("explicitNoShop",false),awaitingShopCityId=if(o.has("awaitingShopCityId")&&!o.isNull("awaitingShopCityId"))o.getLong("awaitingShopCityId")else null,
  assignedBranchId=if(o.has("assignedBranchId")&&!o.isNull("assignedBranchId"))o.getLong("assignedBranchId")else null,
  id=if(o.has("id")&&!o.isNull("id"))o.getLong("id") else 0,
  productId=if(o.has("productId")&&!o.isNull("productId"))o.getLong("productId") else 0,
  quantity=if(o.has("quantity")&&!o.isNull("quantity"))o.getDouble("quantity") else 1.0,
  unit=if(o.has("unit")&&!o.isNull("unit"))o.getString("unit") else "",
  assignedShopId=if(o.has("assignedShopId")&&!o.isNull("assignedShopId"))o.getLong("assignedShopId") else null,
  assignedDepartmentId=if(o.has("assignedDepartmentId")&&!o.isNull("assignedDepartmentId"))o.getLong("assignedDepartmentId") else null,
  isBought=if(o.has("isBought")&&!o.isNull("isBought"))o.getBoolean("isBought") else false,
  isUrgent=if(o.has("isUrgent")&&!o.isNull("isUrgent"))o.getBoolean("isUrgent") else false,
  note=if(o.has("note")&&!o.isNull("note"))o.getString("note") else null,
  comment=if(o.has("comment")&&!o.isNull("comment"))o.getString("comment") else null,
  createdAt=if(o.has("createdAt")&&!o.isNull("createdAt"))o.getLong("createdAt") else System.currentTimeMillis(),
  updatedAt=if(o.has("updatedAt")&&!o.isNull("updatedAt"))o.getLong("updatedAt") else System.currentTimeMillis()
 )
 fun encode(value:UnitEntity):JSONObject = JSONObject().apply {
  put("id",value.id)
  put("name",value.name)
  put("displayOrder",value.displayOrder)
 }
 fun units(o:JSONObject):UnitEntity = UnitEntity(
  id=if(o.has("id")&&!o.isNull("id"))o.getLong("id") else 0,
  name=if(o.has("name")&&!o.isNull("name"))o.getString("name") else "",
  displayOrder=if(o.has("displayOrder")&&!o.isNull("displayOrder"))o.getInt("displayOrder") else 0
 )
 fun encode(value:CityEntity):JSONObject = JSONObject().apply {
  put("id",value.id)
  put("name",value.name)
 }
 fun cities(o:JSONObject):CityEntity = CityEntity(
  id=if(o.has("id")&&!o.isNull("id"))o.getLong("id") else 0,
  name=if(o.has("name")&&!o.isNull("name"))o.getString("name") else ""
 )
 fun encode(value:ShopBranchEntity)=JSONObject().put("id",value.id).put("shopId",value.shopId).put("address",value.address).put("displayOrder",value.displayOrder).put("note",value.note?:JSONObject.NULL).put("venueId",value.venueId?:JSONObject.NULL)
 fun branches(o:JSONObject)=ShopBranchEntity(o.getLong("id"),o.getLong("shopId"),o.getString("address"),o.optInt("displayOrder",0),if(o.isNull("note"))null else o.optString("note").ifBlank{null},venueId=if(o.has("venueId")&&!o.isNull("venueId"))o.getLong("venueId")else null)
 fun encode(value:ProductBranchAvailabilityEntity)=JSONObject().put("productId",value.productId).put("branchId",value.branchId).put("shopId",value.shopId)
 fun availability(o:JSONObject)=ProductBranchAvailabilityEntity(o.getLong("productId"),o.getLong("branchId"),o.optLong("shopId",0))
 fun encode(v:RetailNetworkEntity)=JSONObject().put("id",v.id).put("name",v.name)
 fun encode(v:VenueEntity)=JSONObject().put("id",v.id).put("cityId",v.cityId).put("name",v.name).put("address",v.address?:JSONObject.NULL).put("note",v.note?:JSONObject.NULL)
 fun encode(v:DepartmentTypeEntity)=JSONObject().put("id",v.id).put("name",v.name).put("normalizedName",v.normalizedName).put("displayOrder",v.displayOrder).put("accent",v.accent).put("iconKey",v.iconKey)
 fun encode(v:BranchDepartmentOrderEntity)=JSONObject().put("branchId",v.branchId).put("typeId",v.typeId).put("displayOrder",v.displayOrder)
 fun encode(v:ProductBranchDepartmentEntity)=JSONObject().put("productId",v.productId).put("branchId",v.branchId).put("shopId",v.shopId).put("typeId",v.typeId)
 fun encode(v:ProductBranchPriorityEntity)=JSONObject().put("productId",v.productId).put("branchId",v.branchId).put("shopId",v.shopId).put("displayOrder",v.displayOrder)
 fun manifest(s:BackupSnapshot):JSONObject=JSONObject().put("version",BackupManager.BACKUP_VERSION).put("kind","full").put("exportedAt",java.time.LocalDateTime.now().toString()).put("preferences",s.preferences).put("data",JSONObject().apply {
  put("productBranchPriorities",JSONArray().also{a->s.branchPriorities.forEach{a.put(encode(it))}})
  put("branchDepartmentOrder",JSONArray().also{a->s.branchOrder.forEach{a.put(encode(it))}})
  put("productBranchDepartments",JSONArray().also{a->s.branchDepartments.forEach{a.put(encode(it))}})
  put("retailNetworks",JSONArray().also{a->s.networks.forEach{a.put(encode(it))}})
  put("venues",JSONArray().also{a->s.venues.forEach{a.put(encode(it))}})
  put("departmentTypes",JSONArray().also{a->s.departmentTypes.forEach{a.put(encode(it))}})
  put("shopBranches",JSONArray().also{a->s.branches.forEach{a.put(encode(it))}})
  put("productBranchAvailability",JSONArray().also{a->s.availability.forEach{a.put(encode(it))}})
  put("products",JSONArray().also{a->s.products.forEach{a.put(encode(it))}})
  put("productGroups",JSONArray().also{a->s.groups.forEach{a.put(encode(it))}})
  put("recipients",JSONArray().also{a->s.recipients.forEach{a.put(encode(it))}})
  put("shops",JSONArray().also{a->s.shops.forEach{a.put(encode(it))}})
  put("shopDepartments",JSONArray().also{a->s.departments.forEach{a.put(encode(it))}})
  put("productShopLinks",JSONArray().also{a->s.links.forEach{a.put(encode(it))}})
  put("shoppingListEntries",JSONArray().also{a->s.entries.forEach{a.put(encode(it))}})
  put("units",JSONArray().also{a->s.units.forEach{a.put(encode(it))}})
  put("cities",JSONArray().also{a->s.cities.forEach{a.put(encode(it))}})
 })
 fun upgradeLegacy(s:BackupSnapshot):BackupSnapshot {
  val types=linkedMapOf<String,DepartmentTypeEntity>()
  val deps=s.departments.map{d->
   val key=com.spisokryadom.app.util.FuzzySearch.normalize(d.name)
   val type=types.getOrPut(key){DepartmentTypeEntity(d.id,d.name.trim(),key)}
   d.copy(typeId=type.id,name=type.name)
  }
  val branches=s.branches.associateBy{it.id}
  return s.copy(departments=deps,departmentTypes=types.values.toList(),availability=s.availability.map{it.copy(shopId=branches[it.branchId]?.shopId?:0)})
 }
 fun read(root:JSONObject):BackupSnapshot {
 val d=root.getJSONObject("data")
 return BackupSnapshot(
  products=d.optJSONArray("products")?.let{a->(0 until a.length()).map{products(a.getJSONObject(it))}} ?: emptyList(),
  groups=d.optJSONArray("productGroups")?.let{a->(0 until a.length()).map{groups(a.getJSONObject(it))}} ?: emptyList(),
  recipients=d.optJSONArray("recipients")?.let{a->(0 until a.length()).map{recipients(a.getJSONObject(it))}} ?: emptyList(),
  shops=d.optJSONArray("shops")?.let{a->(0 until a.length()).map{shops(a.getJSONObject(it))}} ?: emptyList(),
  departments=d.optJSONArray("shopDepartments")?.let{a->(0 until a.length()).map{departments(a.getJSONObject(it))}} ?: emptyList(),
  links=d.optJSONArray("productShopLinks")?.let{a->(0 until a.length()).map{links(a.getJSONObject(it))}} ?: emptyList(),
  entries=d.optJSONArray("shoppingListEntries")?.let{a->(0 until a.length()).map{entries(a.getJSONObject(it))}} ?: emptyList(),
  units=d.optJSONArray("units")?.let{a->(0 until a.length()).map{units(a.getJSONObject(it))}} ?: emptyList(),
  cities=d.optJSONArray("cities")?.let{a->(0 until a.length()).map{cities(a.getJSONObject(it))}} ?: emptyList(),
  branches=d.optJSONArray("shopBranches")?.let{a->(0 until a.length()).map{branches(a.getJSONObject(it))}}?:emptyList(),
  availability=d.optJSONArray("productBranchAvailability")?.let{a->(0 until a.length()).map{availability(a.getJSONObject(it))}}?:emptyList(),
  branchOrder=d.optJSONArray("branchDepartmentOrder")?.let{a->(0 until a.length()).map{a.getJSONObject(it).let{o->BranchDepartmentOrderEntity(o.getLong("branchId"),o.getLong("typeId"),o.getInt("displayOrder"))}}}?:emptyList(),
  branchDepartments=d.optJSONArray("productBranchDepartments")?.let{a->(0 until a.length()).map{a.getJSONObject(it).let{o->ProductBranchDepartmentEntity(o.getLong("productId"),o.getLong("branchId"),o.getLong("shopId"),o.getLong("typeId"))}}}?:emptyList(),
  networks=d.optJSONArray("retailNetworks")?.let{a->(0 until a.length()).map{a.getJSONObject(it).let{o->RetailNetworkEntity(o.getLong("id"),o.getString("name"))}}}?:emptyList(),
  venues=d.optJSONArray("venues")?.let{a->(0 until a.length()).map{a.getJSONObject(it).let{o->VenueEntity(o.getLong("id"),o.getLong("cityId"),o.getString("name"),if(o.isNull("address"))null else o.optString("address"),if(o.isNull("note"))null else o.optString("note"))}}}?:emptyList(),
  departmentTypes=d.optJSONArray("departmentTypes")?.let{a->(0 until a.length()).map{a.getJSONObject(it).let{o->DepartmentTypeEntity(o.getLong("id"),o.getString("name"),o.getString("normalizedName"),o.optInt("displayOrder",0),o.optString("accent",""),o.optString("iconKey",""))}}}?:emptyList(),
  branchPriorities=d.optJSONArray("productBranchPriorities")?.let{a->(0 until a.length()).map{a.getJSONObject(it).let{o->ProductBranchPriorityEntity(o.getLong("productId"),o.getLong("branchId"),o.getLong("shopId"),o.getInt("displayOrder"))}}}?:emptyList(),
  preferences=root.optJSONObject("preferences") ?: JSONObject()
 )
 }
}
