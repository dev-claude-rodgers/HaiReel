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
    val showFuel: Boolean = true,
    val showMeter: Boolean = false,
    val showIncome: Boolean = false,
    val showArea: Boolean = true,
    val showRemarks: Boolean = true,
    // 0=個建て  1=車建て（日当）  2=時間制  3=なし
    val paymentType: Int = 3,
    val unitPrice: Int = 0
) {
    companion object {
        fun default(id: Int = 0) = ReportPattern(id = id)
    }
}
