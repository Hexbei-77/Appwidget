package com.hexbei.appwidget

import com.hexbei.appwidget.data.Course
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.InputStream

/**
 * 课表 PDF 解析器（表格版）
 *
 * 大学教务系统导出的课表大多是"星期为列、节次为行"的表格：
 *   星期   | 星期一 | 星期二 | 星期三 | ...
 *   1-2节  |        |        | 操作系统D (1-2节)1-4周/场地:教1楼201/教师:陈磊/…
 * 纯文本按行读会把不同列的内容混在一起（单元格里没有"星期"字样），所以这里改为：
 *   1. 用自定义 PDFTextStripper 收集每段文字的坐标（TextPosition）
 *   2. 定位"星期一…星期日"表头，按坐标把文字段落归到对应星期列
 *      （PDF 可能是旋转 90° 导出的，自动判断列方向是 x 还是 y）
 *   3. 同一列内按行坐标排序，用状态机把"课程名 + (N-M节)周次/场地/教师"拼成课程单元
 *   4. 解析节次、周次（含单双周）、场地、教师
 *
 * 解析不到（如"其他课程：大学体育…"这类没有星期/节次的条目）会被忽略；
 * 若整份 PDF 没有表格结构，退回旧的按行文本解析（"周一 1-2节 高等数学 教1-203"）。
 */
object PdfCourseParser {

    /** 一段带坐标的文字（坐标来自 PDFBox 的 TextPosition，已做方向调整） */
    data class TextItem(val text: String, val x: Float, val y: Float, val width: Float)

    private val WEEK_NAMES = listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")
    private val SLOT_RE = Regex("[（(](\\d{1,2})\\s*[-~—至]\\s*(\\d{1,2})\\s*节[）)]")
    private val NAME_RE = Regex("^[A-Za-z\\u4e00-\\u9fa5ⅤⅣⅢⅡⅠ\\-]+$")
    private val NUMBER_RE = Regex("^\\d{1,2}$")
    private val SKIP_WORDS = setOf("时间段", "节次", "上午", "下午", "晚上")

    /** 从 PDF 输入流解析课程 */
    fun parse(input: InputStream): List<Course> {
        val doc = PDDocument.load(input)
        return try {
            val items = extractItems(doc)
            val byTable = parseItems(items)
            if (byTable.isNotEmpty()) {
                byTable
            } else {
                // 兜底：不是表格版课表时，按行文本解析
                parseTextLines(PDFTextStripper().apply { sortByPosition = true }.getText(doc))
            }
        } finally {
            doc.close()
        }
    }

    /** 提取带坐标的文字段落 */
    fun extractItems(doc: PDDocument): List<TextItem> {
        val stripper = PositionalStripper()
        stripper.sortByPosition = true
        stripper.getText(doc)
        return stripper.items
    }

    /**
     * 表格解析核心（纯逻辑，不依赖 Android / PDFBox，方便单元测试）
     *
     * @param items 文字段落及其坐标（任意方向：无论 PDF 是否旋转，坐标轴会自动判断）
     */
    fun parseItems(items: List<TextItem>): List<Course> {
        if (items.size < 7) return emptyList()

        // 1) 定位星期表头
        val headers = mutableListOf<Pair<Int, TextItem>>()
        for (item in items) {
            val t = item.text.trim()
            if (t.length > 5) continue
            val idx = WEEK_NAMES.indexOfFirst { t.contains(it) }
            if (idx >= 0) headers.add(idx to item)
        }
        if (headers.size < 5) return emptyList()

        // 2) 判断列方向：表头在 y 轴分布更散 → PDF 被旋转过，列方向是 y
        val xs = headers.map { it.second.x }
        val ys = headers.map { it.second.y }
        val rotated = (ys.max() - ys.min()) > (xs.max() - xs.min())
        val colPos = { it: TextItem -> if (rotated) it.y else it.x }
        val rowPos = { it: TextItem -> if (rotated) it.x else it.y }

        // 3) 列边界：按位置排序 + 中点切分；colWeekdays 保留每列真实星期
        val sorted = headers.sortedBy { colPos(it.second) }
        val colWeekdays = sorted.map { it.first + 1 }
        val positions = sorted.map { colPos(it.second) }
        val bounds: List<Float> = buildList {
            val mids = positions.zipWithNext { a, b -> (a + b) / 2f }
            add(positions.first() - (mids.first() - positions.first()))
            addAll(mids)
            add(positions.last() + (positions.last() - mids.last()))
        }

        fun weekdayOf(item: TextItem): Int {
            val c = colPos(item)
            for (i in 0 until bounds.size - 1) {
                if (c >= bounds[i] && c < bounds[i + 1]) return colWeekdays[i]
            }
            return 0
        }

        // 4) 过滤表头/行标签/文档信息，按（星期列，行坐标）排序
        val ordered = items.mapNotNull { item ->
            val t = item.text.trim()
            if (t.isEmpty() || t in SKIP_WORDS || NUMBER_RE.matches(t)) return@mapNotNull null
            if (t.contains("时间段") || t.contains("节次") || t.contains("课表") ||
                t.contains("学年") || t.contains("学号")
            ) return@mapNotNull null
            val wd = weekdayOf(item)
            if (wd == 0) return@mapNotNull null
            Triple(wd, rowPos(item), t)
        }.sortedWith(compareBy({ it.first }, { it.second }))
        if (ordered.isEmpty()) return emptyList()

        // 5) 状态机：课程名 → (N-M节)详情 → 下一门
        val raws = mutableListOf<RawCell>()
        var pendingName = ""
        var inDetail = false
        for ((wd, _, t) in ordered) {
            if (SLOT_RE.containsMatchIn(t)) {
                raws.add(RawCell(wd, pendingName, StringBuilder(t)))
                pendingName = ""
                inDetail = true
            } else if (inDetail) {
                // 详情续行：含 / 或 : 的片段，或不像课程名（例如"网工程241;物联网工程"）
                if (looksLikeName(t)) {
                    pendingName = t
                    inDetail = false
                } else {
                    raws.last().text.append(t)
                }
            } else if (looksLikeName(t)) {
                pendingName = t
            }
        }

        // 6) 字段解析 + 去重
        return raws.mapNotNull { raw ->
            val text = raw.text.toString()
            val m = SLOT_RE.find(text) ?: return@mapNotNull null
            val start = m.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            val end = m.groupValues[2].toIntOrNull() ?: return@mapNotNull null
            if (end < start || start < 1 || end > 12) return@mapNotNull null

            // 周次：节次括号后到第一个 "/" 之前，例如 "3周,7-13周(单)"
            val weeksPart = text.substring(m.range.last + 1).substringBefore("/")
            val oddEven = when {
                weeksPart.contains("单") -> 1
                weeksPart.contains("双") -> 2
                else -> 0
            }
            val numbers = Regex("\\d+").findAll(weeksPart)
                .mapNotNull { it.value.toIntOrNull() }.toList()
            val startWeek = (numbers.minOrNull() ?: 1).coerceIn(1, 20)
            val endWeek = (numbers.maxOrNull() ?: 16).coerceIn(startWeek, 20)

            val name = raw.name.trim().ifEmpty { "未命名课程" }
            val location = Regex("/场地[:：]([^/]*)").find(text)
                ?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() } ?: ""
            val teacher = Regex("/教师[:：]([^/]*)").find(text)
                ?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() } ?: ""

