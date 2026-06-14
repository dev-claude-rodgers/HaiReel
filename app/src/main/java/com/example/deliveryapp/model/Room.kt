package com.rodgers.routist.model

import java.util.UUID

data class Room(
    val id: String = UUID.randomUUID().toString(),
    val number: String,
    val note: String = "",
    val isCompleted: Boolean = false,
    val status: String? = null,    // null=未訪問 / 不在 / 断り / アポ獲得 / 再訪問
    val visitedAt: Long? = null    // 訪問時刻（epoch ms）
)
