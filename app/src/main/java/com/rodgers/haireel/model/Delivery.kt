package com.rodgers.haireel.model

import java.util.UUID

data class Delivery(
    val id: String = UUID.randomUUID().toString(),
    val order: Int,
    val name: String? = null,          // 店名・名前（任意）
    val nameKana: String? = null,      // 名前のふりがな（TTS読み上げ用・任意）
    val address: String,               // 住所（ジオコーディング済み）
    val geocodedAddress: String? = null,
    val note: String? = null,
    val photoUris: List<String>? = null,
    val rooms: List<Room>? = null,
    val timeSlot: String? = null,       // 時間帯指定（例: "14:00-16:00"）
    val openTime: String? = null,       // 営業開始時間（例: "09:00"）
    val closeTime: String? = null,      // 営業終了時間（例: "18:00"）
    val packageCount: Int = 0,          // 荷物の個数
    val dwellMinutes: Int? = null,      // 個別滞在時間（null=グローバル設定を使用）
    var lat: Double = 0.0,
    var lng: Double = 0.0,
    var isCompleted: Boolean = false,
    var isGeocoded: Boolean = false
) {
    val hasLocation: Boolean get() = isGeocoded && lat != 0.0 && lng != 0.0
    val roomList: List<Room> get() = rooms ?: emptyList()
    val displayAddress: String get() = if (!geocodedAddress.isNullOrBlank()) geocodedAddress else address
    val displayTitle: String get() = if (!name.isNullOrBlank()) name else address
    val allPhotoUris: List<String> get() = photoUris ?: emptyList()
}
