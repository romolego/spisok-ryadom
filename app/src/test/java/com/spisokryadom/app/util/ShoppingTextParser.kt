package com.spisokryadom.app.util

data class ParsedPurchase(val original:String,val name:String,val quantity:String,val unit:String,val explicitQuantity:Boolean,val shopName:String?=null)
object ShoppingTextParser {
    private val explicit=Regex("""^(.+?)\s+[—–=]\s+([-+]?\d+(?:[.,]\d+)?)(?:\s+(.*))?$""")
    private val suffix=Regex("""^(.+?)(?:\s*[—–:=]\s*|\s+)([-+]?\d+(?:[.,]\d+)?)\s*([а-яА-Яa-zA-Z.]+)?$""")
    private val prefix=Regex("""^([-+]?\d+(?:[.,]\d+)?)\s+(.+)$""")
    fun parse(text:String,maxPurchases:Int=200,maxCharacters:Int=50_000):List<ParsedPurchase>{
        require(text.length<=maxCharacters){"Список слишком длинный"}
        val purchases=mutableListOf<ParsedPurchase>()
        var shop:String?=null
        var emptySection=false
        for(raw in text.lineSequence()) {
            val original=raw.trim()
            if(original.isBlank())continue
            val line=original.replace(Regex("""^(?:[-*•]\s+|\d+[.)]\s+)"""),"").trim()
            if(line.endsWith(':')) {
                require(!emptySection){"Добавьте покупки после заголовка «$shop:»"}
                shop=line.dropLast(1).trim()
                require(!shop.isNullOrBlank()){"Перед двоеточием нужно название магазина"}
                emptySection=true
                continue
            }
            emptySection=false
            val s=explicit.matchEntire(line)?:suffix.matchEntire(line)
            val p=if(s==null)prefix.matchEntire(line)else null
            purchases+=when {
                s!=null->ParsedPurchase(original,s.groupValues[1].trim(),s.groupValues[2],Quantities.unit(s.groupValues[3]),true,shop)
                p!=null->ParsedPurchase(original,p.groupValues[2].trim(),p.groupValues[1],"",true,shop)
                else->ParsedPurchase(original,line,"1","",false,shop)
            }
            require(purchases.size<=maxPurchases){"За один раз можно обработать до $maxPurchases покупок"}
        }
        require(!emptySection){"Добавьте покупки после заголовка «$shop:»"}
        return purchases
    }
}
