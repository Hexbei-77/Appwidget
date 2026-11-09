package com.hexbei.appwidget

import com.hexbei.appwidget.data.Course
import java.util.Calendar

/**
 * 课程表核心业务逻辑引擎（自定义逻辑模块，评优点）
 *
 * 核心能力：给定"当前周 + 星期几"，判断哪些课今天上、下一节是哪门、本周进度。
 * 小组件和主界面都靠它计算，避免逻辑写散。
 */
object ScheduleEngine {

    /** 判断一门课在当前周、当前星期是否开课 */
    fun isCourseOn(course: Course, currentWeek: Int, weekday: Int): Boolean {
        // 1. 星期不匹配 → 不上
        if (course.weekday != weekday) return false
        // 2. 不在课程起止周范围内 → 不上
        if (currentWeek < course.startWeek || currentWeek > course.endWeek) return false
        // 3. 单双周判断
        return when (course.oddEven) {
            1 -> currentWeek % 2 == 1   // 单周才上
            2 -> currentWeek % 2 == 0   // 双周才上
            else -> true                // 每周都上
        }
    }

    /** 今天要上的课（按开始节次排序） */
    fun coursesToday(courses: List<Course>, currentWeek: Int, weekday: Int): List<Course> =
        courses.filter { isCourseOn(it, currentWeek, weekday) }.sortedBy { it.startSlot }

    /** 今天的星期几：1=周一 .. 7=周日 */
    fun todayWeekday(): Int {
        val day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) // 1=周日..7=周六
        return (day + 5) % 7 + 1
    }

    /** 某节课今天几点开始（毫秒）；时间表查不到返回 null */
    fun slotStartMillis(slot: Int, now: Calendar): Long? =
        SlotTime.startTime(slot)?.let { (h, m) -> todayAtMillis(now, h, m) }

    /** 某节课今天几点结束（毫秒）；时间表查不到返回 null */
    fun slotEndMillis(slot: Int, now: Calendar): Long? =
        SlotTime.endTime(slot)?.let { (h, m) -> todayAtMillis(now, h, m) }

    /** 今天某一时刻的毫秒值 */
    private fun todayAtMillis(now: Calendar, hour: Int, minute: Int): Long {
        val c = now.clone() as Calendar
        c.set(Calendar.HOUR_OF_DAY, hour)
        c.set(Calendar.MINUTE, minute)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    /** 正在上的课：开始 ≤ 现在 < 结束（按真实节次时间） */
    fun ongoingCourse(courses: List<Course>, currentWeek: Int, weekday: Int, now: Calendar): Course? =
        coursesToday(courses, currentWeek, weekday).firstOrNull { course ->
            val s = slotStartMillis(course.startSlot, now) ?: return@firstOrNull false
            val e = slotEndMillis(course.endSlot, now) ?: return@firstOrNull false
            now.timeInMillis in s until e
        }

    /** 下一节课：按真实时间，开始时间晚于现在的最早一节 */
    fun nextCourseByTime(
        courses: List<Course>,
        currentWeek: Int,
        weekday: Int,
        now: Calendar
    ): Course? =
        coursesToday(courses, currentWeek, weekday)
            .filter { course ->
                val s = slotStartMillis(course.startSlot, now) ?: return@filter false
                s > now.timeInMillis
            }
            .minByOrNull { it.startSlot }

    /** 距某节课开始还剩多少分钟（已开始则为负） */
    fun minutesUntilStart(course: Course, now: Calendar): Int {
        val s = slotStartMillis(course.startSlot, now) ?: return -1
        return ((s - now.timeInMillis) / 60000).toInt()
    }

    /** 距某节课下课还剩多少分钟 */
    fun minutesUntilEnd(course: Course, now: Calendar): Int {
        val e = slotEndMillis(course.endSlot, now) ?: return -1
        return ((e - now.timeInMillis) / 60000).toInt()
    }

    /** 本周统计：返回 (本周课程门数, 本周总节数) */
    fun weekStats(courses: List<Course>, currentWeek: Int): Pair<Int, Int> {
        val thisWeekCourses = (1..7).flatMap { weekday ->
            coursesToday(courses, currentWeek, weekday)
        }
        val courseCount = thisWeekCourses.size
        val slotCount = thisWeekCourses.sumOf { it.endSlot - it.startSlot + 1 }
        return courseCount to slotCount
    }

    /**
     * 本周进度：返回 (已上节数, 本周总节数)。
     * "已上" = 周几早于今天整日计入，今天只计已经下课的课。
     */
    fun weekProgress(courses: List<Course>, currentWeek: Int): Pair<Int, Int> {
        val today = todayWeekday()
        val now = Calendar.getInstance()
        val totalCourses = (1..7).flatMap { weekday -> coursesToday(courses, currentWeek, weekday) }
        val total = totalCourses.sumOf { it.endSlot - it.startSlot + 1 }

        val doneCourses = totalCourses.filter { course ->
            when {
                course.weekday < today -> true
                course.weekday > today -> false
                else -> {
                    val end = slotEndMillis(course.endSlot, now) ?: return@filter false
                    now.timeInMillis >= end
                }
            }
        }
        val done = doneCourses.sumOf { it.endSlot - it.startSlot + 1 }
        return done to total
    }

    /** 跨天的"下一节"：最多往后看 7 天（会跨到下周一），带精确开始时间 */
    data class Upcoming(
        val course: Course,
        val startMillis: Long,
        val endMillis: Long,
        val dayOffset: Int,
        val weekday: Int,
        val week: Int
    )

    /**
     * 找到"接下来要上的一节课"（含今天稍后、明天、下周）。
     * 合并组件用它算"还有 X 天 X 时 X 分"。
     */
    fun upcomingClass(courses: List<Course>, currentWeek: Int, now: Calendar): Upcoming? {
        val today = todayWeekday()
        for (offset in 0..7) {
            val weekday = ((today - 1 + offset) % 7) + 1
            val week = currentWeek + (today - 1 + offset) / 7
            coursesToday(courses, week, weekday).sortedBy { it.startSlot }.forEach { course ->
                val start = daySlotMillis(now, offset, course.startSlot, isEnd = false) ?: return@forEach
                val end = daySlotMillis(now, offset, course.endSlot, isEnd = true) ?: return@forEach
                if (offset == 0 && start <= now.timeInMillis) return@forEach
                return Upcoming(course, start, end, offset, weekday, week)
            }
        }
        return null
    }

    /** 以 base 为基准、偏移 dayOffset 天后的某节开始/结束时间 */
    private fun daySlotMillis(base: Calendar, dayOffset: Int, slot: Int, isEnd: Boolean): Long? {
        val time = (if (isEnd) SlotTime.endTime(slot) else SlotTime.startTime(slot)) ?: return null
        val (hour, minute) = time
        val c = base.clone() as Calendar
        c.add(Calendar.DAY_OF_YEAR, dayOffset)
        c.set(Calendar.HOUR_OF_DAY, hour)
        c.set(Calendar.MINUTE, minute)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    /** 毫秒差 → "X 天 X 时" / "X 时 X 分" / "X 分"，给倒计时用 */
    fun formatSpan(millis: Long): String {
        val totalMin = (millis / 60000).coerceAtLeast(0).toInt()
        val days = totalMin / 1440
        val hours = totalMin % 1440 / 60
        val minutes = totalMin % 60
        return when {
            days > 0 -> "$days 天 $hours 时"
            hours > 0 -> "$hours 时 $minutes 分"
            else -> "$minutes 分"
        }
    }
}
