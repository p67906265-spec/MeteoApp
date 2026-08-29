package com.p67906265.meteoapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                MeteoScreen(activity = this)
            }
        }
    }

    fun getLastKnownLocation(): Location? {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) return null
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        val providers = lm.getProviders(true)
        for (p in providers) {
            val loc = lm.getLastKnownLocation(p) ?: continue
            return loc
        }
        return null
    }
}

// ---------- Colori dinamici per categoria meteo ----------
data class WeatherPalette(val top: Color, val bottom: Color, val accent: Color)

fun paletteFor(category: WeatherApi.Category): WeatherPalette = when (category) {
    WeatherApi.Category.CLEAR -> WeatherPalette(Color(0xFF4FA8E0), Color(0xFF7FC4EE), Color(0xFFFFC94A))
    WeatherApi.Category.CLOUDY -> WeatherPalette(Color(0xFF6E7A99), Color(0xFF8E9AB8), Color(0xFFE0E4F0))
    WeatherApi.Category.RAIN -> WeatherPalette(Color(0xFF3A5A82), Color(0xFF5B7CA3), Color(0xFF6FA8DC))
    WeatherApi.Category.STORM -> WeatherPalette(Color(0xFF241B3A), Color(0xFF3E2E5C), Color(0xFFFFD25C))
    WeatherApi.Category.SNOW -> WeatherPalette(Color(0xFF7C93AD), Color(0xFFB8C9DA), Color(0xFFFFFFFF))
    WeatherApi.Category.FOG -> WeatherPalette(Color(0xFF6B6F7A), Color(0xFF8E9199), Color(0xFFD4D6DC))
}

@Composable
fun MeteoScreen(activity: MainActivity) {
    var weather by remember { mutableStateOf<WeatherData?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf(0) } // 0=Meteo 1=Radar
    var showSearch by remember { mutableStateOf(false) }

    var lat by remember { mutableStateOf(41.9028) }
    var lon by remember { mutableStateOf(12.4964) }
    var cityName by remember { mutableStateOf("Roma") }

    val scope = rememberCoroutineScope()

    fun loadWeather() {
        scope.launch {
            error = null
            try {
                val data = withContext(Dispatchers.IO) { WeatherApi.fetch(lat, lon, cityName) }
                weather = data
            } catch (e: Exception) {
                error = "Errore nel recupero dati: ${e.message}"
            }
        }
    }

    LaunchedEffect(Unit) {
        val loc = activity.getLastKnownLocation()
        if (loc != null) {
            lat = loc.latitude
            lon = loc.longitude
            cityName = "Posizione attuale"
        }
        loadWeather()
    }

    val category = weather?.let { WeatherApi.category(it.currentCode) } ?: WeatherApi.Category.CLEAR
    val palette = paletteFor(category)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(palette.top, palette.bottom)))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopBar(
                cityName = weather?.cityName ?: cityName,
                onSearchClick = { showSearch = true },
                onLocationClick = {
                    val loc = activity.getLastKnownLocation()
                    if (loc != null) {
                        lat = loc.latitude
                        lon = loc.longitude
                        cityName = "Posizione attuale"
                        loadWeather()
                    }
                }
            )

            Box(modifier = Modifier.weight(1f)) {
                when (tab) {
                    0 -> MeteoTab(weather, error, palette)
                    1 -> RadarTab(lat, lon)
                }
            }
            BottomBar(tab) { tab = it }
        }

        if (showSearch) {
            CitySearchOverlay(
                onDismiss = { showSearch = false },
                onCitySelected = { city ->
                    lat = city.lat
                    lon = city.lon
                    cityName = city.name
                    showSearch = false
                    loadWeather()
                }
            )
        }
    }
}

@Composable
fun TopBar(cityName: String, onSearchClick: () -> Unit, onLocationClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onLocationClick) {
                Icon(Icons.Filled.LocationOn, contentDescription = "Posizione", tint = Color.White)
            }
            Text(cityName, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        IconButton(onClick = onSearchClick) {
            Icon(Icons.Filled.Search, contentDescription = "Cerca città", tint = Color.White)
        }
    }
}

