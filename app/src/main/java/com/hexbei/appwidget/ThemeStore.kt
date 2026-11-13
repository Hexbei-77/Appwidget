package com.hexbei.appwidget

import android.content.Context

/** 记住用户选的主题（当前是第几个颜色） */
object ThemeStore {

    private const val PREFS = "theme_prefs"
    private const val KEY_COLOR = "theme_color_index"

    fun get(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_COLOR, 0)

    fun set(context: Context, index: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_COLOR, index)
            .apply()
    }
}
