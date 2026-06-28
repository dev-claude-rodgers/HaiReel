package com.rodgers.haireel.model

enum class ColumnType(val defaultLabel: String) {
    START_TIME("開始時刻"),
    END_TIME("終了時刻"),
    WORKING_HOURS("稼働時間"),
    DELIVERY_COUNT("配達件数"),
    PACKAGE_COUNT("個数"),
    DISTANCE("走行距離(km)"),
    FUEL_COST("燃料費"),
    METER_START("開始メーター(km)"),
    METER_END("終了メーター(km)"),
    INCOME("収入"),
    AREA("エリア"),
    REMARKS("備考")
}

data class ExcelColumn(val type: ColumnType, val label: String)

fun List<ExcelColumn>.encodeToJson(): String {
    val arr = org.json.JSONArray()
    forEach { col ->
        arr.put(org.json.JSONObject().apply {
            put("type",  col.type.name)
            put("label", col.label)
        })
    }
    return arr.toString()
}

fun decodeExcelColumns(s: String): List<ExcelColumn> {
    if (s.isBlank()) return ReportPattern.defaultExcelColumns()
    return try {
        val arr = org.json.JSONArray(s)
        (0 until arr.length()).mapNotNull { i ->
            val obj = arr.getJSONObject(i)
            val type = ColumnType.values().find { it.name == obj.optString("type") }
                ?: return@mapNotNull null
            ExcelColumn(type, obj.optString("label", type.defaultLabel))
        }.ifEmpty { ReportPattern.defaultExcelColumns() }
    } catch (_: Exception) {
        ReportPattern.defaultExcelColumns()
    }
}