@Composable
fun CitySearchOverlay(onDismiss: () -> Unit, onCitySelected: (CityResult) -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<CityResult>>(emptyList()) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xEE0D0F1F))
            .padding(20.dp)
    ) {
        Column {
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    scope.launch {
                        if (it.length >= 2) {
                            results = withContext(Dispatchers.IO) {
                                try { WeatherApi.searchCity(it) } catch (e: Exception) { emptyList() }
                            }
                        } else {
                            results = emptyList()
                        }
                    }
                },
                label = { Text("Cerca una città") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Color(0xFF7A82AE),
                    focusedLabelColor = Color.White,
                    unfocusedLabelColor = Color(0xFF7A82AE)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            results.forEach { city ->
                Text(
                    text = "${city.name}${if (city.admin.isNotEmpty()) ", ${city.admin}" else ""} — ${city.country}",
                    color = Color.White,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1B1F3B))
                        .padding(12.dp)
                        .clickable_(onClick = { onCitySelected(city) })
                )
            }

            Spacer(Modifier.height(24.dp))
            TextButton(onClick = onDismiss) {
                Text("Chiudi", color = Color(0xFF7A82AE))
            }
        }
    }
}

// piccola estensione per evitare import ambiguo di clickable
fun Modifier.clickable_(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)

@Composable
fun MeteoTab(weather: WeatherData?, error: String?, palette: WeatherPalette) {
    var subTab by remember { mutableStateOf(0) } // 0 = oraria, 1 = settimanale

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when {
            error != null -> Text(error, color = Color(0xFFFF8A80), modifier = Modifier.padding(top = 40.dp))
            weather == null -> CircularProgressIndicator(color = Color.White, modifier = Modifier.padding(top = 40.dp))
            else -> {
                Spacer(Modifier.height(16.dp))
                PuffyIcon(weather.currentCode, palette)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${weather.currentTemp.toInt()}°",
                    fontSize = 84.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = WeatherApi.describe(weather.currentCode),
                    fontSize = 18.sp,
                    color = Color(0xFFEFF2FF)
                )
                Text(
                    text = "Percepiti ${weather.feelsLike.toInt()}°",
                    fontSize = 14.sp,
                    color = Color(0xFFC8CEE8)
                )

                Spacer(Modifier.height(20.dp))

                // Selettore Oraria / Settimanale
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0x33000000))
                        .padding(4.dp)
                ) {
                    SegmentButton("Oraria", subTab == 0) { subTab = 0 }
                    SegmentButton("Settimanale", subTab == 1) { subTab = 1 }
                }

                Spacer(Modifier.height(16.dp))

                if (subTab == 0) {
                    HourlyCard(weather.hourly)
                } else {
                    WeeklyCard(weather.daily)
                }

                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
fun SegmentButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Color.White.copy(alpha = 0.25f) else Color.Transparent)
            .clickable_(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
fun PuffyIcon(code: Int, palette: WeatherPalette) {
    val category = WeatherApi.category(code)
    Canvas(modifier = Modifier.size(130.dp)) {
        // bagliore soffuso
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(palette.accent.copy(alpha = 0.55f), palette.accent.copy(alpha = 0f)),
                radius = size.minDimension
            ),
            radius = size.minDimension / 1.5f,
            center = Offset(size.width / 2f, size.height / 2f)
        )
        when (category) {
            WeatherApi.Category.CLEAR -> {
                drawCircle(color = palette.accent, radius = size.minDimension / 3.4f, center = Offset(size.width / 2f, size.height / 2.3f))
            }
            else -> {
                // nuvola stilizzata: tre cerchi sovrapposti
                val cy = size.height / 1.8f
                drawCircle(color = Color(0xFFF0F2F8), radius = size.minDimension / 4.2f, center = Offset(size.width / 2.6f, cy))
                drawCircle(color = Color(0xFFF0F2F8), radius = size.minDimension / 3.4f, center = Offset(size.width / 1.9f, cy - size.height / 14f))
                drawCircle(color = Color(0xFFF0F2F8), radius = size.minDimension / 4.2f, center = Offset(size.width / 1.5f, cy))
            }
        }
    }
}

