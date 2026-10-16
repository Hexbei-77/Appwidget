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
 * 课程 ViewModel —— UI 与数据仓库之间的"桥梁"
 *
 * 为什么需要它（课程要求）：
 *  1. 它持有数据（课程列表），屏幕旋转时数据不丢；
 *  2. 它用 viewModelScope + 协程做数据操作，避免把 Repository 直接暴露给 Activity、
 *     也避免主线程阻塞。
 *
 * Activity/界面只观察 courses 这个 StateFlow 来自动刷新，不直接操作文件。
 */
class CourseViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CourseRepository(application)

    // StateFlow：可观察的数据流，界面订阅后数据一变就会自动刷新
    private val _courses = MutableStateFlow<List<Course>>(emptyList())
    val courses: StateFlow<List<Course>> = _courses.asStateFlow()

    init {
        load()
    }

    /** 重新从仓库读取课程，并刷新给界面 */
    fun load() {
        viewModelScope.launch {
            _courses.value = repository.getAll()
        }
    }

    /** 新增课程，完成后再刷新列表 */
    fun insert(course: Course, onDone: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = repository.insert(course)
            load()
            onDone(id)
        }
    }

    /** 更新课程 */
    fun update(course: Course) {
        viewModelScope.launch {
            repository.update(course)
            load()
        }
    }

    /** 删除课程 */
    fun delete(id: Long) {
        viewModelScope.launch {
            repository.delete(id)
            load()
        }
    }

    // 详情页用：当前正在查看的课程
    private val _currentCourse = MutableStateFlow<Course?>(null)
    val currentCourse: StateFlow<Course?> = _currentCourse.asStateFlow()

    /** 加载单门课程给详情页展示 */
    fun loadCourse(id: Long) {
        viewModelScope.launch {
            _currentCourse.value = repository.getById(id)
        }
    }
}
