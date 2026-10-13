package com.hexbei.appwidget.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File

/**
 * 课程数据仓库（第一版：JSON 文件持久化）
 *
 * 设计目标：
 *  1. 用 App 私有目录下的 courses.json 存数据，零第三方依赖，编译零风险。
 *  2. 接口设计成和 Room 的 DAO 一致（增删改查），
 *     之后升级到 Room 时只需替换本类的内部实现，UI 层不用改。
 *
 * 所有读写都在 IO 调度器执行，避免阻塞主线程（符合协程要求）。
 */
class CourseRepository(context: Context) {

    private val file: File = File(context.filesDir, "courses.json")

    /** 读取全部课程 */
    suspend fun getAll(): List<Course> = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext emptyList()
        val arr = JSONArray(file.readText())
        (0 until arr.length()).map { Course.fromJson(arr.getJSONObject(it)) }
    }

    /** 按 id 获取单门课程 */
    suspend fun getById(id: Long): Course? = withContext(Dispatchers.IO) {
        getAll().firstOrNull { it.id == id }
    }

    /** 新增课程，返回新 id */
    suspend fun insert(course: Course): Long = withContext(Dispatchers.IO) {
        val list = getAll().toMutableList()
        val newId = (list.maxOfOrNull { it.id } ?: 0L) + 1
        list.add(course.copy(id = newId))
        save(list)
        newId
    }

    /** 更新课程 */
    suspend fun update(course: Course) = withContext(Dispatchers.IO) {
        val list = getAll().toMutableList()
        val idx = list.indexOfFirst { it.id == course.id }
        if (idx >= 0) list[idx] = course
        save(list)
    }

    /** 删除课程 */
    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        save(getAll().filterNot { it.id == id })
    }

    /** 将课程列表写入文件 */
    private fun save(list: List<Course>) {
        val arr = JSONArray()
        list.sortedBy { it.id }.forEach { arr.put(it.toJson()) }
        file.writeText(arr.toString())
    }
}
