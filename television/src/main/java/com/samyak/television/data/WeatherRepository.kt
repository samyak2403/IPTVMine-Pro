package com.samyak.television.data

import android.content.Context
import android.content.SharedPreferences

class WeatherRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_API_KEY = "saved_api_key"
        private const val KEY_CITY_NAME = "saved_city_name"
        private const val KEY_LAT = "saved_lat"
        private const val KEY_LON = "saved_lon"
        private const val KEY_UNITS = "saved_units"
    }

    fun getApiKey(): String {
        return prefs.getString(KEY_API_KEY, "22283a5841cda522322bc99d69f9d07a") ?: "22283a5841cda522322bc99d69f9d07a"
    }

    fun saveApiKey(apiKey: String) {
        prefs.edit().putString(KEY_API_KEY, apiKey).apply()
    }

    fun getCityName(): String {
        return prefs.getString(KEY_CITY_NAME, "New York") ?: "New York"
    }

    fun getCoordinates(): Pair<Double, Double> {
        val lat = prefs.getFloat(KEY_LAT, 40.7128f).toDouble()
        val lon = prefs.getFloat(KEY_LON, -74.0060f).toDouble()
        return Pair(lat, lon)
    }

    fun saveLocation(cityName: String, lat: Double, lon: Double) {
        prefs.edit()
            .putString(KEY_CITY_NAME, cityName)
            .putFloat(KEY_LAT, lat.toFloat())
            .putFloat(KEY_LON, lon.toFloat())
            .apply()
    }

    fun getUnits(): String {
        return prefs.getString(KEY_UNITS, "metric") ?: "metric"
    }

    fun saveUnits(units: String) {
        prefs.edit().putString(KEY_UNITS, units).apply()
    }
}
