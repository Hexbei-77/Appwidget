package com.hexbei.appwidget.widget

import com.hexbei.appwidget.WidgetIntents

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
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
 * "周进度环"桌面小组件（2×2 中号）
 * 用 Canvas 自绘圆环：环形轨道 + 已上节数进度弧 + 中心百分比文字。
 * RemoteViews 不支持自定义 View，所以把圆环画成 Bitmap 放进 ImageView。
 */
class WeekProgressWidget : AppWidgetProvider() {

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
        val views = RemoteViews(context.packageName, R.layout.widget_week_progress)
        val theme = ThemePalette.themes[WidgetStore.currentThemeIndex(context)]
        views.setInt(R.id.widgetRoot, "setBackgroundColor", theme.bg)
        views.setTextColor(R.id.widgetTitle, theme.accent)
        views.setFloat(R.id.widgetTitle, "setTextSize", 11f * WidgetStore.fontScale(context))
        // 自定义鼓励语（设置页可改，默认"好好学习，天天向上！"）
        views.setTextViewText(R.id.widgetMotto, WidgetStore.getMotto(context))
        views.setFloat(R.id.widgetMotto, "setTextSize", 9f * WidgetStore.fontScale(context))

        val openApp = WidgetIntents.open(context, "progress")
        views.setOnClickPendingIntent(R.id.widgetRoot, openApp)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val courses = CourseRepository(context).getAll()
                val currentWeek = CurrentWeekStore.get(context)
                val (done, total) = ScheduleEngine.weekProgress(courses, currentWeek)
                views.setImageViewBitmap(R.id.widgetRing, drawRing(done, total, theme.accent))
                manager.updateAppWidget(widgetId, views)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /** 画圆环：400px 画布，环形轨道 + 进度弧 + 中心文字 */
    private fun drawRing(done: Int, total: Int, accent: Int): Bitmap {
        val size = 400
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cx = size / 2f
        val cy = size / 2f
        val radius = size / 2f - 34f
        val stroke = 34f

        // 轨道
        val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.ROUND
            color = 0x26000000
        }
        canvas.drawCircle(cx, cy, radius, track)

        if (total <= 0) {
            // 本周没课：中心显示"无课"
            drawCenter(canvas, cx, cy, "无课", null, accent)
            return bitmap
        }

        // 进度弧（从 12 点方向顺时针）
        val progress = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.ROUND
            color = accent
        }
        val sweep = 360f * done / total
        if (sweep > 0) {
            canvas.drawArc(
                RectF(cx - radius, cy - radius, cx + radius, cy + radius),
                -90f, sweep, false, progress
            )
        }

        // 中心：百分比大数字 + 下面"已上/总 节"
        val percent = if (total > 0) (100 * done / total) else 0
        drawCenter(canvas, cx, cy, "$percent%", "$done/${total}节", accent)
        return bitmap
    }

    private fun drawCenter(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        main: String,
        sub: String?,
        accent: Int
    ) {
        val mainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent
            textSize = 64f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText(main, cx, cy + 20f, mainPaint)

        if (sub != null) {
            val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x99000000.toInt()
                textSize = 26f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(sub, cx, cy + 62f, subPaint)
        }
    }
}
