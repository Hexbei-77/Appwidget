package com.hexbei.appwidget

/**
 * 主题色板：换肤功能的数据源（C6 动态主题）。
 * 每个主题 = 背景色 + 强调色，App 和桌面小组件共用。
 */
object ThemePalette {

    data class Theme(
        val name: String,
        val bg: Int,
        val accent: Int
    )

    val themes = listOf(
        Theme("暖橙", 0xFFFDF3E7.toInt(), 0xFF8A5A2B.toInt()),
        Theme("清新绿", 0xFFEAF2EA.toInt(), 0xFF2D5A2D.toInt()),
        Theme("天空蓝", 0xFFEAF2FB.toInt(), 0xFF2D5A8A.toInt()),
        Theme("奶油粉", 0xFFFDEAF0.toInt(), 0xFF8A2D5A.toInt())
    )

    /** 课程格子用的 8 色配色（周历格子 / 列表色条共用，保证全项目一致） */
    val courseColors = intArrayOf(
        0xFFE57373.toInt(), 0xFFF06292.toInt(), 0xFFBA68C8.toInt(),
        0xFF64B5F6.toInt(), 0xFF4DB6AC.toInt(), 0xFF81C784.toInt(),
        0xFFFFB74D.toInt(), 0xFFA1887F.toInt()
    )
}
