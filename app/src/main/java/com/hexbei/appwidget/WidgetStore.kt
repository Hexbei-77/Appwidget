package com.hexbei.appwidget

import android.content.Context
import java.util.Calendar

/**
 * 小组件个性化设置：字号档位 + 动态背景开关
 */
object WidgetStore {

    private const val PREFS = "widget_prefs"
    private const val KEY_FONT_SIZE = "font_size"     // 0=小 1=标准 2=大
    private const val KEY_DYNAMIC_BG = "dynamic_bg"   // true=按小时自动换主题
    private const val KEY_MOTTO = "motto"             // 进度环下方的自定义鼓励文案

    fun getFontSize(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_FONT_SIZE, 1)

    fun setFontSize(context: Context, size: Int) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_FONT_SIZE, size).apply()

    fun getDynamicBg(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DYNAMIC_BG, false)

    fun setDynamicBg(context: Context, on: Boolean) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_DYNAMIC_BG, on).apply()

    /** 根据当前小时选主题：早上暖橙 / 中午绿 / 下午蓝 / 晚上粉 */
    fun themeByHour(): Int {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            h in 6..10 -> 0
            h in 11..13 -> 1
            h in 14..17 -> 2
            else -> 3
        }
    }

    /** 小组件实际该用哪个主题 */
    fun currentThemeIndex(context: Context): Int =
        if (getDynamicBg(context)) themeByHour() else ThemeStore.get(context)

    /** 字号对应的缩放系数：小 0.85 / 标准 1.0 / 大 1.15（给各组件基础字号乘这个系数） */
    fun fontScale(context: Context): Float = when (getFontSize(context)) {
        0 -> 0.85f
        2 -> 1.15f
        else -> 1.0f
    }

    /** 进度环下方文案：用户可自定义，默认"好好学习，天天向上！" */
    fun getMotto(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MOTTO, "好好学习，天天向上！") ?: "好好学习，天天向上！"

    fun setMotto(context: Context, text: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_MOTTO, text).apply()
    }
}