            Course(
                name = name,
                teacher = teacher,
                location = location,
                weekday = raw.weekday,
                startSlot = start,
                endSlot = end,
                startWeek = startWeek,
                endWeek = endWeek,
                oddEven = oddEven,
                colorIndex = (name.hashCode() and Int.MAX_VALUE) % 8
            )
        }.distinctBy {
            "${it.weekday}-${it.startSlot}-${it.endSlot}-${it.name}-${it.startWeek}-${it.endWeek}-${it.oddEven}"
        }.sortedWith(compareBy({ it.weekday }, { it.startSlot }))
    }

    /** 旧的按行文本解析，作为非表格课表的兜底 */
    fun parseTextLines(text: String): List<Course> {
        val weekdayMap = mapOf(
            "一" to 1, "二" to 2, "三" to 3, "四" to 4,
            "五" to 5, "六" to 6, "日" to 7, "天" to 7
        )
        val result = mutableListOf<Course>()
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEach
            val day = Regex("(?:周|星期|礼拜)([一二三四五六日天])").find(line) ?: return@forEach
            val weekday = weekdayMap[day.groupValues[1]] ?: return@forEach
            val slot = Regex("第?([1-9]|1[0-2])\\s*[-~至]\\s*([1-9]|1[0-2])\\s*节?").find(line)
                ?: return@forEach
            val start = slot.groupValues[1].toInt()
            val end = slot.groupValues[2].toInt()
            if (end < start) return@forEach

            val tokens = line.substring(slot.range.last + 1).trim()
                .split(Regex("\\s+")).filter { it.isNotBlank() }
            if (tokens.isEmpty()) return@forEach
            val name = tokens.first().trimEnd('（', '(', '：', ':')
            if (name.length > 40) return@forEach
            val location = tokens.drop(1).firstOrNull {
                it.contains(Regex("[楼教馆室]")) || it.matches(Regex(".*[A-Za-z0-9-]+.*"))
            } ?: ""
            val teacher = tokens.drop(1).firstOrNull { it != location } ?: ""

            result.add(
                Course(
                    name = name, teacher = teacher, location = location, weekday = weekday,
                    startSlot = start, endSlot = end, startWeek = 1, endWeek = 16,
                    colorIndex = (name.hashCode() and Int.MAX_VALUE) % 8
                )
            )
        }
        return result.distinctBy { "${it.weekday}-${it.startSlot}-${it.name}" }
    }

    /** 课程名候选：纯中文/字母，不含路径符号、括号、数字 */
    private fun looksLikeName(t: String): Boolean {
        if (t.contains("/") || t.contains(":") || t.contains("：") ||
            t.contains(";") || t.contains("；")
        ) return false
        if (t.contains("(") || t.contains("（") || t.contains(")") || t.contains("）")) return false
        return NAME_RE.matches(t) && t.length in 2..40
    }

    private class RawCell(val weekday: Int, val name: String, val text: StringBuilder)

    /** 收集每段文字及其坐标（PDFBox 的 writeString 回调） */
    private class PositionalStripper : PDFTextStripper() {
        val items = mutableListOf<TextItem>()

        override fun writeString(text: String, textPositions: List<TextPosition>) {
            if (text.isBlank() || textPositions.isEmpty()) return
            val clean = text.trim()
            if (clean.isEmpty()) return
            val first = textPositions.first()
            val last = textPositions.last()
            val width = (last.xDirAdj + last.widthDirAdj) - first.xDirAdj
            items.add(TextItem(clean, first.xDirAdj + width / 2f, first.yDirAdj, width))
        }
    }
}
