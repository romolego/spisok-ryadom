package com.spisokryadom.app.data.repository

import androidx.room.withTransaction
import com.spisokryadom.app.data.db.AppDatabase
import com.spisokryadom.app.data.entity.*
import com.spisokryadom.app.util.FuzzySearch
import com.spisokryadom.app.util.Quantities
import com.spisokryadom.app.domain.BulkProductInput

data class BulkProductRow(val key:Int,val source:String,val name:String=source,val productId:Long?=null,
    val stableId:String?=null,val resolved:Boolean=false,val quantity:String="1")

class BulkPurchaseRepository(private val db:AppDatabase,private val basket:ShoppingListRepository,private val locations:LocationRepository) {
    suspend fun cityId()=locations.currentId()
    suspend fun product(id:Long)=com.spisokryadom.app.domain.variantNames(db.productDao().getAllSync(),db.productVariantDao().variants()).find{it.id==id&&!it.isTemporary}
    suspend fun committed(token:String)=db.productInputDao().run(token)!=null
    suspend fun candidates(query:String,cityId:Long):List<ProductEntity> {
        val variants=db.productVariantDao().variants()
        val disabled=db.productVariantDao().cities().filter{it.cityId==cityId&&!it.enabled}.map{it.productId}.toSet()
        val products=com.spisokryadom.app.domain.variantNames(db.productDao().getAllSync().filter{!it.isTemporary&&it.id !in disabled},variants)
        val ranked=FuzzySearch.rank(products,query,{it.name})
        val words=FuzzySearch.normalize(query).split(Regex("[\\s-]+" )).filter{it.length>=3}.distinct()
        val related=if(words.size>1)words.flatMap{word->FuzzySearch.rank(products,word,{it.name})}else emptyList()
        return (ranked+related).distinctBy{it.id}
    }
    suspend fun preview(text:String,cityId:Long):List<BulkProductRow> = db.withTransaction {
        BulkProductInput.split(text).mapIndexed{index,name->
            val exact=candidates(name,cityId).filter{FuzzySearch.normalize(it.name)==FuzzySearch.normalize(name)}.singleOrNull()
            BulkProductRow(index,name,exact?.name?:name,exact?.id,exact?.stableId,exact!=null)
        }
    }
    suspend fun add(token:String,rows:List<BulkProductRow>,cityId:Long,choice:ShopChoice,shopId:Long?,branchId:Long?,saveNew:Boolean,fallbackUnit:String,expectedGeneration:Long):Boolean =
        ProductEditorTransactions.inGeneration(expectedGeneration){db.withTransaction {
            require(token.length==36&&runCatching{java.util.UUID.fromString(token)}.isSuccess){"Откройте предпросмотр заново"}
            if(db.productInputDao().run(token)!=null)return@withTransaction false
            require(cityId==locations.currentId()){"Город изменился. Подготовьте список заново"}
            require(rows.isNotEmpty()&&rows.size<=BulkProductInput.MAX_ROWS&&rows.map{it.key}.distinct().size==rows.size){"Проверьте состав списка"}
            rows.forEach{row->
                require(row.resolved){"Выберите товар для «${row.source}»"}
                val quantity=Quantities.parse(row.quantity)?:error("Проверьте количество для «${row.source}»")
                val id=row.productId
                if(id!=null){
                    val product=db.productDao().getById(id)?:error("Товар «${row.name}» удалён. Выберите другой")
                    require(product.stableId==row.stableId&&!product.isTemporary){"Товар изменился. Выберите его заново"}
                    basket.addProduct(id,quantity,choice=choice,shopId=shopId,branchId=branchId,exactVariant=true)
                }else{
                    require(row.name.isNotBlank()&&row.name.length<=500&&'\u0000' !in row.name){"Проверьте название нового товара"}
                    val unit=ProductInputRepository(db).suggest(row.name).unit?:fallbackUnit
                    basket.addNewProduct(row.name,quantity,unit,saveNew,choice,shopId,branchId)
                }
            }
            db.productInputDao().run(BulkPurchaseRun(token,System.currentTimeMillis()))
            true
        }}
}
