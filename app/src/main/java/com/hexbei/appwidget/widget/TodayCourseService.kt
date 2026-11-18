package com.hexbei.appwidget.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.hexbei.appwidget.CurrentWeekStore
import com.hexbei.appwidget.R
import com.hexbei.appwidget.ScheduleEngine
import com.hexbei.appwidget.SlotTime
import com.hexbei.appwidget.WidgetStore
import com.hexbei.appwidget.data.Course
import com.hexbei.appwidget.data.CourseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * 今日课程列表的数据服务：为 AppWidget 里的 ListView 提供"当天课程"行数据。
 * 这是 C6 的进阶点：RemoteViews 里放真正的可滚动列表，而不是写死几行。
 */
class TodayCourseService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        TodayCourseFactory(applicationContext)
}

/** 列表工厂：每次数据变化时重新读课程，生成一行一行的小组件视图 */
class TodayCourseFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    private var courses: List<Course> = emptyList()

    override fun onCreate() {}

    /** 数据变化时调用（onDataSetChanged），这里重新计算"今天要上的课" */
    override fun onDataSetChanged() {
        courses = runBlocking(Dispatchers.IO) {
            val repo = CourseRepository(context)
            val week = CurrentWeekStore.get(context)
            ScheduleEngine.coursesToday(repo.getAll(), week, ScheduleEngine.todayWeekday())
        }
    }

    override fun getCount(): Int = courses.size

    override fun getViewAt(position: Int): RemoteViews {
        val course = courses[position]
        val row = RemoteViews(context.packageName, R.layout.item_today_course)
        val scale = WidgetStore.fontScale(context)

        row.setTextViewText(R.id.tvTime, SlotTime.range(course.startSlot, course.endSlot))
        row.setTextViewText(R.id.tvName, course.name)
        row.setTextViewText(
            R.id.tvLoc,
            "@ ${course.location.ifEmpty { "教室待定" }}"
        )

        // 字号档位：时间/地点同档，课程名稍大，保持三层层次
        row.setFloat(R.id.tvTime, "setTextSize", 11f * scale)
        row.setFloat(R.id.tvName, "setTextSize", 15f * scale)
        row.setFloat(R.id.tvLoc, "setTextSize", 11f * scale)

        // 颜色：时间灰 / 课程名黑 / 地点灰（跟随主题的强调色点缀标题即可，行内保持可读性）
        row.setTextColor(R.id.tvTime, 0xFF999999.toInt())
        row.setTextColor(R.id.tvName, 0xFF333333.toInt())
        row.setTextColor(R.id.tvLoc, 0xFF999999.toInt())

        // 点击行 → 由模板 PendingIntent 打开 App 主界面
        val fillIn = Intent().putExtra("course_id", course.id)
        row.setOnClickFillInIntent(R.id.widgetCourseRow, fillIn)
        return row
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = courses[position].id

    override fun hasStableIds(): Boolean = true

    override fun onDestroy() {}
}
