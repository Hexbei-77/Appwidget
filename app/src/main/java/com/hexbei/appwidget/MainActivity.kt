package com.hexbei.appwidget

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.hexbei.appwidget.data.Course
import com.hexbei.appwidget.data.CourseRepository
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import java.util.Calendar

/**
 * 主界面：课程列表页（App 是"配角"，只负责录课和进设置）。
 * 主角是桌面小组件：这里改动课程后立即调用 WidgetRefreshHelper 刷新所有组件。
 *
 * 数据走 CourseViewModel：列表数据不写在 Activity 里，旋转屏幕不丢；
 * 点列表项进 CourseDetailActivity 看详情（"列表 → 详情"导航）。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var adapter: CourseAdapter
    private val viewModel: CourseViewModel by viewModels()

    /** PDF 文件选择（SAF 免权限） */
    private val pdfPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) importPdf(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // pdfbox 需要先初始化资源加载器
        PDFBoxResourceLoader.init(applicationContext)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val recycler = findViewById<RecyclerView>(R.id.rvCourses)
        // 点列表项：进详情页（删除也挪到详情页里）
        adapter = CourseAdapter(onClick = { course ->
            startActivity(Intent(this, CourseDetailActivity::class.java).apply {
                putExtra(CourseDetailActivity.EXTRA_COURSE_ID, course.id)
            })
        })
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        findViewById<FloatingActionButton>(R.id.fabAdd).setOnClickListener { showAddDialog() }
        findViewById<TextView>(R.id.tvImportPdf).setOnClickListener { pickPdf() }
        findViewById<TextView>(R.id.tvSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<TextView>(R.id.tvAbout).setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }

        // 订阅 ViewModel 里的课程列表：数据一变就刷新界面（生命周期感知，切后台不跑）
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.courses.collect { list ->
                    adapter.submitList(list)
                    findViewById<TextView>(R.id.tvEmpty).visibility =
                        if (list.isEmpty()) View.VISIBLE else View.GONE
                    pushDynamicShortcuts(list)
                }
            }
        }

        handleDeepLink(intent)
    }

    /** App 已经在后台时再点组件 / 快捷方式，走这里 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    /**
     * 解析深链：appwidget://course/{action}
     * 这个方法是 C6"深度链接"的落点——组件点击、快捷方式、adb / 浏览器都能走到同一套分发。
     */
    private fun handleDeepLink(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != WidgetIntents.SCHEME || uri.host != WidgetIntents.HOST) return
        when (uri.pathSegments.firstOrNull()) {
            "settings" -> startActivity(Intent(this, SettingsActivity::class.java))
            "add" -> showAddDialog()
            "import" -> pickPdf()
            null -> Unit
            else -> showDeepLinkHint(uri.pathSegments.first())
        }
    }

    /** 组件点击进来时，用一句 Toast 把"它想让你看的信息"直接给出来 */
    private fun showDeepLinkHint(action: String) {
        lifecycleScope.launch {
            val courses = withContext(Dispatchers.IO) { CourseRepository(this@MainActivity).getAll() }
            val week = CurrentWeekStore.get(this@MainActivity)
            val today = ScheduleEngine.todayWeekday()
            val now = Calendar.getInstance()
            val msg = when (action) {
                "today" -> {
                    val list = ScheduleEngine.coursesToday(courses, week, today)
                    if (list.isEmpty()) "今天（第 ${week} 周 周${today}）没有课"
                    else "今天有 ${list.size} 节课：" + list.joinToString("｜") {
                        "${it.name} ${SlotTime.range(it.startSlot, it.endSlot)}"
                    }
                }
                "next" -> {
                    val ongoing = ScheduleEngine.ongoingCourse(courses, week, today, now)
                    val upcoming = ScheduleEngine.upcomingClass(courses, week, now)
                    when {
                        ongoing != null ->
                            "正在上：${ongoing.name}｜${ongoing.location.ifBlank { "地点待定" }}"
                        upcoming != null ->
                            "下一节：${upcoming.course.name}｜${SlotTime.range(upcoming.course.startSlot, upcoming.course.endSlot)}｜${upcoming.course.location.ifBlank { "地点待定" }}"
                        else -> "今天后面没有课了"
                    }
                }
                "week" -> {
                    val (c, s) = ScheduleEngine.weekStats(courses, week)
                    "第 $week 周共 $c 门课、$s 节课"
                }
                "progress" -> {
                    val (d, t) = ScheduleEngine.weekProgress(courses, week)
                    "本周已上 $d / $t 节"
                }
                else -> "已打开课程表"
            }
            Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * C6"快捷方式"的动态部分：长按桌面图标，第一项永远显示"下一节/上课中"的实时课程。
     * 课程数据一变（列表刷新时）就重新推送，属于 ShortcutManager 动态快捷方式。
     */
    private fun pushDynamicShortcuts(courses: List<Course>) {
        try {
            val sm = getSystemService(ShortcutManager::class.java) ?: return
            val week = CurrentWeekStore.get(this)
            val today = ScheduleEngine.todayWeekday()
            val now = Calendar.getInstance()
            val ongoing = ScheduleEngine.ongoingCourse(courses, week, today, now)
            val upcoming = ScheduleEngine.upcomingClass(courses, week, now)
            val target = ongoing ?: upcoming?.course
            val shortLabel: String
            val longLabel: String
            if (target == null) {
                shortLabel = "今天没课"
                longLabel = "今天没有待上的课，点开查看整周课表"
            } else {
                val prefix = if (ongoing != null) "上课中" else "下一节"
                shortLabel = "$prefix${target.name}".take(10)
                longLabel = "$prefix ${target.name}｜${SlotTime.range(target.startSlot, target.endSlot)}｜${target.location.ifBlank { "地点待定" }}"
            }
            val intent = Intent(Intent.ACTION_VIEW, WidgetIntents.uri("next")).setPackage(packageName)
            val info = ShortcutInfo.Builder(this, "shortcut_next")
                .setShortLabel(shortLabel)
                .setLongLabel(longLabel)
                .setIcon(Icon.createWithResource(this, R.drawable.ic_shortcut_today))
                .setIntent(intent)
                .build()
            sm.setDynamicShortcuts(listOf(info))
        } catch (e: Exception) {
            // 快捷方式属于增强项，推送失败不影响录课和小组件
        }
    }

    /** 添加课程对话框（完整字段） */
    private fun showAddDialog() {
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 0)
        }
        val etName = EditText(this).apply { hint = "课程名（如：物联网传感）" }
        val etWeekday = EditText(this).apply {
            hint = "星期几（1=周一 … 7=周日）"; inputType = InputType.TYPE_CLASS_NUMBER
        }
        val etStart = EditText(this).apply {
            hint = "第几节开始（1-10）"; inputType = InputType.TYPE_CLASS_NUMBER
        }
        val etEnd = EditText(this).apply {
            hint = "第几节结束（如 4）"; inputType = InputType.TYPE_CLASS_NUMBER
        }
        val etLocation = EditText(this).apply { hint = "教室（如：学楼10楼503，选填）" }
        val etTeacher = EditText(this).apply { hint = "教师（选填）" }
        val etWeeks = EditText(this).apply {
            hint = "起止周（如 1-16，默认整学期）"; inputType = InputType.TYPE_CLASS_NUMBER
        }

        val radioEvery = RadioButton(this).apply { text = "每周" }
        val radioOdd = RadioButton(this).apply { text = "单周" }
        val radioEven = RadioButton(this).apply { text = "双周" }
        val weekGroup = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            addView(radioEvery)
            addView(radioOdd)
            addView(radioEven)
            check(radioEvery.id)
        }

        wrap.addView(etName); wrap.addView(etWeekday)
        wrap.addView(etStart); wrap.addView(etEnd)
        wrap.addView(etLocation); wrap.addView(etTeacher); wrap.addView(etWeeks)
        wrap.addView(weekGroup)

        AlertDialog.Builder(this)
            .setTitle("加一门课")
            .setView(wrap)
            .setPositiveButton("保存") { _, _ ->
                val name = etName.text.toString().trim()
                val weekday = etWeekday.text.toString().toIntOrNull()
                val start = etStart.text.toString().toIntOrNull()
                val end = etEnd.text.toString().toIntOrNull() ?: start
                val location = etLocation.text.toString().trim()
                val teacher = etTeacher.text.toString().trim()
                val (startWeek, endWeek) = parseWeeks(etWeeks.text.toString().trim())
                val oddEven = when (weekGroup.checkedRadioButtonId) {
                    radioOdd.id -> 1
                    radioEven.id -> 2
                    else -> 0
                }

                if (name.isEmpty() || weekday == null || weekday !in 1..7 ||
                    start == null || start !in 1..10 || end == null || end < start
                ) {
                    Toast.makeText(this, "请把课程名、星期、节次填对", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                lifecycleScope.launch {
                    val colorIndex = withContext(Dispatchers.IO) {
                        CourseRepository(this@MainActivity).getAll().size
                    } % ThemePalette.courseColors.size
                    withContext(Dispatchers.IO) {
                        CourseRepository(this@MainActivity).insert(
                            Course(
                                name = name,
                                teacher = teacher,
                                location = location,
                                weekday = weekday,
                                startSlot = start,
                                endSlot = end,
                                startWeek = startWeek,
                                endWeek = endWeek,
                                oddEven = oddEven,
                                colorIndex = colorIndex
                            )
                        )
                    }
                    viewModel.refresh()
                    WidgetRefreshHelper.refresh(this@MainActivity)
                    Toast.makeText(this@MainActivity, "已添加，桌面小组件已刷新", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /** 解析 "1-16" / "3~18" 起止周，默认整学期 */
    private fun parseWeeks(input: String): Pair<Int, Int> {
        val p = input.split(Regex("[-~至]"))
        val start = p.getOrNull(0)?.toIntOrNull() ?: 1
        val end = p.getOrNull(1)?.toIntOrNull() ?: 16
        return start.coerceIn(1, 20) to end.coerceIn(start, 20)
    }

    /** 选择 PDF 并导入 */
    private fun pickPdf() {
        pdfPicker.launch(arrayOf("application/pdf"))
    }

    private fun importPdf(uri: android.net.Uri) {
        lifecycleScope.launch {
            val parsed = withContext(Dispatchers.IO) {
                try {
                    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: return@withContext emptyList<Course>()
                    PdfCourseParser.parse(bytes.inputStream())
                } catch (e: Exception) {
                    emptyList<Course>()
                }
            }
            if (parsed.isEmpty()) {
                Toast.makeText(this@MainActivity, "没有从 PDF 识别出课程，请手动添加", Toast.LENGTH_SHORT).show()
                return@launch
            }
            AlertDialog.Builder(this@MainActivity)
                .setTitle("导入课表")
                .setMessage("从 PDF 识别到 ${parsed.size} 门课，确定导入吗？\n（识别不到的请手动添加，已存在的不重复导入）")
                .setPositiveButton("导入") { _, _ ->
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            val repo = CourseRepository(this@MainActivity)
                            val existing = repo.getAll()
                            parsed.filter { p ->
                                existing.none {
                                    it.weekday == p.weekday && it.startSlot == p.startSlot && it.name == p.name
                                }
                            }.forEach { repo.insert(it) }
                        }
                        viewModel.refresh()
                        WidgetRefreshHelper.refresh(this@MainActivity)
                        Toast.makeText(this@MainActivity, "导入完成", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }
}
