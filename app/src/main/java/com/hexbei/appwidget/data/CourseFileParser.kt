package com.hexbei.appwidget.data

import com.hexbei.appwidget.PdfCourseParser
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * 多格式课表解析：Word（docx）和 CSV 先提取成纯文本，
 * 再复用 PDF 那套按行文本规则解析（parseTextLines）。
 *
 * 这里只做本地提取，不联网；识别不出来的条目交给界面提示手动添加。
 */
object CourseFileParser {

    /** 按文件类型解析，返回识别到的课程；识别不到返回空列表 */
    fun parse(extension: String, bytes: ByteArray): List<Course> {
        return when (extension.lowercase()) {
            "docx" -> PdfCourseParser.parseTextLines(docxToText(bytes))
            "csv" -> PdfCourseParser.parseTextLines(csvToText(bytes))
            else -> emptyList()
        }
    }

    /** docx 本质是 zip，读出 word/document.xml，去掉 XML 标签换成纯文本 */
    private fun docxToText(bytes: ByteArray): String {
        var xml = ""
        ZipInputStream(ByteArrayInputStream(bytes)).use { zin ->
            var entry = zin.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    xml = zin.readBytes().toString(Charsets.UTF_8)
                    break
                }
                entry = zin.nextEntry
            }
        }
        var text = xml.replace(Regex("</w:p>"), "\n")
        text = text.replace(Regex("<[^>]+>"), "")
        text = text.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
        return text
    }

    /** CSV 按行读出，逗号 / 分号 / 制表符换成空格，交给按行规则解析 */
    private fun csvToText(bytes: ByteArray): String {
        val text = bytes.toString(Charsets.UTF_8)
        return text.lineSequence().joinToString("\n") { it.replace(Regex("[,;\t]"), " ") }
    }
}
