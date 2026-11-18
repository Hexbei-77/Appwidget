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
import com.hexbei.appwidget.ThemePalette
import com.hexbei.appwidget.WidgetStore

/**
 * "今日课程"桌面小组件（4×3 中号）
 * 通过 RemoteViewsService 挂一个可滚动 ListView，展示当日全部课程。
 * 行内三层排版：时间灰 / 课程名粗黑 / 地点灰。
 * 点击组件任意位置 → 打开 App 主界面。
 */
class TodayCourseWidget : AppWidgetProvider() {

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
        val views = RemoteViews(context.packageName, R.layout.widget_today_course)
        val currentWeek = CurrentWeekStore.get(context)
        views.setTextViewText(R.id.widgetWeek, "第 $currentWeek 周")

        // 换肤：手动选主题 / 动态按小时换主题
        val theme = ThemePalette.themes[WidgetStore.currentThemeIndex(context)]
        views.setInt(R.id.widgetRoot, "setBackgroundColor", theme.bg)
        views.setTextColor(R.id.widgetTitle, theme.accent)
        views.setTextColor(R.id.widgetWeek, theme.accent)
        views.setTextColor(R.id.widgetEmpty, theme.accent)
        val scale = WidgetStore.fontScale(context)
        views.setFloat(R.id.widgetTitle, "setTextSize", 12f * scale)
        views.setFloat(R.id.widgetWeek, "setTextSize", 11f * scale)

        // 列表数据源 + 空态
        views.setRemoteAdapter(
            R.id.widgetCourseList,
            Intent(context, TodayCourseService::class.java)
        )
        views.setEmptyView(R.id.widgetCourseList, R.id.widgetEmpty)

        // 点击组件 / 点击列表行 → 打开 App 主界面
        val openApp = WidgetIntents.open(context, "today")
        views.setPendingIntentTemplate(R.id.widgetCourseList, openApp)
        views.setOnClickPendingIntent(R.id.widgetRoot, openApp)

        manager.updateAppWidget(widgetId, views)
    }
}
