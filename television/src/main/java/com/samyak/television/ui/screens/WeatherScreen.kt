package com.samyak.television.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.samyak.television.data.WeatherState
import com.samyak.television.data.WeatherViewModel
import com.samyak.television.model.DailyWeather
import com.samyak.television.model.GeocodingResponse
import com.samyak.television.model.HourlyWeather
import com.samyak.television.model.WeatherResponse
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun WeatherScreen(viewModel: WeatherViewModel = viewModel()) {
    val focusManager = LocalFocusManager.current

    val weatherState by viewModel.weatherState.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val searchError by viewModel.searchError.collectAsState()
    val cityName by viewModel.cityName.collectAsState()
    val units by viewModel.units.collectAsState()

    var citySearchQuery by remember { mutableStateOf("") }
    val isDropdownVisible = isSearching || searchError != null || searchResults.isNotEmpty()

    // Close dropdown on back press
    BackHandler(enabled = isDropdownVisible) {
        viewModel.clearSearchResults()
        citySearchQuery = ""
        focusManager.clearFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── TOP HEADER ROW ────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Search input field
                OutlinedTextField(
                    value = citySearchQuery,
                    onValueChange = { citySearchQuery = it },
                    placeholder = {
                        Text("Search city...", color = Color.Gray.copy(alpha = 0.7f), fontSize = 13.sp)
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF26A69A),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedContainerColor = Color(0xFF0E1629).copy(alpha = 0.6f),
                        unfocusedContainerColor = Color(0xFF0E1629).copy(alpha = 0.3f),
                        cursorColor = Color(0xFF26A69A)
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        if (citySearchQuery.isNotBlank()) {
                            viewModel.searchCity(citySearchQuery.trim())
                            focusManager.clearFocus()
                        }
                    }),
                    modifier = Modifier
                        .width(280.dp)
                        .height(54.dp)
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                when (keyEvent.key) {
                                    Key.DirectionLeft -> { focusManager.moveFocus(FocusDirection.Left); true }
                                    Key.DirectionRight -> { focusManager.moveFocus(FocusDirection.Right); true }
                                    Key.DirectionUp -> { focusManager.moveFocus(FocusDirection.Up); true }
                                    Key.DirectionDown -> { focusManager.moveFocus(FocusDirection.Down); true }
                                    else -> false
                                }
                            } else false
                        }
                )

                // Search button
                Button(
                    onClick = {
                        if (citySearchQuery.isNotBlank()) {
                            viewModel.searchCity(citySearchQuery.trim())
                            focusManager.clearFocus()
                        }
                    },
                    enabled = citySearchQuery.isNotBlank(),
                    colors = ButtonDefaults.colors(
                        containerColor = Color(0xFF26A69A),
                        focusedContainerColor = Color(0xFF1DB8AC),
                        disabledContainerColor = Color.Gray.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier.height(44.dp)
                ) {
                    Text("Search", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.weight(1f))

                // Unit toggle button
                Button(
                    onClick = { viewModel.toggleUnits() },
                    colors = ButtonDefaults.colors(
                        containerColor = Color(0xFF1E293B).copy(alpha = 0.8f),
                        focusedContainerColor = Color(0xFF26A69A)
                    ),
                    modifier = Modifier.height(44.dp)
                ) {
                    Text(
                        text = if (units == "metric") "°C / m/s" else "°F / mph",
                        fontSize = 13.sp,
                        color = Color.White
                    )
                }
            }

            // ── MAIN CONTENT AREA ─────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (val state = weatherState) {
                    is WeatherState.Idle -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "☁  Weather Cast",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Search for a city above to get started",
                                fontSize = 14.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 32.dp)
                            )
                        }
                    }

                    is WeatherState.Loading -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFF26A69A),
                                modifier = Modifier.size(40.dp),
                                strokeWidth = 3.dp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Fetching weather...", color = Color.Gray, fontSize = 13.sp)
                        }
                    }

                    is WeatherState.Error -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "⚠  Weather Failed",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEF5350)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = state.message,
                                fontSize = 12.sp,
                                color = Color.LightGray,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = { viewModel.fetchWeather() },
                                colors = ButtonDefaults.colors(
                                    containerColor = Color(0xFF26A69A),
                                    focusedContainerColor = Color(0xFF1DB8AC)
                                )
                            ) {
                                Text("Retry", fontSize = 13.sp)
                            }
                        }
                    }

                    is WeatherState.Success -> {
                        WeatherDetailsContent(
                            cityName = cityName,
                            weather = state.weather,
                            units = units
                        )
                    }
                }
            }
        }

        // ── FLOATING SEARCH DROPDOWN ──────────────────────────────────────────
        if (isDropdownVisible) {
            Box(
                modifier = Modifier
                    .padding(start = 0.dp, top = 58.dp)
                    .width(320.dp)
                    .heightIn(max = 280.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0D1B2A).copy(alpha = 0.98f))
                    .border(1.dp, Color(0xFF26A69A).copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                    .align(Alignment.TopStart)
            ) {
                when {
                    isSearching -> {
                        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                color = Color(0xFF26A69A),
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                    searchError != null -> {
                        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = searchError ?: "",
                                color = Color(0xFFEF5350),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    else -> {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            contentPadding = PaddingValues(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(searchResults) { result ->
                                GeocodingResultItem(result) {
                                    viewModel.selectCity(result)
                                    citySearchQuery = ""
                                    focusManager.clearFocus()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── SEARCH RESULT ITEM ────────────────────────────────────────────────────────
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun GeocodingResultItem(result: GeocodingResponse, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF1E3A5F).copy(alpha = 0.6f),
            focusedContainerColor = Color(0xFF26A69A),
            pressedContainerColor = Color(0xFF1DB8AC)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOfNotNull(result.state, result.country).joinToString(", "),
                    fontSize = 11.sp,
                    color = Color.LightGray.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "→",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.5f)
            )
        }
    }
}

// ── WEATHER DETAILS CONTENT ───────────────────────────────────────────────────
@Composable
fun WeatherDetailsContent(
    cityName: String,
    weather: WeatherResponse,
    units: String
) {
    val current = weather.current
    val speedUnit = if (units == "metric") "m/s" else "mph"
    val tempUnit = if (units == "metric") "°C" else "°F"

    // Single scrollable column – all sections are individual top-level items
    // so LazyRow (hourly) can coexist without scroll conflicts
    val columnState = rememberLazyListState()

    LazyColumn(
        state = columnState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {

        // ── SECTION: City header + timestamp ──────────────────────────────
        item(key = "header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = cityName,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Weather Cast",
                        fontSize = 12.sp,
                        color = Color(0xFF26A69A),
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.2.sp
                    )
                }
                val timeString = remember(current.dt) {
                    val date = Date(current.dt * 1000)
                    SimpleDateFormat("hh:mm a", Locale.getDefault()).format(date)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Updated",
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                    Text(
                        text = timeString,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.LightGray
                    )
                }
            }
        }

        // ── SECTION: Current Weather Card ─────────────────────────────────
        item(key = "current_card") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF0D2137).copy(alpha = 0.9f),
                                Color(0xFF1A3A5C).copy(alpha = 0.7f)
                            )
                        )
                    )
                    .border(1.dp, Color(0xFF26A69A).copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Temperature + condition
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${current.temp.toInt()}$tempUnit",
                                fontSize = 72.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                lineHeight = 76.sp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            val mainDesc = current.weather.firstOrNull()
                            if (mainDesc != null) {
                                AsyncImage(
                                    model = "https://openweathermap.org/img/wn/${mainDesc.icon}@4x.png",
                                    contentDescription = mainDesc.description,
                                    modifier = Modifier.size(80.dp),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = current.weather.firstOrNull()?.description
                                ?.replaceFirstChar { it.uppercase() } ?: "",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                        val todayForecast = weather.daily.firstOrNull()
                        if (todayForecast != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "H: ${todayForecast.temp.max.toInt()}$tempUnit  •  L: ${todayForecast.temp.min.toInt()}$tempUnit",
                                fontSize = 12.sp,
                                color = Color.LightGray.copy(alpha = 0.8f)
                            )
                        }
                    }

                    // Right: Stats grid
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            WeatherStatTile(
                                label = "Feels Like",
                                value = "${current.feelsLike.toInt()}$tempUnit",
                                icon = "🌡"
                            )
                            WeatherStatTile(
                                label = "Wind",
                                value = "${current.windSpeed.toInt()} $speedUnit",
                                icon = "💨"
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            WeatherStatTile(
                                label = "Humidity",
                                value = "${current.humidity}%",
                                icon = "💧"
                            )
                            WeatherStatTile(
                                label = "UV Index",
                                value = "${current.uvi.toInt()}",
                                icon = "☀"
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            WeatherStatTile(
                                label = "Pressure",
                                value = "${current.pressure} hPa",
                                icon = "📊"
                            )
                            WeatherStatTile(
                                label = "Clouds",
                                value = "${current.clouds}%",
                                icon = "☁"
                            )
                        }
                    }
                }
            }
        }

        // ── SECTION: Hourly Forecast Header ───────────────────────────────
        item(key = "hourly_header") {
            WeatherSectionHeader(title = "Hourly Forecast", subtitle = "Next 24 hours")
        }

        // ── SECTION: Hourly Forecast Row ──────────────────────────────────
        // Kept as a SEPARATE top-level item to avoid nested scroll conflicts
        item(key = "hourly_row") {
            val hourlyState = rememberLazyListState()
            LazyRow(
                state = hourlyState,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val next24 = weather.hourly.take(8) // 8 × 3hr = 24h from 2.5 API
                items(next24, key = { it.dt }) { hourData ->
                    HourlyForecastCard(hourData = hourData, tempUnit = tempUnit)
                }
            }
        }

        // ── SECTION: Daily Forecast Header ────────────────────────────────
        item(key = "daily_header") {
            WeatherSectionHeader(title = "5-Day Forecast", subtitle = "Daily overview")
        }

        // ── SECTION: Each daily row as its own top-level item ─────────────
        // This avoids Column-inside-LazyColumn nesting and enables proper TV focus
        val dailyItems = weather.daily.take(5)
        items(dailyItems, key = { "daily_${it.dt}" }) { dailyData ->
            DailyForecastRow(dailyData = dailyData, tempUnit = tempUnit)
        }

        // Bottom spacer
        item(key = "bottom_space") {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

// ── SECTION HEADER ────────────────────────────────────────────────────────────
@Composable
fun WeatherSectionHeader(title: String, subtitle: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(18.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF26A69A))
            )
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = Color.Gray,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        HorizontalDivider(
            color = Color.White.copy(alpha = 0.07f),
            thickness = 1.dp
        )
    }
}

