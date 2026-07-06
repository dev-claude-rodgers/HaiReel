package com.rodgers.haireel.util

interface GeocodingApi {
    val isRequestDenied: Boolean
    val biasLat: Double
    val biasLng: Double

    fun configure(apiKey: String)
    fun setAreaHint(hint: String)
    fun setBias(lat: Double, lng: Double)
    fun hasBias(): Boolean
    fun resultMatchesInput(inputAddress: String, geocodedAddress: String): Boolean
    fun extractPrefCity(address: String): List<String>

    suspend fun geocode(address: String): GeocodingClient.GeoResult?
    suspend fun geocodeExact(address: String): GeocodingClient.GeoResult?
    suspend fun geocodeCandidates(address: String): List<GeocodingClient.GeoResult>
    suspend fun searchPlaces(query: String): List<GeocodingClient.PlaceInfo>
    suspend fun reverseGeocode(lat: Double, lng: Double): GeocodingClient.GeoResult?
    suspend fun searchNearbyName(lat: Double, lng: Double): String?
}
