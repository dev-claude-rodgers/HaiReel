package com.rodgers.routist.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 過去に配達した住所の履歴。配達回数・最終配達日時で候補の優先順を決める。 */
@Entity(tableName = "known_addresses")
data class KnownAddressEntity(
    @PrimaryKey val address: String,          // 住所（主キー）
    val name: String?      = null,            // 最後に使った受取人名
    val deliveryCount: Int = 1,               // 配達回数（多いほど上位に表示）
    val lastDeliveredAt: Long = System.currentTimeMillis()
)
