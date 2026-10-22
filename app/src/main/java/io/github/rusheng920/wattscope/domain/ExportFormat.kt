package io.github.rusheng920.wattscope.domain

/** 记录导出格式：两种文本 + 一张分享图。 */
enum class ExportFormat(val ext: String, val mime: String) {
    JSON("json", "application/json"),
    CSV("csv", "text/csv"),
    PNG("png", "image/png"),
}
