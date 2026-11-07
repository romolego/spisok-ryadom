package com.spisokryadom.app.domain

/** This input is names only: even a period inside a number is a delimiter. */
object BulkProductInput {
    const val MAX_TEXT=16000
    const val MAX_ROWS=200
    fun split(text:String):List<String> {
        require(text.length<=MAX_TEXT){"Слишком длинный список: до $MAX_TEXT символов"}
        val names=text.split(Regex("[\\r\\n,;.!?•]+"))
            .map{it.trim().removePrefix("- ").removePrefix("– ").trim()}.filter{it.isNotEmpty()}
        require(names.isNotEmpty()){"Введите названия товаров"}
        require(names.size<=MAX_ROWS){"За один раз можно добавить до $MAX_ROWS позиций"}
        require(names.all{it.length<=500&&'\u0000' !in it}){"Название товара: до 500 символов"}
        return names
    }
}
