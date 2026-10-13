package com.hexbei.appwidget

/**
 * 课程配色表：给每门课分配一种主题色（列表色条 / 小组件共用）。
 * 之后做"动态主题"时，会从这里读取配色。
 */
object CourseColors {
    val palette = intArrayOf(
        0xFF5B8DEF.toInt(), // 蓝
        0xFFF2994A.toInt(), // 橙
        0xFF27AE60.toInt(), // 绿
        0xFFEB5757.toInt(), // 红
        0xFF9B51E0.toInt(), // 紫
        0xFF56CCF2.toInt(), // 浅蓝
        0xFFF2C94C.toInt()  // 黄
    )
}
