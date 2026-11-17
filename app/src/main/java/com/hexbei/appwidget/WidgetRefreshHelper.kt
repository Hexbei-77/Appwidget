package com.hexbei.appwidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import com.hexbei.appwidget.widget.CheckInWidget
import com.hexbei.appwidget.widget.NextCountdownWidget
import com.hexbei.appwidget.widget.TodayCourseWidget
import com.hexbei.appwidget.widget.WeekProgressWidget
import com.hexbei.appwidget.widget.WeekViewWidget

/**
 * 小组件刷新助手：课程数据或设置一变，就主动刷新所有桌面小组件，
 * 保证桌面信息跟 App 同步（C6 的"实时性"）。
 * 另外系统会按各组件 updatePeriodMillis 定时兜底刷新。
 */
object WidgetRefreshHelper {

    private val providers = listOf(
        TodayCourseWidget::class.java,
        NextCountdownWidget::class.java,
        WeekViewWidget::class.java,
        WeekProgressWidget::class.java,
        CheckInWidget::class.java
    )

    fun refresh(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        for (cls in providers) {
            val ids = manager.getAppWidgetIds(ComponentName(context, cls))
            if (ids.isNotEmpty()) {
                // 今日课程是列表视图：先通知列表数据变化，让 RemoteViewsFactory 重新加载
                if (cls == TodayCourseWidget::class.java) {
                    manager.notifyAppWidgetViewDataChanged(ids, R.id.widgetCourseList)
                }
                val provider = cls.getDeclaredConstructor().newInstance() as AppWidgetProvider
                provider.onUpdate(context, manager, ids)
            }
        }
    }
}
