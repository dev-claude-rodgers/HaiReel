package com.rodgers.haireel.model

import java.util.UUID

data class DeliveryGroup(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val colorHex: String = "#F44336",
    val patternId: Int = -1  // -1 = 帳票パターン未設定
)

// グループごとのピン色（最大10グループ）
val GROUP_COLORS = listOf(
    "#F44336", // 赤
    "#2196F3", // 青
    "#4CAF50", // 緑
    "#FF9800", // オレンジ
    "#9C27B0", // 紫
    "#00BCD4", // シアン
    "#E91E63", // ピンク
    "#795548", // ブラウン
    "#607D8B", // グレー
    "#FFEB3B"  // 黄
)

fun colorForIndex(index: Int): String = GROUP_COLORS[index % GROUP_COLORS.size]
