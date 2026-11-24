package com.hexbei.appwidget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.Toast
import com.hexbei.appwidget.R
import com.hexbei.appwidget.ThemePalette
import com.hexbei.appwidget.WidgetStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "今日签到"桌面小组件（1×1 交互按钮）
 * 点一下：切换签到状态。签到后显示 ✓ 已签到，再点取消。
 * 状态按天存，第二天自动重置。
 */
class CheckInWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_CHECK_IN = "com.hexbei.appwidget.CHECK_IN"
        private const val PREFS = "checkin_prefs"

        private fun todayKey(): String =
            SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())

        fun isCheckedIn(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(todayKey(), false)

        private fun setCheckedIn(context: Context, on: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(todayKey(), on).apply()
        }
    }

    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) render(context, manager, id)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_CHECK_IN) {
            val now = isCheckedIn(context)
            setCheckedIn(context, !now)
            Toast.makeText(context, if (!now) "已签到 ✓" else "已取消签到", Toast.LENGTH_SHORT).show()
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, CheckInWidget::class.java))
            for (id in ids) render(context, manager, id)
        }
    }

    private fun render(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_checkin)
        val checked = isCheckedIn(context)
        if (checked) {
            // 已完成：固定绿色填充 + 深绿文字（状态语义优先）
            views.setTextViewText(R.id.widgetCheckInText, "✓ 已签到")
            views.setInt(R.id.widgetRoot, "setBackgroundResource", R.drawable.bg_checkin_done)
            views.setTextColor(R.id.widgetCheckInText, 0xFF1B5E20.toInt())
        } else {
            // 未签到：跟随主题 / 动态背景（符合"所有组件支持样式配置"）
            val theme = ThemePalette.themes[WidgetStore.currentThemeIndex(context)]
            views.setTextViewText(R.id.widgetCheckInText, "签到")
            views.setInt(R.id.widgetRoot, "setBackgroundColor", theme.bg)
            views.setTextColor(R.id.widgetCheckInText, theme.accent)
        }

        val pi = PendingIntent.getBroadcast(
            context, widgetId,
            Intent(context, CheckInWidget::class.java).setAction(ACTION_CHECK_IN),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widgetRoot, pi)
        manager.updateAppWidget(widgetId, views)
    }
}
