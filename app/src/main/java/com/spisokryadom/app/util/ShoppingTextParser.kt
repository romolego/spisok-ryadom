package com.spisokryadom.app.util

data class ParsedPurchase(val original:String,val name:String,val quantity:String,val unit:String,val explicitQuantity:Boolean)
object ShoppingTextParser {
    private val suffix=Regex("""^(.+?)(?:\s*[—–:=]\s*|\s+)([-+]?\d+(?:[.,]\d+)?)\s*([а-яА-Яa-zA-Z.]+)?$""")
    private val prefix=Regex("""^([-+]?\d+(?:[.,]\d+)?)\s+(.+)$""")
    fun parse(text:String):List<ParsedPurchase>{
        require(text.length<=50_000){"Список слишком длинный"}
        val lines=text.lines().map{it.trim()}.filter{it.isNotBlank()}
        require(lines.size<=200){"За один раз можно добавить до 200 строк"}
        return lines.map{original->
            val line=original.replace(Regex("""^(?:[-*•]\s+|\d+[.)]\s+)"""),"").trim()
            val s=suffix.matchEntire(line)
            if(s!=null)ParsedPurchase(original,s.groupValues[1].trim(),s.groupValues[2],Quantities.unit(s.groupValues[3]),true)
            else {
                val p=prefix.matchEntire(line)
                if(p!=null)ParsedPurchase(original,p.groupValues[2].trim(),p.groupValues[1],"",true)
                else ParsedPurchase(original,line,"1","",false)
            }
        }
    }
}
