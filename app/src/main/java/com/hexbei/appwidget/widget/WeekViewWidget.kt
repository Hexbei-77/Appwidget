package com.hexbei.appwidget.widget

import com.hexbei.appwidget.WidgetIntents

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.hexbei.appwidget.CurrentWeekStore
import com.hexbei.appwidget.MainActivity
import com.hexbei.appwidget.R
import com.hexbei.appwidget.ScheduleEngine
import com.hexbei.appwidget.ThemePalette
import com.hexbei.appwidget.WidgetStore
import com.hexbei.appwidget.data.CourseRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * "周历视图"桌面小组件（4×4 大尺寸，桌历式网格）
 * 表头：周一~周日；内容：1-2 / 3-4 / 5-6 / 7-8 / 9-10 共五行格子，
 * 有课的格子显示课程名（最多两个，超出显示 +N），空格子留浅色底。
 */
class WeekViewWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) {
            updateWidget(context, appWidgetManager, id)
        }
    }

    private fun updateWidget(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_week_view)
        val currentWeek = CurrentWeekStore.get(context)
        views.setTextViewText(R.id.widgetWeek, "第 $currentWeek 周")

        // 主题：背景 + 标题色（字号档位只调标题，格子字号固定以免撑破网格）
        val theme = ThemePalette.themes[WidgetStore.currentThemeIndex(context)]
        views.setInt(R.id.widgetRoot, "setBackgroundColor", theme.bg)
        views.setTextColor(R.id.widgetTitle, theme.accent)
        views.setTextColor(R.id.widgetWeek, theme.accent)
        val scale = WidgetStore.fontScale(context)
        views.setFloat(R.id.widgetTitle, "setTextSize", 12f * scale)
        views.setFloat(R.id.widgetWeek, "setTextSize", 10f * scale)

        // 高亮今天这一列：表头用主题色加粗，一眼找到今天
        val today = ScheduleEngine.todayWeekday()
        views.setTextColor(H_HEADER_IDS[today - 1], theme.accent)
        views.setFloat(H_HEADER_IDS[today - 1], "setTextSize", 11f)

        // 点击组件 → 打开 App 主界面
        val openApp = WidgetIntents.open(context, "week")
        views.setOnClickPendingIntent(R.id.widgetRoot, openApp)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val courses = CourseRepository(context).getAll()
                val ranges = listOf(1 to 2, 3 to 4, 5 to 6, 7 to 8, 9 to 10)
                ranges.forEachIndexed { r, (start, end) ->
                    for (d in 1..7) {
                        val cellId = CELL_IDS[r][d - 1]
                        val onCourses = courses.filter { course ->
                            ScheduleEngine.isCourseOn(course, currentWeek, d) &&
                                course.startSlot <= end && course.endSlot >= start
                        }
                        if (onCourses.isEmpty()) {
                            views.setTextViewText(cellId, "")
                            views.setInt(cellId, "setBackgroundColor", 0x0A000000)
                        } else {
                            views.setTextViewText(cellId, cellText(onCourses))
                            views.setInt(
                                cellId,
                                "setBackgroundColor",
                                ThemePalette.courseColors[onCourses.first().colorIndex % ThemePalette.courseColors.size]
                            )
                        }
                    }
                }
                manager.updateAppWidget(widgetId, views)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /** 格子文字竖排：只放课程名（一个字一行），不拼接不省略号 */
    private fun cellText(courses: List<com.hexbei.appwidget.data.Course>): String {
        return courses.first().name
    }

    companion object {
        /** 表头七个列的 id（周一~周日），用来高亮今天 */
        private val H_HEADER_IDS = intArrayOf(
            R.id.wvH1, R.id.wvH2, R.id.wvH3, R.id.wvH4, R.id.wvH5, R.id.wvH6, R.id.wvH7
        )

        /** 五行 × 七列的格子 id */
        private val CELL_IDS = arrayOf(
            intArrayOf(R.id.wvR1D1, R.id.wvR1D2, R.id.wvR1D3, R.id.wvR1D4, R.id.wvR1D5, R.id.wvR1D6, R.id.wvR1D7),
            intArrayOf(R.id.wvR2D1, R.id.wvR2D2, R.id.wvR2D3, R.id.wvR2D4, R.id.wvR2D5, R.id.wvR2D6, R.id.wvR2D7),
            intArrayOf(R.id.wvR3D1, R.id.wvR3D2, R.id.wvR3D3, R.id.wvR3D4, R.id.wvR3D5, R.id.wvR3D6, R.id.wvR3D7),
            intArrayOf(R.id.wvR4D1, R.id.wvR4D2, R.id.wvR4D3, R.id.wvR4D4, R.id.wvR4D5, R.id.wvR4D6, R.id.wvR4D7),
            intArrayOf(R.id.wvR5D1, R.id.wvR5D2, R.id.wvR5D3, R.id.wvR5D4, R.id.wvR5D5, R.id.wvR5D6, R.id.wvR5D7)
        )
    }
}
