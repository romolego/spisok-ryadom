package com.spisokryadom.app.ui.goallists

import android.content.Context
import android.content.Intent
import com.spisokryadom.app.domain.GoalListTransfer
import com.spisokryadom.app.util.*

internal const val GOAL_SHARE_INLINE_CHARS = MESSAGE_SHARE_INLINE_CHARS
internal fun goalShareUsesFile(text: String): Boolean = messageShareUsesFile(text)
internal fun goalShareIntent(context: Context, title: String, text: String): Intent {
    require(text.toByteArray(Charsets.UTF_8).size <= GoalListTransfer.MAX_INPUT_BYTES) { "Список больше 1 МиБ" }
    return messageShareIntent(context, title, text)
}
