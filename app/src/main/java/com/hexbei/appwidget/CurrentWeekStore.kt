package com.hexbei.appwidget

import android.content.Context

/**
 * 保存"当前是第几周"，方便用户校准，也方便小组件/主界面读取。
 * 用 SharedPreferences 简单存储；后续换肤设置页会复用这个偏好。
 */
object CurrentWeekStore {

    private const val PREFS = "app_prefs"
    private const val KEY = "current_week"

    fun get(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY, 1)

    fun set(context: Context, week: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY, week).apply()
    }
}
