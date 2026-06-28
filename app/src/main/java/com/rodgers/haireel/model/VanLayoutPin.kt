package com.rodgers.haireel.model

import java.util.UUID

/** バン荷室写真上のピン（配達先の位置マーカー） */
data class VanLayoutPin(
    val id: String         = UUID.randomUUID().toString(),
    val xPercent: Float,   // 写真の横位置 0.0〜1.0
    val yPercent: Float,   // 写真の縦位置 0.0〜1.0
    val deliveryId: String,
    val orderLabel: Int    // 配達番号（ピンに表示する数字）
)

/** 1つのビュー（例: 右ドア・左ドア）の写真＋ピンセット */
data class VanView(
    val id: String              = UUID.randomUUID().toString(),
    val name: String,           // ユーザーが自由に設定する名前
    val photoUri: String        = "",
    val pins: List<VanLayoutPin> = emptyList()
)

/** グループごとの荷室レイアウト（複数ビューを管理） */
data class VanLayout(
    val views: List<VanView> = emptyList()
)