// ── WEATHER STAT TILE ─────────────────────────────────────────────────────────
@Composable
fun WeatherStatTile(label: String, value: String, icon: String) {
    Column(modifier = Modifier.width(96.dp)) {
        Text(
            text = "$icon  $label",
            fontSize = 9.sp,
            color = Color.Gray,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 14.sp,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ── HOURLY FORECAST CARD ──────────────────────────────────────────────────────
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HourlyForecastCard(hourData: HourlyWeather, tempUnit: String) {
    val timeLabel = remember(hourData.dt) {
        val date = Date(hourData.dt * 1000)
        SimpleDateFormat("h a", Locale.getDefault()).format(date)
    }

    Surface(
        onClick = {},
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF0E1A2D).copy(alpha = 0.7f),
            focusedContainerColor = Color(0xFF26A69A),
            pressedContainerColor = Color(0xFF1DB8AC)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        modifier = Modifier
            .width(88.dp)
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(14.dp))
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 6.dp)
        ) {
            Text(
                text = timeLabel,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.LightGray
            )

            Spacer(modifier = Modifier.height(6.dp))

            val iconUrl = hourData.weather.firstOrNull()?.icon
            if (iconUrl != null) {
                AsyncImage(
                    model = "https://openweathermap.org/img/wn/$iconUrl@2x.png",
                    contentDescription = hourData.weather.firstOrNull()?.description,
                    modifier = Modifier.size(40.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Spacer(modifier = Modifier.size(40.dp))
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "${hourData.temp.toInt()}$tempUnit",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            // Precipitation chance
            if (hourData.pop > 0.1) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${(hourData.pop * 100).toInt()}%",
                    fontSize = 10.sp,
                    color = Color(0xFF64B5F6),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// ── DAILY FORECAST ROW ────────────────────────────────────────────────────────
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DailyForecastRow(dailyData: DailyWeather, tempUnit: String) {
    val dayName = remember(dailyData.dt) {
        val date = Date(dailyData.dt * 1000)
        val today = Calendar.getInstance()
        val check = Calendar.getInstance().apply { time = date }
        if (today.get(Calendar.DAY_OF_YEAR) == check.get(Calendar.DAY_OF_YEAR) &&
            today.get(Calendar.YEAR) == check.get(Calendar.YEAR)
        ) "Today"
        else SimpleDateFormat("EEEE", Locale.getDefault()).format(date)
    }

    val dateLabel = remember(dailyData.dt) {
        val date = Date(dailyData.dt * 1000)
        SimpleDateFormat("MMM d", Locale.getDefault()).format(date)
    }

    Surface(
        onClick = {},
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF0E1A2D).copy(alpha = 0.5f),
            focusedContainerColor = Color(0xFF1A3A5C).copy(alpha = 0.9f),
            pressedContainerColor = Color(0xFF26A69A).copy(alpha = 0.4f)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Day name + date
            Column(modifier = Modifier.width(110.dp)) {
                Text(
                    text = dayName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1
                )
                Text(
                    text = dateLabel,
                    fontSize = 10.sp,
                    color = Color.Gray,
                    maxLines = 1
                )
            }

            // Weather icon
            val weatherInfo = dailyData.weather.firstOrNull()
            if (weatherInfo != null) {
                AsyncImage(
                    model = "https://openweathermap.org/img/wn/${weatherInfo.icon}@2x.png",
                    contentDescription = weatherInfo.description,
                    modifier = Modifier.size(38.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Spacer(modifier = Modifier.size(38.dp))
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Description
            Text(
                text = weatherInfo?.description?.replaceFirstChar { it.uppercase() } ?: "",
                fontSize = 12.sp,
                color = Color.LightGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // Precipitation
            if (dailyData.pop > 0.05) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = "💧",
                        fontSize = 11.sp
                    )
                    Text(
                        text = " ${(dailyData.pop * 100).toInt()}%",
                        fontSize = 11.sp,
                        color = Color(0xFF64B5F6),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Max temp
            Text(
                text = "${dailyData.temp.max.toInt()}°",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.width(38.dp),
                textAlign = TextAlign.End
            )

            Spacer(modifier = Modifier.width(6.dp))

            // Min temp
            Text(
                text = "${dailyData.temp.min.toInt()}°",
                fontSize = 13.sp,
                color = Color.Gray,
                modifier = Modifier.width(38.dp),
                textAlign = TextAlign.End
            )
        }
    }
}
