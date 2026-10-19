package com.spisokryadom.app.util

/** Serializable color vocabulary, shared by forms, database validation and backup import. */
object AppearanceColors {
    private val presets=mapOf("green" to 0xFF52865B.toInt(),"yellow" to 0xFFAD841F.toInt(),
        "blue" to 0xFF4B81A5.toInt(),"rose" to 0xFFA96D82.toInt(),"violet" to 0xFF8670A8.toInt())
    fun argb(key:String):Int?=presets[key]?:key.takeIf{it.matches(Regex("#[0-9a-fA-F]{6}"))}
        ?.drop(1)?.toLong(16)?.let{(0xFF000000L or it).toInt()}
    fun validOptional(key:String)=key.isEmpty()||argb(key)!=null
}
