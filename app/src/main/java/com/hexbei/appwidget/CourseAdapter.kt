package com.hexbei.appwidget

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hexbei.appwidget.data.Course

/**
 * RecyclerView 的适配器：把"课程列表"渲染成一排排可滚动列表项。
 *
 * 它只负责"怎么把一门课画成一个列表项"，不关心数据从哪来。
 * 列表项显示：课程名 + 一行摘要信息（星期/节次/教室/周次/单双周）+ 左侧颜色条。
 */
class CourseAdapter(
    private val onClick: (Course) -> Unit
) : ListAdapter<Course, CourseAdapter.ViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_course, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvName = view.findViewById<TextView>(R.id.tvCourseName)
        private val tvInfo = view.findViewById<TextView>(R.id.tvCourseInfo)
        private val colorBar = view.findViewById<View>(R.id.colorBar)

        fun bind(course: Course) {
            tvName.text = course.name

            val weekText = when (course.oddEven) {
                1 -> "单周"
                2 -> "双周"
                else -> "每周"
            }
            tvInfo.text = "周${course.weekday} 第${course.startSlot}-${course.endSlot}节" +
                    " · ${course.location} · 第${course.startWeek}-${course.endWeek}周 $weekText"

            colorBar.setBackgroundColor(
                CourseColors.palette[course.colorIndex % CourseColors.palette.size]
            )
            itemView.setOnClickListener { onClick(course) }
        }
    }

    companion object {
        /** DiffUtil：高效比较新旧列表，只更新变化的项（列表滚动不闪烁） */
        private val Diff = object : DiffUtil.ItemCallback<Course>() {
            override fun areItemsTheSame(a: Course, b: Course) = a.id == b.id
            override fun areContentsTheSame(a: Course, b: Course) = a == b
        }
    }
}
