package com.hexbei.appwidget

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.hexbei.appwidget.data.CourseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 课程详情页：从主列表点一门课进来，看完整信息。
 * 这是课程设计指南要求的"列表 → 详情"导航结构里的详情页。
 * 除了展示字段，还会根据当前周算出"本周上不上这门课"，删课也挪到这里。
 */
class CourseDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_COURSE_ID = "course_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_course_detail)
        title = "课程详情"

        val courseId = intent.getLongExtra(EXTRA_COURSE_ID, -1L)
        if (courseId < 0) {
            finish()
            return
        }

        lifecycleScope.launch {
            val course = withContext(Dispatchers.IO) {
                CourseRepository(this@CourseDetailActivity).getById(courseId)
            }
            if (course == null) {
                Toast.makeText(this@CourseDetailActivity, "课程不存在或已被删除", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }
            bind(course)
        }

        findViewById<Button>(R.id.btnDetailBack).setOnClickListener { finish() }
    }

    private fun bind(course: com.hexbei.appwidget.data.Course) {
        findViewById<TextView>(R.id.tvDetailName).text = course.name

        // 本周是否上这门课：算出来贴个标签
        val week = CurrentWeekStore.get(this)
        val onThisWeek = ScheduleEngine.isCourseOn(course, week, course.weekday)
        val badge = findViewById<TextView>(R.id.tvDetailOnWeek)
        if (onThisWeek) {
            badge.text = "第 $week 周有这门课"
            badge.setBackgroundColor(0xFF2E7D32.toInt())
        } else {
            badge.text = "第 $week 周没这门课"
            badge.setBackgroundColor(0xFF999999.toInt())
        }

        val weekdayName = arrayOf("一", "二", "三", "四", "五", "六", "日")[course.weekday - 1]
        val weekText = when (course.oddEven) {
            1 -> "单周"
            2 -> "双周"
            else -> "每周"
        }
        findViewById<TextView>(R.id.tvDetailTeacher).text = "教师：${course.teacher.ifEmpty { "未填" }}"
        findViewById<TextView>(R.id.tvDetailTime).text = "时间：周$weekdayName  ${SlotTime.range(course.startSlot, course.endSlot)}"
        findViewById<TextView>(R.id.tvDetailSlot).text = "节次：第 ${course.startSlot}-${course.endSlot} 节"
        findViewById<TextView>(R.id.tvDetailLocation).text = "教室：${course.location.ifEmpty { "待定" }}"
        findViewById<TextView>(R.id.tvDetailWeek).text = "周次：第 ${course.startWeek}-${course.endWeek} 周（$weekText）"

        findViewById<Button>(R.id.btnDetailDelete).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("删除这门课？")
                .setMessage("${course.name}（周${course.weekday} 第${course.startSlot}-${course.endSlot}节）")
                .setPositiveButton("删") { _, _ ->
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            CourseRepository(this@CourseDetailActivity).delete(course.id)
                        }
                        WidgetRefreshHelper.refresh(this@CourseDetailActivity)
                        Toast.makeText(this@CourseDetailActivity, "已删除", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }
}
