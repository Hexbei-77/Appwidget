package com.hexbei.appwidget.data

/**
 * 课程数据访问接口（对齐 Room DAO 风格）。
 *
 * 现在是 JSON 文件实现（CourseRepository），以后想换 Room 时，
 * 只需再写一个 Room 实现，界面和小组件完全不用改。
 */
interface CourseDao {

    /** 读取全部课程 */
    suspend fun getAll(): List<Course>

    /** 按 id 获取单门课程 */
    suspend fun getById(id: Long): Course?

    /** 新增课程，返回新 id */
    suspend fun insert(course: Course): Long

    /** 更新课程 */
    suspend fun update(course: Course)

    /** 删除课程 */
    suspend fun delete(id: Long)
}
