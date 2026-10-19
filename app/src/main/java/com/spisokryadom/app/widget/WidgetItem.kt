package com.spisokryadom.app.widget

/** Headers have their own namespace and never carry a purchase action token. */
sealed interface WidgetItem {
    val stableId: Long
    data class Purchase(val row: WidgetPurchase) : WidgetItem { override val stableId = row.id }
    data class Header(val key: String, val title: String, val subtitle: String? = null, val depth: Int = 0) : WidgetItem {
        override val stableId: Long = java.nio.ByteBuffer.wrap(
            java.security.MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
        ).long or Long.MIN_VALUE
    }
}

data class WidgetFrame(val settings: WidgetSettings = WidgetSettings(), val items: List<WidgetItem> = emptyList())
