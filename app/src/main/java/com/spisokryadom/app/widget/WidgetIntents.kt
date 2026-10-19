package com.spisokryadom.app.widget

import android.content.Context
import android.content.Intent
import com.spisokryadom.app.MainActivity

object WidgetIntents {
    const val OPEN="com.spisokryadom.app.widget.OPEN"
    const val ADD="com.spisokryadom.app.widget.ADD"
    fun open(context:Context,add:Boolean=false)=Intent(context,MainActivity::class.java)
        .setAction(if(add)ADD else OPEN).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    fun destination(intent:Intent?):String?=when(intent?.action){ADD->"add";OPEN->"list";else->null}
}
