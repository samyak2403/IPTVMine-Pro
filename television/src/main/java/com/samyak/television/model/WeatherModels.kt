package com.samyak.television.model

import com.google.gson.annotations.SerializedName

data class WeatherResponse(
    val lat: Double,
    val lon: Double,
    val timezone: String,
    val current: CurrentWeather,
    val hourly: List<HourlyWeather>,
    val daily: List<DailyWeather>
)

data class CurrentWeather(
    val dt: Long,
    val temp: Double,
    @SerializedName("feels_like") val feelsLike: Double,
    val pressure: Int,
    val humidity: Int,
    val uvi: Double,
    val clouds: Int,
    val visibility: Int,
    @SerializedName("wind_speed") val windSpeed: Double,
    val weather: List<WeatherDescription>
)

data class HourlyWeather(
    val dt: Long,
    val temp: Double,
    @SerializedName("feels_like") val feelsLike: Double,
    val pressure: Int,
    val humidity: Int,
    val uvi: Double,
    val clouds: Int,
    val visibility: Int,
    @SerializedName("wind_speed") val windSpeed: Double,
    val weather: List<WeatherDescription>,
    val pop: Double
)

data class DailyWeather(
    val dt: Long,
    val temp: DailyTemp,
    val weather: List<WeatherDescription>,
    val pop: Double
)

data class DailyTemp(
    val day: Double,
    val min: Double,
    val max: Double,
    val night: Double,
    val eve: Double,
    val morn: Double
)

data class WeatherDescription(
    val id: Int,
    val main: String,
    val description: String,
    val icon: String
)

data class GeocodingResponse(
    val name: String,
    val lat: Double,
    val lon: Double,
    val country: String,
    val state: String? = null
)

data class CurrentWeather25(
    val dt: Long,
    val main: Main25,
    val wind: Wind25,
    val clouds: Clouds25,
    val visibility: Int,
    val weather: List<WeatherDescription>
)

data class Main25(
    val temp: Double,
    @SerializedName("feels_like") val feelsLike: Double,
    val temp_min: Double,
    val temp_max: Double,
    val pressure: Int,
    val humidity: Int
)

data class Wind25(
    val speed: Double,
    val deg: Double
)

data class Clouds25(
    val all: Int
)

data class Forecast25Response(
    val list: List<ForecastItem25>
)

data class ForecastItem25(
    val dt: Long,
    val main: Main25,
    val weather: List<WeatherDescription>,
    val clouds: Clouds25,
    val wind: Wind25,
    val visibility: Int,
    val pop: Double,
    @SerializedName("dt_txt") val dtTxt: String
)
