package com.samyak.television.data

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.samyak.television.model.GeocodingResponse
import com.samyak.television.model.WeatherResponse
import com.samyak.television.model.CurrentWeather
import com.samyak.television.model.HourlyWeather
import com.samyak.television.model.DailyWeather
import com.samyak.television.model.DailyTemp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

sealed class WeatherState {
    object Idle : WeatherState()
    object Loading : WeatherState()
    data class Success(val weather: WeatherResponse) : WeatherState()
    data class Error(val message: String) : WeatherState()
}

class WeatherViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "WeatherViewModel"
    private val repository = WeatherRepository(application)
    private val client = OkHttpClient()
    private val gson = Gson()

    private val _weatherState = MutableStateFlow<WeatherState>(WeatherState.Idle)
    val weatherState: StateFlow<WeatherState> = _weatherState.asStateFlow()

    private val _searchResults = MutableStateFlow<List<GeocodingResponse>>(emptyList())
    val searchResults: StateFlow<List<GeocodingResponse>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()

    private val _apiKey = MutableStateFlow(repository.getApiKey())
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _cityName = MutableStateFlow(repository.getCityName())
    val cityName: StateFlow<String> = _cityName.asStateFlow()

    private val _units = MutableStateFlow(repository.getUnits())
    val units: StateFlow<String> = _units.asStateFlow()

    init {
        // Auto-fetch weather on start if API Key is present
        if (_apiKey.value.isNotBlank()) {
            fetchWeather()
        }
    }

    fun updateApiKey(newKey: String) {
        repository.saveApiKey(newKey)
        _apiKey.value = newKey
        if (newKey.isNotBlank()) {
            fetchWeather()
        } else {
            _weatherState.value = WeatherState.Idle
        }
    }

    fun toggleUnits() {
        val newUnit = if (_units.value == "metric") "imperial" else "metric"
        repository.saveUnits(newUnit)
        _units.value = newUnit
        if (_apiKey.value.isNotBlank()) {
            fetchWeather()
        }
    }

    fun selectCity(geocoding: GeocodingResponse) {
        val displayCity = "${geocoding.name}, ${geocoding.country}"
        repository.saveLocation(displayCity, geocoding.lat, geocoding.lon)
        _cityName.value = displayCity
        _searchResults.value = emptyList()
        fetchWeather()
    }

    fun clearSearchResults() {
        _searchResults.value = emptyList()
        _searchError.value = null
    }

    fun searchCity(query: String) {
        val key = _apiKey.value
        if (key.isBlank()) {
            _searchError.value = "Please configure an API Key first"
            return
        }
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }

        _isSearching.value = true
        _searchError.value = null

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = "https://api.openweathermap.org/geo/1.0/direct?q=${java.net.URLEncoder.encode(query, "UTF-8")}&limit=5&appid=$key"
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string() ?: ""
                        Log.e(TAG, "Search city failed: $errorBody")
                        withContext(Dispatchers.Main) {
                            _searchError.value = "Search failed: Code ${response.code}"
                            _searchResults.value = emptyList()
                        }
                    } else {
                        val json = response.body?.string() ?: "[]"
                        val type = object : TypeToken<List<GeocodingResponse>>() {}.type
                        val list: List<GeocodingResponse> = gson.fromJson(json, type)
                        withContext(Dispatchers.Main) {
                            _searchResults.value = list
                            if (list.isEmpty()) {
                                _searchError.value = "No cities found"
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Search city exception", e)
                withContext(Dispatchers.Main) {
                    _searchError.value = "Error: ${e.localizedMessage ?: "Unknown connection error"}"
                }
            } finally {
                withContext(Dispatchers.Main) {
                    _isSearching.value = false
                }
            }
        }
    }

    fun fetchWeather() {
        val key = _apiKey.value
        if (key.isBlank()) {
            _weatherState.value = WeatherState.Error("API Key is missing. Please configure it.")
            return
        }

        _weatherState.value = WeatherState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            val coords = repository.getCoordinates()
            val unitStr = _units.value
            try {
                // Fetch Current Weather (2.5) - read body inside use{} to prevent closed-stream issues
                val urlWeather = "https://api.openweathermap.org/data/2.5/weather?lat=${coords.first}&lon=${coords.second}&units=$unitStr&appid=$key"
                // Fetch 5-day / 3-hour Forecast (2.5)
                val urlForecast = "https://api.openweathermap.org/data/2.5/forecast?lat=${coords.first}&lon=${coords.second}&units=$unitStr&appid=$key"

                val requestWeather = Request.Builder().url(urlWeather).build()
                val requestForecast = Request.Builder().url(urlForecast).build()

                // Read bodies inside use{} so the response stream stays open
                data class ApiResult(val code: Int, val isSuccessful: Boolean, val body: String?)

                val deferredWeather = async {
                    client.newCall(requestWeather).execute().use { res ->
                        ApiResult(res.code, res.isSuccessful, res.body?.string())
                    }
                }
                val deferredForecast = async {
                    client.newCall(requestForecast).execute().use { res ->
                        ApiResult(res.code, res.isSuccessful, res.body?.string())
                    }
                }

                val resWeather = deferredWeather.await()
                val resForecast = deferredForecast.await()

                if (!resWeather.isSuccessful || !resForecast.isSuccessful) {
                    val codeW = resWeather.code
                    val codeF = resForecast.code
                    val errW = if (!resWeather.isSuccessful) resWeather.body ?: "" else ""
                    val errF = if (!resForecast.isSuccessful) resForecast.body ?: "" else ""
                    Log.e(TAG, "Fetch weather 2.5 failed: W=$codeW ($errW), F=$codeF ($errF)")

                    withContext(Dispatchers.Main) {
                        _weatherState.value = WeatherState.Error(
                            "API Error: Current=$codeW, Forecast=$codeF\n${if (errW.isNotBlank()) errW else errF}"
                        )
                    }
                } else {
                    val jsonWeather = resWeather.body
                    val jsonForecast = resForecast.body

                    if (jsonWeather.isNullOrBlank() || jsonForecast.isNullOrBlank()) {
                        Log.e(TAG, "Empty response body: weather=${jsonWeather?.length}, forecast=${jsonForecast?.length}")
                        withContext(Dispatchers.Main) {
                            _weatherState.value = WeatherState.Error("Empty response from weather server. Please try again.")
                        }
                        return@launch
                    }

                    val currentData = gson.fromJson(jsonWeather, com.samyak.television.model.CurrentWeather25::class.java)
                    val forecastData = gson.fromJson(jsonForecast, com.samyak.television.model.Forecast25Response::class.java)

                    val mappedResponse = combineToWeatherResponse(currentData, forecastData, coords.first, coords.second)

                    withContext(Dispatchers.Main) {
                        _weatherState.value = WeatherState.Success(mappedResponse)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fetch weather exception", e)
                withContext(Dispatchers.Main) {
                    _weatherState.value = WeatherState.Error("Connection Error: ${e.localizedMessage ?: "Failed to connect to weather server"}")
                }
            }
        }
    }

    private fun combineToWeatherResponse(
        currentRes: com.samyak.television.model.CurrentWeather25,
        forecastRes: com.samyak.television.model.Forecast25Response,
        lat: Double,
        lon: Double
    ): WeatherResponse {
        val currentWeather = CurrentWeather(
            dt = currentRes.dt,
            temp = currentRes.main.temp,
            feelsLike = currentRes.main.feelsLike,
            pressure = currentRes.main.pressure,
            humidity = currentRes.main.humidity,
            uvi = 0.0,
            clouds = currentRes.clouds.all,
            visibility = currentRes.visibility,
            windSpeed = currentRes.wind.speed,
            weather = currentRes.weather
        )

        val hourlyList = forecastRes.list.map { item ->
            HourlyWeather(
                dt = item.dt,
                temp = item.main.temp,
                feelsLike = item.main.feelsLike,
                pressure = item.main.pressure,
                humidity = item.main.humidity,
                uvi = 0.0,
                clouds = item.clouds.all,
                visibility = item.visibility,
                windSpeed = item.wind.speed,
                weather = item.weather,
                pop = item.pop
            )
        }

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")

        val groupedByDay = forecastRes.list.groupBy { item ->
            sdf.format(Date(item.dt * 1000))
        }

        val dailyList = groupedByDay.map { (_, items) ->
            val minTemp = items.minOf { it.main.temp_min }
            val maxTemp = items.maxOf { it.main.temp_max }

            val midItem = items.minByOrNull { item ->
                val cal = Calendar.getInstance().apply { time = Date(item.dt * 1000) }
                val hour = cal.get(Calendar.HOUR_OF_DAY)
                Math.abs(hour - 12)
            } ?: items[items.size / 2]

            val maxPop = items.maxOf { it.pop }

            DailyWeather(
                dt = midItem.dt,
                temp = DailyTemp(
                    day = midItem.main.temp,
                    min = minTemp,
                    max = maxTemp,
                    night = items.last().main.temp,
                    eve = midItem.main.temp,
                    morn = items.first().main.temp
                ),
                weather = midItem.weather,
                pop = maxPop
            )
        }.sortedBy { it.dt }

        return WeatherResponse(
            lat = lat,
            lon = lon,
            timezone = "UTC",
            current = currentWeather,
            hourly = hourlyList,
            daily = dailyList
        )
    }
}
