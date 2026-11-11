package com.hexbei.appwidget

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hexbei.appwidget.data.Course
import com.hexbei.appwidget.data.CourseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 课程列表的 ViewModel：把"读课程数据"这件事从 Activity 里拿出来。
 *
 * 之前 MainActivity 直接在 lifecycleScope 里读写仓库，页面一旋转就重新拉一遍；
 * 现在数据放在 ViewModel 里，Activity 只负责"显示"，旋转屏幕不丢数据、不阻塞主线程，
 * 符合课程设计指南里"协程 + ViewModel"的要求。
 */
class CourseViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = CourseRepository(app)
    private val _courses = MutableStateFlow<List<Course>>(emptyList())
    val courses: StateFlow<List<Course>> = _courses.asStateFlow()

    init {
        refresh()
    }

    /** 重新拉一次课程列表（增删改、导入后都调它） */
    fun refresh() {
        viewModelScope.launch { _courses.value = repo.getAll() }
    }

    /** 删一门课，删完自动刷新列表 */
    fun delete(id: Long) {
        viewModelScope.launch {
            repo.delete(id)
            refresh()
        }
    }

    /** 详情页按 id 查一门课（详情页自己也能直接调） */
    suspend fun getById(id: Long): Course? = repo.getById(id)
}
