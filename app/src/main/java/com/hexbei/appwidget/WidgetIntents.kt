package com.hexbei.appwidget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * C6 核心能力之一：桌面小组件 → App 的深度链接入口（appwidget://course/xxx）
 *
 * 6 个小组件不再各写一套"显式 Intent"，统一从这里生成带 data 的 PendingIntent：
 * App 侧（MainActivity.handleDeepLink）按 path 分发到对应功能，外部（adb / 浏览器 / 快捷方式）也能走同一套 URL。
 *
 * 支持的动作：
 * today      今日课程组件 → 打开今日课表
 * next       下一节课组件 → 打开并提示下一节
 * countdown  倒计时组件   → 打开并提示剩余时间
 * week       周历组件     → 打开并提示本周统计
 * progress   进度环组件   → 打开并提示本周进度
 * settings   快捷方式     → 直接进小组件设置页
 * add        快捷方式     → 直接弹出"加一门课"
 * import     快捷方式     → 直接拉起 PDF 导入
 */
object WidgetIntents {

    const val SCHEME = "appwidget"
    const val HOST = "course"

    fun uri(action: String): Uri = Uri.parse("$SCHEME://$HOST/$action")

    /**
     * 小组件点击用的 PendingIntent。
     * 用"显式组件 + ACTION_VIEW + data"：既能保证一定能打开 App（不依赖系统解析 intent-filter，
     * 避免历史上"点击无响应"的坑），又携带深链数据供 App 分发。
     */
    fun open(context: Context, action: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            this.action = Intent.ACTION_VIEW
            data = uri(action)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            context,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
