package com.spisokryadom.app.ui.bulk

import android.content.Context
import android.content.Intent
import com.spisokryadom.app.util.messageShareIntent
import com.spisokryadom.app.util.messageShareUsesFile

/** Pure small-message factory retained for callers that do not have a Context. */
fun shoppingListShareIntent(text:String):Intent {
    require(!messageShareUsesFile(text)) { "Для отправки большого списка нужен контекст приложения" }
    return Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT,"Список покупок")
        .putExtra(Intent.EXTRA_TEXT,text),"Поделиться списком")
}

fun shoppingListShareIntent(context:Context,text:String):Intent = Intent.createChooser(
    messageShareIntent(context,"Список покупок",text),"Поделиться списком")

fun shareShoppingList(context:Context,text:String) {
    context.startActivity(shoppingListShareIntent(context,text))
}
