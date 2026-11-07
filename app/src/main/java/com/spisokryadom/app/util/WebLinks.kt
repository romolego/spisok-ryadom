package com.spisokryadom.app.util

import java.net.URI

object WebLinks {
    private val addresses=Regex("(?:https?://|www\\.)[^\\s\\p{Z}<>\\\"«»“”]+",RegexOption.IGNORE_CASE)
    fun extract(text:String):List<String> {
        val trimmed=text.trim()
        val source=if(trimmed.matches(Regex("[\\p{L}\\d][\\p{L}\\d.-]*[.][\\p{L}]{2,}(?::[0-9]+)?(?:[/?#][^\\s<>]*)?")))"https://$trimmed"else text
        return addresses.findAll(source).mapNotNull{found->
        var value=found.value.trimEnd('.',',',';',':','!','?','\'', '’')
        while(value.endsWith(')')&&value.count{it==')'}>value.count{it=='('})value=value.dropLast(1)
        while(value.endsWith(']')&&value.count{it==']'}>value.count{it=='['})value=value.dropLast(1)
        if(value.startsWith("www.",ignoreCase=true))value="https://$value"
        runCatching{val uri=URI(value);value.takeIf{uri.scheme.lowercase() in setOf("http","https")&&!uri.rawAuthority.isNullOrBlank()}}.getOrNull()
    }.distinct().toList()
    }
}
