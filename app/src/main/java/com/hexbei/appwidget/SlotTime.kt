package com.hexbei.appwidget

/**
 * 节次 → 时间映射（大学常见作息）
 * 给小组件显示具体上下课时间用，也供 ScheduleEngine 按真实时间计算。
 *
 * 节次是两节连上的大节：
 *  1-2 节 = 上午第一节（08:00-09:40）
 *  3-4 节 = 上午第二节（10:00-11:40）
 *  5-6 节 = 下午第一节（14:00-15:40）
 *  7-8 节 = 下午第二节（16:00-17:40）
 *  9-10 节 = 晚上课（19:00-20:40）
 */
object SlotTime {

    private val startTimes = mapOf(
        1 to "08:00", 2 to "08:55",
        3 to "10:00", 4 to "10:55",
        5 to "14:00", 6 to "14:55",
        7 to "16:00", 8 to "16:55",
        9 to "19:00", 10 to "19:55"
    )
    private val endTimes = mapOf(
        1 to "08:45", 2 to "09:40",
        3 to "10:45", 4 to "11:40",
        5 to "14:45", 6 to "15:40",
        7 to "16:45", 8 to "17:40",
        9 to "19:45", 10 to "20:40"
    )

    /** 第几节几点开始，返回 (时, 分)；查不到返回 null */
    fun startTime(slot: Int): Pair<Int, Int>? = startTimes[slot]?.let { parse(it) }

    /** 第几节几点结束，返回 (时, 分)；查不到返回 null */
    fun endTime(slot: Int): Pair<Int, Int>? = endTimes[slot]?.let { parse(it) }

    /** 返回 "08:00-09:40"，查不到就退回 "第3-4节" */
    fun range(startSlot: Int, endSlot: Int): String {
        val s = startTimes[startSlot] ?: return "第$startSlot-${endSlot}节"
        val e = endTimes[endSlot] ?: return "第$startSlot-${endSlot}节"
        return "$s-$e"
    }

    private fun parse(time: String): Pair<Int, Int> {
        val p = time.split(":")
        return p[0].toInt() to p[1].toInt()
    }
}
