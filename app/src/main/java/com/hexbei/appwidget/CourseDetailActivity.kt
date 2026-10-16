package com.hexbei.appwidget

import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 课程详情页：展示一门课程的完整信息。
 * 从列表点击进入，Intent 里带上课程 id，再通过 ViewModel 查出来展示。
 */
class CourseDetailActivity : AppCompatActivity() {

    private val viewModel: CourseViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_course_detail)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.detailRoot)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val courseId = intent.getLongExtra(EXTRA_COURSE_ID, -1L)

        // 观察"当前课程"，查到后填充界面
        lifecycleScope.launch {
            viewModel.currentCourse.collectLatest { course ->
                if (course != null) {
                    findViewById<TextView>(R.id.tvDetailName).text = course.name
                    findViewById<TextView>(R.id.tvDetailTeacher).text =
                        "教师：${course.teacher.ifEmpty { "无" }}"
                    findViewById<TextView>(R.id.tvDetailLocation).text =
                        "教室：${course.location.ifEmpty { "无" }}"
                    findViewById<TextView>(R.id.tvDetailWeekday).text = "星期：周${course.weekday}"
                    findViewById<TextView>(R.id.tvDetailSlot).text =
                        "节次：第${course.startSlot}-${course.endSlot}节"
                    findViewById<TextView>(R.id.tvDetailWeek).text =
                        "周次：第${course.startWeek}-${course.endWeek}周"
                    val oddEvenText = when (course.oddEven) {
                        1 -> "单周"
                        2 -> "双周"
                        else -> "每周"
                    }
                    findViewById<TextView>(R.id.tvDetailOddEven).text = "单双周：$oddEvenText"
                    findViewById<TextView>(R.id.tvDetailNote).text =
                        "备注：${course.note.ifEmpty { "无" }}"
                }
            }
        }
        viewModel.loadCourse(courseId)
    }

    companion object {
        const val EXTRA_COURSE_ID = "course_id"
    }
}
