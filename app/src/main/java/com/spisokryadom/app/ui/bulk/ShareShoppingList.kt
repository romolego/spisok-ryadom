package com.spisokryadom.app.ui.bulk

import android.content.Context
import android.content.Intent

fun shoppingListShareIntent(text:String):Intent=Intent.createChooser(
    Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT,"Список покупок")
        .putExtra(Intent.EXTRA_TEXT,text),"Поделиться списком")

fun shareShoppingList(context:Context,text:String) {
    context.startActivity(shoppingListShareIntent(text))
}
