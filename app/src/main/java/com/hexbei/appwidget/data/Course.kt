package com.hexbei.appwidget.data

import org.json.JSONObject

/**
 * 课程数据模型（对应课程表数据库里的 Course 表）
 *
 * 字段说明：
 *  id        - 主键，自增
 *  name      - 课程名
 *  teacher   - 教师
 *  location  - 教室
 *  weekday   - 星期，1..7（周一..周日）
 *  startSlot - 起始节次
 *  endSlot   - 结束节次
 *  startWeek - 起始周
 *  endWeek   - 结束周
 *  oddEven   - 单双周：0=每周都上，1=单周，2=双周
 *  colorIndex- 主题色索引（小组件/课表配色保持一致）
 *  note      - 备注
 */
data class Course(
    val id: Long = 0L,
    val name: String,
    val teacher: String,
    val location: String,
    val weekday: Int,        // 1..7
    val startSlot: Int,
    val endSlot: Int,
    val startWeek: Int,
    val endWeek: Int,
    val oddEven: Int = 0,    // 0=每周 1=单周 2=双周
    val colorIndex: Int = 0,
    val note: String = ""
) {
    /** 序列化为 JSON，供文件持久化使用 */
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("teacher", teacher)
        put("location", location)
        put("weekday", weekday)
        put("startSlot", startSlot)
        put("endSlot", endSlot)
        put("startWeek", startWeek)
        put("endWeek", endWeek)
        put("oddEven", oddEven)
        put("colorIndex", colorIndex)
        put("note", note)
    }

    companion object {
        /** 从 JSON 反序列化 */
        fun fromJson(o: JSONObject): Course = Course(
            id = o.optLong("id"),
            name = o.optString("name"),
            teacher = o.optString("teacher"),
            location = o.optString("location"),
            weekday = o.optInt("weekday"),
            startSlot = o.optInt("startSlot"),
            endSlot = o.optInt("endSlot"),
            startWeek = o.optInt("startWeek"),
            endWeek = o.optInt("endWeek"),
            oddEven = o.optInt("oddEven"),
            colorIndex = o.optInt("colorIndex"),
            note = o.optString("note")
        )
    }
}
