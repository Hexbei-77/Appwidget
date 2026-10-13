package com.hexbei.appwidget

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 主界面：课程列表。
 * 观察 CourseViewModel 的课程数据，数据一变列表自动刷新。
 */
class MainActivity : AppCompatActivity() {

    private val viewModel: CourseViewModel by viewModels()
    private lateinit var adapter: CourseAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val recycler = findViewById<RecyclerView>(R.id.rvCourses)
        val emptyView = findViewById<TextView>(R.id.tvEmpty)

        adapter = CourseAdapter(onClick = { /* 详情页后续再加 */ })
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        findViewById<FloatingActionButton>(R.id.fabAdd).setOnClickListener {
            startActivity(Intent(this, AddCourseActivity::class.java))
        }

        // 订阅数据：课程变化时刷新列表 + 切换空态提示
        lifecycleScope.launch {
            viewModel.courses.collectLatest { list ->
                adapter.submitList(list)
                emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    /** 从新增页返回时重新加载，保证列表最新 */
    override fun onResume() {
        super.onResume()
        viewModel.load()
    }
}
