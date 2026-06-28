package com.rodgers.haireel.model

data class ReportPattern(
    val id: Int,
    val title: String = "稼働報告書",
    val clientName: String = "",
    val driverName: String = "",
    val closingDay: Int = 25,
    val excelColumns: List<ExcelColumn> = defaultExcelColumns(),
    // 0=個建て  1=車建て（日当）  2=時間制  3=なし
    val paymentType: Int = 3,
    val unitPrice: Int = 0
) {
    companion object {
        fun defaultExcelColumns() = listOf(
            ExcelColumn(ColumnType.START_TIME,    ColumnType.START_TIME.defaultLabel),
            ExcelColumn(ColumnType.END_TIME,      ColumnType.END_TIME.defaultLabel),
            ExcelColumn(ColumnType.WORKING_HOURS, ColumnType.WORKING_HOURS.defaultLabel),
            ExcelColumn(ColumnType.DELIVERY_COUNT, ColumnType.DELIVERY_COUNT.defaultLabel),
            ExcelColumn(ColumnType.FUEL_COST,     ColumnType.FUEL_COST.defaultLabel)
        )

        fun default(id: Int = 0) = ReportPattern(id = id)
    }
}
