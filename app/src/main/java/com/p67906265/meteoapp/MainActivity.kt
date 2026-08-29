package com.p67906265.meteoapp

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                MeteoScreen()
            }
        }
    }
}

@Composable
fun MeteoScreen() {
    var weather by remember { mutableStateOf<WeatherData?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf(0) } // 0 = Meteo, 1 = Radar

    // Coordinate di default (Roma) - si può collegare al GPS in seguito
    val lat = 41.9028
    val lon = 12.4964

    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val data = withContext(Dispatchers.IO) { WeatherApi.fetch(lat, lon) }
                weather = data
            } catch (e: Exception) {
                error = "Errore nel recupero dati: ${e.message}"
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF1B1F3B), Color(0xFF0D0F1F))
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                when (tab) {
                    0 -> MeteoTab(weather, error)
                    1 -> RadarTab(lat, lon)
                }
            }
            BottomBar(tab) { tab = it }
        }
    }
}

@Composable
fun MeteoTab(weather: WeatherData?, error: String?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))

        when {
            error != null -> Text(error, color = Color(0xFFFF8A80))
            weather == null -> CircularProgressIndicator(color = Color.White)
            else -> {
                PuffyIcon(weather.currentCode)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${weather.currentTemp.toInt()}°",
                    fontSize = 96.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = WeatherApi.describe(weather.currentCode),
                    fontSize = 20.sp,
                    color = Color(0xFFB0B8E0)
                )

                Spacer(Modifier.height(32.dp))

                HourlyCard(weather.hourly)
            }
        }
    }
}

@Composable
fun PuffyIcon(code: Int) {
    // Icona "puffy" 3D-like: cerchio con bagliore soft, colore in base al meteo
    val color = when {
        code == 0 -> Color(0xFFFFC94A) // sereno
        code in 1..3 -> Color(0xFFB8C4E8) // nuvoloso
        code in 51..82 -> Color(0xFF6FA8DC) // pioggia
        code in 95..99 -> Color(0xFF8E7CC3) // temporale
        else -> Color(0xFFB8C4E8)
    }

    Canvas(modifier = Modifier.size(140.dp)) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = 0.9f), color.copy(alpha = 0.05f)),
                radius = size.minDimension
            ),
            radius = size.minDimension / 1.6f,
            center = Offset(size.width / 2f, size.height / 2f)
        )
        drawCircle(
            color = color,
            radius = size.minDimension / 3.2f,
            center = Offset(size.width / 2f, size.height / 2f)
        )
    }
}

@Composable
fun HourlyCard(hourly: List<HourPoint>) {
    val next12 = hourly.take(12)
    val maxTemp = next12.maxOf { it.temp }
    val minTemp = next12.minOf { it.temp }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF181C36))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Prossime 12 ore", color = Color(0xFFB0B8E0), fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
            ) {
                val stepX = size.width / (next12.size - 1).coerceAtLeast(1)
                val range = (maxTemp - minTemp).coerceAtLeast(1.0)

                val points = next12.mapIndexed { i, p ->
                    val x = i * stepX
                    val y = size.height - ((p.temp - minTemp) / range * size.height).toFloat()
                    Offset(x, y)
                }

                for (i in 0 until points.size - 1) {
                    drawLine(
                        color = Color(0xFF6FA8DC),
                        start = points[i],
                        end = points[i + 1],
                        strokeWidth = 6f,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }
                points.forEach { p ->
                    drawCircle(color = Color.White, radius = 6f, center = p)
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                next12.forEachIndexed { i, p ->
                    if (i % 3 == 0) {
                        Text(
                            text = p.hour,
                            color = Color(0xFF7A82AE),
                            fontSize = 11.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
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
                loadDataWithBaseURL(
                    "https://rainviewer.com",
                    radarHtml(lat, lon),
                    "text/html",
                    "UTF-8",
                    null
                )
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
<style>html,body,#map{height:100%;margin:0;background:#0D0F1F;}</style>
</head>
<body>
<div id="map"></div>
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<script>
var map = L.map('map').setView([$lat, $lon], 7);
L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
  maxZoom: 18
}).addTo(map);

fetch('https://api.rainviewer.com/public/weather-maps.json')
  .then(r => r.json())
  .then(data => {
    var frame = data.radar.past[data.radar.past.length - 1];
    var path = data.host + frame.path + '/256/{z}/{x}/{y}/2/1_1.png';
    L.tileLayer(path, { opacity: 0.7 }).addTo(map);
  });
</script>
</body>
</html>
"""

@Composable
fun BottomBar(selected: Int, onSelect: (Int) -> Unit) {
    NavigationBar(containerColor = Color(0xFF12142A)) {
        NavigationBarItem(
            selected = selected == 0,
            onClick = { onSelect(0) },
            icon = {},
            label = { Text("Meteo") }
        )
        NavigationBarItem(
            selected = selected == 1,
            onClick = { onSelect(1) },
            icon = {},
            label = { Text("Radar") }
        )
    }
}