@Composable
fun HourlyCard(hourly: List<HourPoint>) {
    val next12 = hourly.take(12)
    if (next12.isEmpty()) return
    val maxTemp = next12.maxOf { it.temp }
    val minTemp = next12.minOf { it.temp }

    Card(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0x33101430))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Prossime 12 ore", color = Color(0xFFDCE0F5), fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))

            Canvas(modifier = Modifier.fillMaxWidth().height(90.dp)) {
                val stepX = size.width / (next12.size - 1).coerceAtLeast(1)
                val range = (maxTemp - minTemp).coerceAtLeast(1.0)
                val points = next12.mapIndexed { i, p ->
                    val x = i * stepX
                    val y = size.height - ((p.temp - minTemp) / range * size.height).toFloat()
                    Offset(x, y)
                }
                for (i in 0 until points.size - 1) {
                    drawLine(
                        color = Color.White,
                        start = points[i], end = points[i + 1],
                        strokeWidth = 6f,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }
                points.forEach { p -> drawCircle(color = Color.White, radius = 6f, center = p) }
            }

            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                next12.forEachIndexed { i, p ->
                    if (i % 3 == 0) {
                        Text(p.hour, color = Color(0xFFC8CEE8), fontSize = 11.sp, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun WeeklyCard(daily: List<DayPoint>) {
    Card(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0x33101430))
    ) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            daily.forEach { day ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(WeatherApi.dayLabel(day.date), color = Color.White, fontSize = 15.sp, modifier = Modifier.width(60.dp))
                    Text(WeatherApi.describe(day.code), color = Color(0xFFC8CEE8), fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text("${day.minTemp.toInt()}° / ${day.maxTemp.toInt()}°", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RadarTab(lat: Double, lon: Double) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                loadDataWithBaseURL("https://rainviewer.com", radarHtml(lat, lon), "text/html", "UTF-8", null)
            }
        }
    )
}

private fun radarHtml(lat: Double, lon: Double): String = """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
<style>
html,body,#map{height:100%;margin:0;background:#0D0F1F;}
#playbtn{position:absolute;bottom:16px;left:16px;z-index:999;background:#fff;border:none;border-radius:20px;padding:10px 16px;font-family:sans-serif;font-weight:bold;}
</style>
</head>
<body>
<div id="map"></div>
<button id="playbtn">⏸ Pausa</button>
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<script>
var map = L.map('map').setView([$lat, $lon], 7);
L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 18 }).addTo(map);

var radarLayer = null;
var frames = [];
var frameIndex = 0;
var playing = true;
var timer = null;

function showFrame(i) {
  if (radarLayer) map.removeLayer(radarLayer);
  var f = frames[i];
  radarLayer = L.tileLayer(f.host + f.path + '/256/{z}/{x}/{y}/2/1_1.png', { opacity: 0.75 });
  radarLayer.addTo(map);
}

function tick() {
  if (frames.length === 0) return;
  frameIndex = (frameIndex + 1) % frames.length;
  showFrame(frameIndex);
}

function startPlaying() {
  if (timer) clearInterval(timer);
  timer = setInterval(tick, 700);
}

fetch('https://api.rainviewer.com/public/weather-maps.json')
  .then(r => r.json())
  .then(data => {
    var past = data.radar.past.map(function(fr) { return { path: fr.path, host: data.host }; });
    var fut = (data.radar.nowcast || []).map(function(fr) { return { path: fr.path, host: data.host }; });
    frames = past.concat(fut);
    frameIndex = frames.length - 1;
    showFrame(frameIndex);
    startPlaying();
  })
  .catch(function(e) {
    document.title = 'radar-error';
  });

document.getElementById('playbtn').addEventListener('click', function() {
  playing = !playing;
  if (playing) {
    startPlaying();
    this.innerText = '⏸ Pausa';
  } else {
    clearInterval(timer);
    this.innerText = '▶ Play';
  }
});
</script>
</body>
</html>
"""

@Composable
fun BottomBar(selected: Int, onSelect: (Int) -> Unit) {
    NavigationBar(containerColor = Color(0xFF12142A)) {
        NavigationBarItem(selected = selected == 0, onClick = { onSelect(0) }, icon = {}, label = { Text("Meteo") })
        NavigationBarItem(selected = selected == 1, onClick = { onSelect(1) }, icon = {}, label = { Text("Radar") })
    }
}
