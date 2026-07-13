package com.rodgers.haireel.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.rodgers.haireel.model.Delivery
import com.rodgers.haireel.model.Room as RoomModel

private val gson = Gson()

@Entity(
    tableName = "deliveries",
    indices = [Index("group_id")]
)
data class DeliveryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "group_id") val groupId: String,
    @ColumnInfo(name = "sort_order") val order: Int,
    val name: String? = null,
    @ColumnInfo(name = "name_kana") val nameKana: String? = null,
    val address: String,
    @ColumnInfo(name = "geocoded_address") val geocodedAddress: String? = null,
    val note: String? = null,
    @ColumnInfo(name = "photo_uri") val photoUri: String? = null,
    @ColumnInfo(name = "photo_uris_json") val photoUrisJson: String? = null,
    @ColumnInfo(name = "rooms_json") val roomsJson: String? = null,
    @ColumnInfo(name = "time_slot") val timeSlot: String? = null,
    @ColumnInfo(name = "open_time") val openTime: String? = null,
    @ColumnInfo(name = "close_time") val closeTime: String? = null,
    @ColumnInfo(name = "package_count") val packageCount: Int = 0,
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    @ColumnInfo(name = "is_completed") val isCompleted: Boolean = false,
    @ColumnInfo(name = "is_geocoded") val isGeocoded: Boolean = false
)

fun DeliveryEntity.toDelivery(): Delivery {
    val stringListType = object : TypeToken<List<String>>() {}.type
    val roomListType = object : TypeToken<List<RoomModel>>() {}.type
    return Delivery(
        id = id,
        order = order,
        name = name,
        nameKana = nameKana,
        address = address,
        geocodedAddress = geocodedAddress,
        note = note,
        photoUri = photoUri,
        photoUris = photoUrisJson?.let { gson.fromJson(it, stringListType) },
        rooms = roomsJson?.let { gson.fromJson(it, roomListType) },
        timeSlot = timeSlot,
        openTime = openTime,
        closeTime = closeTime,
        packageCount = packageCount,
        lat = lat,
        lng = lng,
        isCompleted = isCompleted,
        isGeocoded = isGeocoded
    )
}

fun Delivery.toEntity(groupId: String): DeliveryEntity = DeliveryEntity(
    id = id,
    groupId = groupId,
    order = order,
    name = name,
    nameKana = nameKana,
    address = address,
    geocodedAddress = geocodedAddress,
    note = note,
    photoUri = photoUri,
    photoUrisJson = photoUris?.let { gson.toJson(it) },
    roomsJson = rooms?.let { gson.toJson(it) },
    timeSlot = timeSlot,
    openTime = openTime,
    closeTime = closeTime,
    packageCount = packageCount,
    lat = lat,
    lng = lng,
    isCompleted = isCompleted,
    isGeocoded = isGeocoded
)
