package com.rodgers.routist.model

data class ReportPattern(
    val id: Int,
    val title: String = "稼働報告書",
    val clientName: String = "",
    val driverName: String = "",
    val closingDay: Int = 25,
    val deliveryLabel: String = "配達件数",
    val packageLabel: String = "個数",
    val showTime: Boolean = true,
    val showDelivery: Boolean = true,
    val showPackage: Boolean = true,
    val showDistance: Boolean = true,
    val showArea: Boolean = true,
    val showRemarks: Boolean = true
) {
    companion object {
        fun default(id: Int = 0) = ReportPattern(id = id)
    }
}
