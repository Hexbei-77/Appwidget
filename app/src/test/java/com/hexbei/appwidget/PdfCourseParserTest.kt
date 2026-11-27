package com.hexbei.appwidget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PdfCourseParser 单元测试
 *
 * 样本 pdf_items.tsv 来自真实课表 PDF（何锦雯 2026-2027-1）的逐行文字坐标导出，
 * 用它可以离线验证"坐标表格解析"：星期列定位、节次/周次/单双周/场地/教师提取。
 */
class PdfCourseParserTest {

    private fun loadItems(): List<PdfCourseParser.TextItem> {
        val stream = javaClass.classLoader!!.getResourceAsStream("pdf_items.tsv")
            ?: error("测试样本 pdf_items.tsv 不存在")
        return stream.bufferedReader(Charsets.UTF_8).readLines().mapNotNull { line ->
            if (line.isBlank()) return@mapNotNull null
            val p = line.split('\t')
            if (p.size < 4) return@mapNotNull null
            PdfCourseParser.TextItem(
                text = p[3],
                x = p[0].toFloatOrNull() ?: return@mapNotNull null,
                y = p[1].toFloatOrNull() ?: return@mapNotNull null,
                width = p[2].toFloatOrNull() ?: 0f
            )
        }
    }

    /** 真实课表：14 个课程单元（其中"计算机组成原理D"周三/周五各一条单双周） */
    @Test
    fun parsesRealTimetableTable() {
        val courses = PdfCourseParser.parseItems(loadItems())

        assertEquals("应解析出 14 门课程单元", 14, courses.size)
        assertTrue("所有课程都应落在周一~周日", courses.all { it.weekday in 1..7 })
        assertTrue("所有课程节次都应在 1~12", courses.all { it.startSlot in 1..12 && it.endSlot in 1..12 })
        assertTrue("结束节次不小于开始节次", courses.all { it.endSlot >= it.startSlot })
        assertTrue("课程名不应为空", courses.all { it.name.isNotBlank() })

        // 抽查：周二 3-4 节 物联网传感与控制技术（3周,7-13周单，学10楼503，吕佳）
        val sensor = courses.first {
            it.name == "物联网传感与控制技术" && it.weekday == 2 && it.startSlot == 3
        }
        assertEquals(4, sensor.endSlot)
        assertEquals(1, sensor.oddEven)
        assertEquals(3, sensor.startWeek)
        assertEquals(13, sensor.endWeek)
        assertEquals("学10楼503", sensor.location)
        assertEquals("吕佳", sensor.teacher)

        // 抽查：周三 1-2 节 操作系统D（1-4,6-16 周，教1楼201，陈磊）
        val osWed = courses.first { it.name == "操作系统D" && it.weekday == 3 && it.startSlot == 1 }
        assertEquals(16, osWed.endWeek)
        assertEquals("教1楼201", osWed.location)
        assertEquals("陈磊", osWed.teacher)

        // 抽查：周五 1-2 节有两条"计算机组成原理D"（单周一条、双周一条）
        val comp = courses.filter { it.name == "计算机组成原理D" && it.weekday == 5 && it.startSlot == 1 }
        assertEquals(2, comp.size)
        assertTrue(comp.any { it.oddEven == 1 })
        assertTrue(comp.any { it.oddEven == 2 })

        // 抽查：周日 8-9 节 物联网通信技术A（2周，学15楼209，张建锋）
        val comm = courses.first { it.name == "物联网通信技术A" && it.weekday == 7 && it.startSlot == 8 }
        assertEquals(9, comm.endSlot)
        assertEquals(2, comm.startWeek)
        assertEquals(2, comm.endWeek)
        assertEquals("学15楼209", comm.location)
    }

    /** 没有表格结构时，退回按行文本解析（"周一 1-2节 高等数学 教1-203 张三"） */
    @Test
    fun fallsBackToLineParsing() {
        val text = """
            周一 第1-2节 高等数学 教1-203 张三
            星期三 3-4节 计算机组成原理 学楼10楼503 李四
        """.trimIndent()
        val courses = PdfCourseParser.parseTextLines(text)
        assertEquals(2, courses.size)
        val math = courses.first { it.name == "高等数学" }
        assertEquals(1, math.weekday)
        assertEquals(1, math.startSlot)
        assertEquals(2, math.endSlot)
        assertEquals("教1-203", math.location)
    }

    /** 空输入 / 噪声输入不应崩 */
    @Test
    fun handlesEmptyInput() {
        assertTrue(PdfCourseParser.parseItems(emptyList()).isEmpty())
        assertTrue(PdfCourseParser.parseTextLines("").isEmpty())
    }
}
