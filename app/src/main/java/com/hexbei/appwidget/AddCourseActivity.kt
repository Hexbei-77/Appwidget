package com.hexbei.appwidget

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.hexbei.appwidget.data.Course

/**
 * 新增课程页：一个表单，填完点"保存"把课程写进数据仓库。
 *
 * 它是 C6"数据底座"的一部分——小组件要展示课程，得先在这里录入门课程。
 */
class AddCourseActivity : AppCompatActivity() {

    private val viewModel: CourseViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_add_course)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.addRoot)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val etName = findViewById<EditText>(R.id.etName)
        val etTeacher = findViewById<EditText>(R.id.etTeacher)
        val etLocation = findViewById<EditText>(R.id.etLocation)
        val spWeekday = findViewById<Spinner>(R.id.spWeekday)
        val etStartSlot = findViewById<EditText>(R.id.etStartSlot)
        val etEndSlot = findViewById<EditText>(R.id.etEndSlot)
        val etStartWeek = findViewById<EditText>(R.id.etStartWeek)
        val etEndWeek = findViewById<EditText>(R.id.etEndWeek)
        val spOddEven = findViewById<Spinner>(R.id.spOddEven)
        val etNote = findViewById<EditText>(R.id.etNote)

        // 星期下拉框：周一..周日，对应 weekday 1..7
        spWeekday.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, WEEKDAYS).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        // 单双周下拉框：每周/单周/双周，对应 oddEven 0/1/2
        spOddEven.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, ODD_EVEN).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            val name = etName.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, "请填写课程名", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val course = Course(
                name = name,
                teacher = etTeacher.text.toString().trim(),
                location = etLocation.text.toString().trim(),
                weekday = spWeekday.selectedItemPosition + 1,
                startSlot = etStartSlot.text.toString().toIntOrNull() ?: 1,
                endSlot = etEndSlot.text.toString().toIntOrNull() ?: 1,
                startWeek = etStartWeek.text.toString().toIntOrNull() ?: 1,
                endWeek = etEndWeek.text.toString().toIntOrNull() ?: 20,
                oddEven = spOddEven.selectedItemPosition,
                note = etNote.text.toString().trim()
            )
            viewModel.insert(course) { finish() }
        }
    }

    companion object {
        private val WEEKDAYS = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        private val ODD_EVEN = arrayOf("每周", "单周", "双周")
    }
}
