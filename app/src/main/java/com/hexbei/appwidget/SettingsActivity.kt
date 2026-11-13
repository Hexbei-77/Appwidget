package com.hexbei.appwidget

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.hexbei.appwidget.data.CourseRepository
import com.hexbei.appwidget.data.DemoCourses
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 小组件设置页（App 的"配角"配置页）：
 *  1. 选主题色（手动，四套主题）
 *  2. 小组件字体大小（小 / 标准 / 大）
 *  3. 动态背景开关（按小时自动换色）
 *  4. 当前周（影响单双周判断、周历与进度环）
 * 所有改动立即刷新桌面小组件。
 */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        title = "小组件设置"

        setupThemeSection()
        setupFontSizeSection()
        setupDynamicBgSection()
        setupWeekSection()
        setupMottoSection()
        setupDemoSection()
    }

    /** 1. 主题色列表 */
    private fun setupThemeSection() {
        val themeContainer = findViewById<LinearLayout>(R.id.themeContainer)
        val current = ThemeStore.get(this)
        ThemePalette.themes.forEachIndexed { index, theme ->
            val row = TextView(this).apply {
                text = theme.name + if (index == current) "  ✓" else ""
                textSize = 16f
                setPadding(16.dp(), 16.dp(), 16.dp(), 16.dp())
                setBackgroundColor(theme.bg)
                setTextColor(theme.accent)
                setOnClickListener {
                    ThemeStore.set(this@SettingsActivity, index)
                    WidgetStore.setDynamicBg(this@SettingsActivity, false)
                    WidgetRefreshHelper.refresh(this@SettingsActivity)
                    Toast.makeText(
                        this@SettingsActivity,
                        "已切换到「${theme.name}」",
                        Toast.LENGTH_SHORT
                    ).show()
                    recreate()
                }
            }
            themeContainer.addView(row, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 12.dp() })
        }
    }

    /** 2. 字体大小三选 */
    private fun setupFontSizeSection() {
        val fontSizeContainer = findViewById<LinearLayout>(R.id.fontSizeContainer)
        val currentFont = WidgetStore.getFontSize(this)
        listOf("小" to 0, "标准" to 1, "大" to 2).forEach { (label, value) ->
            val btn = TextView(this).apply {
                text = label + if (currentFont == value) "  ✓" else ""
                textSize = 15f
                setPadding(24.dp(), 14.dp(), 24.dp(), 14.dp())
                setBackgroundColor(if (currentFont == value) 0xFFE3F2FD.toInt() else 0xFFF0F0F0.toInt())
                setTextColor(Color.DKGRAY)
                setOnClickListener {
                    WidgetStore.setFontSize(this@SettingsActivity, value)
                    WidgetRefreshHelper.refresh(this@SettingsActivity)
                    recreate()
                }
            }
            fontSizeContainer.addView(btn, LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            ).apply { marginEnd = 12.dp() })
        }
    }

    /** 3. 动态背景开关 */
    private fun setupDynamicBgSection() {
        val dynamicBgContainer = findViewById<LinearLayout>(R.id.dynamicBgContainer)
        val dynamicOn = WidgetStore.getDynamicBg(this)
        listOf("开启" to true, "关闭" to false).forEach { (label, value) ->
            val btn = TextView(this).apply {
                text = label + if (dynamicOn == value) "  ✓" else ""
                textSize = 15f
                setPadding(24.dp(), 14.dp(), 24.dp(), 14.dp())
                setBackgroundColor(if (dynamicOn == value) 0xFFE3F2FD.toInt() else 0xFFF0F0F0.toInt())
                setTextColor(Color.DKGRAY)
                setOnClickListener {
                    WidgetStore.setDynamicBg(this@SettingsActivity, value)
                    WidgetRefreshHelper.refresh(this@SettingsActivity)
                    Toast.makeText(
                        this@SettingsActivity,
                        "动态背景已${if (value) "开启" else "关闭"}",
                        Toast.LENGTH_SHORT
                    ).show()
                    recreate()
                }
            }
            dynamicBgContainer.addView(btn, LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            ).apply { marginEnd = 12.dp() })
        }
    }

    /** 4. 当前周：点击弹输入框修改 */
    private fun setupWeekSection() {
        val weekContainer = findViewById<LinearLayout>(R.id.weekContainer)
        val btn = TextView(this).apply {
            text = "当前第 ${CurrentWeekStore.get(this@SettingsActivity)} 周（点击修改）"
            textSize = 15f
            setPadding(24.dp(), 14.dp(), 24.dp(), 14.dp())
            setBackgroundColor(0xFFF0F0F0.toInt())
            setTextColor(Color.DKGRAY)
            setOnClickListener { showWeekDialog() }
        }
        weekContainer.addView(btn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
    }

    private fun showWeekDialog() {
        val et = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "当前第几周（1-20）"
            setText(CurrentWeekStore.get(this@SettingsActivity).toString())
        }
        AlertDialog.Builder(this)
            .setTitle("修改当前周")
            .setView(et)
            .setPositiveButton("保存") { _, _ ->
                val week = et.text.toString().toIntOrNull()
                if (week == null || week !in 1..20) {
                    Toast.makeText(this, "请输入 1-20 之间的周数", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                CurrentWeekStore.set(this, week)
                WidgetRefreshHelper.refresh(this)
                Toast.makeText(this, "已设为第 $week 周", Toast.LENGTH_SHORT).show()
                recreate()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /** 5. 进度环下方的鼓励语：点击弹输入框改成自己的口号 */
    private fun setupMottoSection() {
        val mottoContainer = findViewById<LinearLayout>(R.id.mottoContainer)
        val btn = TextView(this).apply {
            text = "当前文案：" + WidgetStore.getMotto(this@SettingsActivity) + "（点击修改）"
            textSize = 15f
            setPadding(24.dp(), 14.dp(), 24.dp(), 14.dp())
            setBackgroundColor(0xFFF0F0F0.toInt())
            setTextColor(Color.DKGRAY)
            setOnClickListener { showMottoDialog() }
        }
        mottoContainer.addView(btn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
    }

    private fun showMottoDialog() {
        val et = EditText(this).apply {
            hint = "比如：好好学习，天天向上！"
            setText(WidgetStore.getMotto(this@SettingsActivity))
            setSelection(text.length)
        }
        AlertDialog.Builder(this)
            .setTitle("修改进度环鼓励语")
            .setView(et)
            .setPositiveButton("保存") { _, _ ->
                val text = et.text.toString().trim().ifEmpty { "好好学习，天天向上！" }
                WidgetStore.setMotto(this, text)
                WidgetRefreshHelper.refresh(this)
                Toast.makeText(this, "鼓励语已更新", Toast.LENGTH_SHORT).show()
                recreate()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /** 6. 演示模式：一键写入铺满一周的演示课表 */
    private fun setupDemoSection() {
        val demoContainer = findViewById<LinearLayout>(R.id.demoContainer)
        val btn = TextView(this).apply {
            text = "一键铺满一周演示课表"
            textSize = 15f
            setPadding(24.dp(), 14.dp(), 24.dp(), 14.dp())
            setBackgroundColor(0xFFF0F0F0.toInt())
            setTextColor(Color.DKGRAY)
            setOnClickListener {
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        DemoCourses.load(CourseRepository(this@SettingsActivity))
                    }
                    WidgetRefreshHelper.refresh(this@SettingsActivity)
                    Toast.makeText(
                        this@SettingsActivity,
                        "已写入演示课表，桌面组件已刷新",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        demoContainer.addView(btn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
}
