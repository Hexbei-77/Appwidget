package com.hexbei.appwidget.widget

import com.hexbei.appwidget.WidgetIntents

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.hexbei.appwidget.CurrentWeekStore
import com.hexbei.appwidget.R
import com.hexbei.appwidget.ScheduleEngine
import com.hexbei.appwidget.SlotTime
import com.hexbei.appwidget.ThemePalette
import com.hexbei.appwidget.WidgetStore
import com.hexbei.appwidget.data.CourseRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * 「下一节 + 倒计时」合并组件（4×2）
 *
 * 原来「下一节课」(2×1) 和「上课倒计时」(2×1) 各占一格、信息重复，
 * 现在合成一个横幅卡片：左边课程名/时间/教室，右边"天 / 时 / 分"倒计时。
 * 正在上课时右侧切换为"剩余"。
 */
class NextCountdownWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_TICK = "com.hexbei.appwidget.NEXT_COUNTDOWN_TICK"
        private val WEEKDAY_LABELS = arrayOf("一", "二", "三", "四", "五", "六", "日")
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) {
            updateWidget(context, appWidgetManager, id)
        }
        scheduleNextTick(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TICK) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, NextCountdownWidget::class.java)
            )
            for (id in ids) {
                updateWidget(context, manager, id)
            }
            scheduleNextTick(context)
        }
    }

    override fun onEnabled(context: Context) {
        scheduleNextTick(context)
    }

    override fun onDisabled(context: Context) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.cancel(tickIntent(context))
    }

    /** 每分钟整点触发一次刷新（对齐到下一分钟的第 0 秒） */
    private fun scheduleNextTick(context: Context) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val calendar = Calendar.getInstance().apply {
            add(Calendar.MINUTE, 1)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // Android 12+ 没授予精确闹钟权限时会抛 SecurityException，
        // 退化成"非精确但允许休眠唤醒"的闹钟，倒计时差几秒无所谓，别把组件搞崩。
        try {
            alarm.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                tickIntent(context)
            )
        } catch (e: SecurityException) {
            alarm.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                tickIntent(context)
            )
        }
    }

    private fun tickIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            2,
            Intent(context, NextCountdownWidget::class.java).apply { action = ACTION_TICK },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun updateWidget(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_next_countdown)
        val theme = ThemePalette.themes[WidgetStore.currentThemeIndex(context)]
        val scale = WidgetStore.fontScale(context)

        views.setInt(R.id.widgetRoot, "setBackgroundColor", theme.bg)
        views.setTextColor(R.id.widgetTitle, theme.accent)
        views.setTextColor(R.id.widgetCountdownBig, theme.accent)
        views.setFloat(R.id.widgetCourseName, "setTextSize", 15f * scale)
        views.setFloat(R.id.widgetCourseInfo, "setTextSize", 10f * scale)
        views.setFloat(R.id.widgetTitle, "setTextSize", 9f * scale)
        views.setFloat(R.id.widgetCountdownBig, "setTextSize", 20f * scale)
        views.setOnClickPendingIntent(R.id.widgetRoot, WidgetIntents.open(context, "next"))

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val courses = CourseRepository(context).getAll()
                val week = CurrentWeekStore.get(context)
                val today = ScheduleEngine.todayWeekday()
                val now = Calendar.getInstance()
                val ongoing = ScheduleEngine.ongoingCourse(courses, week, today, now)
                val upcoming = ScheduleEngine.upcomingClass(courses, week, now)

                when {
                    ongoing != null -> {
                        val label = "正在上课"
                        val endTime = SlotTime.endTime(ongoing.endSlot)
                        val subTitle = if (endTime != null) {
                            "预计 ${"%02d:%02d".format(endTime.first, endTime.second)} 下课"
                        } else ""
                        val target = ScheduleEngine.slotEndMillis(ongoing.endSlot, now)
                        val remain = if (target != null) target - now.timeInMillis else 0
                        bind(
                            views, label, "剩余",
                            ongoing.name,
                            listOf(SlotTime.range(ongoing.startSlot, ongoing.endSlot), ongoing.location)
                                .filter { it.isNotEmpty() }.joinToString(" · "),
                            remain
                        )
                    }

                    upcoming != null -> {
                        val dayLabel = dayLabelOf(upcoming.dayOffset, upcoming.weekday)
                        val startTime = SlotTime.startTime(upcoming.course.startSlot)
                        val title = "下一节 · " + if (startTime != null) {
                            "$dayLabel ${"%02d:%02d".format(startTime.first, startTime.second)}"
                        } else dayLabel
                        bind(
                            views, title, "距上课",
                            upcoming.course.name,
                            listOf(
                                SlotTime.range(upcoming.course.startSlot, upcoming.course.endSlot),
                                upcoming.course.location
                            ).filter { it.isNotEmpty() }.joinToString(" · "),
                            upcoming.startMillis - now.timeInMillis
                        )
                    }

                    else -> {
                        views.setTextViewText(R.id.widgetTitle, "下一节")
                        views.setTextViewText(R.id.widgetCourseName, "本周课表是空的")
                        views.setTextViewText(R.id.widgetCourseInfo, "去 App 里导入或添加课程")
                        views.setTextViewText(R.id.widgetCountdownLabel, "距上课")
                        views.setTextViewText(R.id.widgetCountdownBig, "--")
                        views.setTextViewText(R.id.widgetCountdownSub, "")
                    }
                }
                // 不管哪个分支，文案都已经塞到 views 里了，这里统一提交一次给桌面
                manager.updateAppWidget(widgetId, views)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /** 把课程信息 + 剩余时间写进 RemoteViews */
    private fun bind(
        views: RemoteViews,
        title: String,
        countdownLabel: String,
        courseName: String,
        courseInfo: String,
        spanMillis: Long
    ) {
        views.setTextViewText(R.id.widgetTitle, title)
        views.setTextViewText(R.id.widgetCourseName, courseName)
        views.setTextViewText(R.id.widgetCourseInfo, courseInfo)

        val totalMin = (spanMillis / 60000L).coerceAtLeast(0L).toInt()
        val days = totalMin / 1440
        val hours = totalMin % 1440 / 60
        val minutes = totalMin % 60

        // 统一按"天 / 时 / 分"三段展示，大字号放前两段，小字号补最后一段
        views.setTextViewText(R.id.widgetCountdownLabel, countdownLabel)
        when {
            days > 0 -> {
                views.setTextViewText(R.id.widgetCountdownBig, "${days}天 ${hours}时")
                views.setTextViewText(R.id.widgetCountdownSub, "${minutes}分")
            }
            hours > 0 -> {
                views.setTextViewText(R.id.widgetCountdownBig, "${hours}时 ${minutes}分")
                views.setTextViewText(R.id.widgetCountdownSub, "后${if (countdownLabel == "距上课") "上课" else "下课"}")
            }
            else -> {
                views.setTextViewText(R.id.widgetCountdownBig, "${minutes}分")
                views.setTextViewText(R.id.widgetCountdownSub, "就快了")
            }
        }
        // 交给调用方更新，这里只负责文案
    }

    private fun dayLabelOf(offset: Int, weekday: Int): String = when (offset) {
        0 -> "今天"
        1 -> "明天"
        2 -> "后天"
        else -> "周" + WEEKDAY_LABELS[weekday - 1]
    }
}
