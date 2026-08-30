package com.p67906265.meteoapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {

    var permissionGrantedTick by mutableStateOf(0)
        private set

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) permissionGrantedTick++
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
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

data class WeatherPalette(val top: Color, val bottom: Color, val cardTop: Color, val cardBottom: Color, val accent: Color)

fun paletteFor(category: WeatherApi.Category): WeatherPalette = when (category) {
    WeatherApi.Category.CLEAR -> WeatherPalette(Color(0xFFEFF3FF), Color(0xFFE3E8FB), Color(0xFF7B90E8), Color(0xFFA98CE0), Color(0xFFFFB84A))
    WeatherApi.Category.CLOUDY -> WeatherPalette(Color(0xFFEEF0F4), Color(0xFFDDE1EA), Color(0xFF8891A6), Color(0xFFAAB2C4), Color(0xFFE7EAF0))
    WeatherApi.Category.RAIN -> WeatherPalette(Color(0xFFE7EEF6), Color(0xFFD3DEEA), Color(0xFF4C6A93), Color(0xFF6E88AE), Color(0xFF6FA8DC))
    WeatherApi.Category.STORM -> WeatherPalette(Color(0xFFE7E4F2), Color(0xFFD3CDE8), Color(0xFF423263), Color(0xFF5E4A85), Color(0xFFFFD25C))
    WeatherApi.Category.SNOW -> WeatherPalette(Color(0xFFF0F3F7), Color(0xFFE1E7ED), Color(0xFF8FA3B8), Color(0xFFB6C6D6), Color(0xFFFFFFFF))
    WeatherApi.Category.FOG -> WeatherPalette(Color(0xFFEDEDEF), Color(0xFFDBDCE0), Color(0xFF7A7E88), Color(0xFF9C9FA8), Color(0xFFD4D6DC))
}

@Composable
fun MeteoScreen(activity: MainActivity) {
    var weather by remember { mutableStateOf<WeatherData?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf(0) }
    var showSearch by remember { mutableStateOf(false) }
    var showAllHours by remember { mutableStateOf(false) }

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

    // Se il permesso viene concesso dopo l'avvio (es. prima installazione),
    // riprova a leggere la posizione e ricarica il meteo.
    LaunchedEffect(activity.permissionGrantedTick) {
        if (activity.permissionGrantedTick > 0) {
            val loc = activity.getLastKnownLocation()
            if (loc != null) {
                lat = loc.latitude
                lon = loc.longitude
                cityName = "Posizione attuale"
                loadWeather()
            }
        }
    }

    val category = weather?.let { WeatherApi.category(it.currentCode) } ?: WeatherApi.Category.CLEAR
    val palette = paletteFor(category)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(palette.top, palette.bottom)))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (tab == 0) {
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
            }

            Box(modifier = Modifier.weight(1f)) {
                when (tab) {
                    0 -> MeteoTab(weather, error, palette, showAllHours, onToggleAllHours = { showAllHours = !showAllHours })
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Meteo", color = Color(0xFF23262F), fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onLocationClick) {
                Icon(Icons.Filled.LocationOn, contentDescription = "Posizione", tint = Color(0xFF6C5CE7))
            }
            IconButton(onClick = onSearchClick) {
                Icon(Icons.Filled.Search, contentDescription = "Cerca città", tint = Color(0xFF6C5CE7))
            }
        }
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
fun CitySearchOverlay(onDismiss: () -> Unit, onCitySelected: (CityResult) -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<CityResult>>(emptyList()) }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        // scrim trasparente: tocco fuori chiude la tendina, senza scurire tutto lo schermo
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onDismiss() }
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 84.dp, start = 20.dp, end = 20.dp)
                .zIndex(10f)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    scope.launch {
                        if (it.length >= 2) {
                            results = withContext(Dispatchers.IO) {
                                try { WeatherApi.searchCity(it) } catch (e: Exception) { emptyList() }
                            }
                        } else results = emptyList()
                    }
                },
                label = { Text("Cerca una città") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color(0xFF23262F),
                    unfocusedTextColor = Color(0xFF23262F),
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = Color(0xFF6C5CE7),
                    unfocusedBorderColor = Color(0xFFDADEEA),
                    focusedLabelColor = Color(0xFF6C5CE7),
                    unfocusedLabelColor = Color(0xFF8A8F9C)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = 6.dp, shape = RoundedCornerShape(16.dp))
            )

            AnimatedVisibility(
                visible = results.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .shadow(elevation = 14.dp, shape = RoundedCornerShape(18.dp)),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column {
                        results.forEachIndexed { i, city ->
                            CityResultRow(city = city, onClick = { onCitySelected(city) })
                            if (i != results.lastIndex) {
                                HorizontalDivider(color = Color(0xFFEFF0F5), thickness = 1.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CityResultRow(city: CityResult, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0xFF6C5CE7).copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.LocationOn,
                contentDescription = null,
                tint = Color(0xFF6C5CE7),
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(city.name, color = Color(0xFF23262F), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            val subtitle = listOf(city.admin, city.country).filter { it.isNotEmpty() }.joinToString(", ")
            if (subtitle.isNotEmpty()) {
                Text(subtitle, color = Color(0xFF8A8F9C), fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun MeteoTab(weather: WeatherData?, error: String?, palette: WeatherPalette, showAllHours: Boolean, onToggleAllHours: () -> Unit) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp).verticalScroll(scrollState),
    ) {
        when {
            error != null -> Text(error, color = Color(0xFFB00020), modifier = Modifier.padding(top = 40.dp))
            weather == null -> Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF6C5CE7))
            }
            else -> {
                MainCard(weather, palette)
                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Prossime ore", color = Color(0xFF23262F), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (showAllHours) "Mostra meno" else "Vedi tutte",
                        color = Color(0xFF6C5CE7),
                        fontSize = 14.sp,
                        modifier = Modifier.clickable { onToggleAllHours() }
                    )
                }
                Spacer(Modifier.height(12.dp))
                HourlyRow(weather.hourly, if (showAllHours) 24 else 8)

                Spacer(Modifier.height(28.dp))
                Text("Prossimi giorni", color = Color(0xFF23262F), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                DailyList(weather.daily)

                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
fun MainCard(weather: WeatherData, palette: WeatherPalette) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 18.dp, shape = RoundedCornerShape(28.dp), clip = false)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(palette.cardTop, palette.cardBottom)))
            .padding(24.dp)
    ) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(weather.cityName, color = Color(0xFFEFF0FF), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text("${weather.currentTemp.toInt()}°", color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.Bold)
                    Text(WeatherApi.describe(weather.currentCode), color = Color(0xFFEFF0FF), fontSize = 17.sp)
                }
                WeatherGlyph(weather.currentCode, palette)
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "💨 Vento ${weather.windSpeed.toInt()} km/h  •  ${WeatherApi.windDirLabel(weather.windDirection)}",
                color = Color(0xFFEFF0FF), fontSize = 13.sp
            )

            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatBox("Percepita", "${weather.feelsLike.toInt()}°", Modifier.weight(1f))
                StatBox("Umidità", "${weather.humidity}%", Modifier.weight(1f))
                StatBox("Pressione", "${weather.pressure.toInt()} hPa", Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun StatBox(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x33FFFFFF))
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = Color(0xFFEFF0FF), fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun WeatherGlyph(code: Int, palette: WeatherPalette, size: androidx.compose.ui.unit.Dp = 76.dp) {
    val category = WeatherApi.category(code)
    Canvas(modifier = Modifier.size(size)) {
        val w = size.toPx()
        val cx = w / 2f
        val cy = w / 2f
        when (category) {
            WeatherApi.Category.CLEAR -> {
                drawPuffySun(cx, cy, w, withFace = true)
            }
            WeatherApi.Category.CLOUDY, WeatherApi.Category.FOG -> {
                drawPuffyCloud(cx, cy, w)
            }
            WeatherApi.Category.RAIN -> {
                drawPuffySun(cx - w * 0.14f, cy - w * 0.12f, w * 0.62f, withFace = false)
                drawPuffyCloud(cx + w * 0.02f, cy + w * 0.02f, w * 0.92f)
                val drop = Brush.verticalGradient(listOf(Color(0xFF9FCCF2), Color(0xFF4E86C4)))
                for (i in -1..1) {
                    val bx = cx + i * w * 0.17f
                    val by = cy + w * 0.28f
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(bx, by)
                        cubicTo(bx - w * 0.05f, by + w * 0.12f, bx - w * 0.035f, by + w * 0.2f, bx, by + w * 0.22f)
                        cubicTo(bx + w * 0.035f, by + w * 0.2f, bx + w * 0.05f, by + w * 0.12f, bx, by)
                        close()
                    }
                    drawPath(path, drop)
                    drawCircle(Color.White.copy(alpha = 0.5f), radius = w * 0.01f, center = Offset(bx - w * 0.012f, by + w * 0.08f))
                }
            }
            WeatherApi.Category.STORM -> {
                drawPuffyCloud(cx, cy - w * 0.04f, w, tint = Color(0xFFCBC2EA), shadowTint = Color(0xFF6E63A8))
                val boltColor = Brush.verticalGradient(listOf(Color(0xFFFFE58A), Color(0xFFFFB84A)))
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(cx + w * 0.04f, cy + w * 0.10f)
                    lineTo(cx - w * 0.07f, cy + w * 0.32f)
                    lineTo(cx + w * 0.01f, cy + w * 0.26f)
                    lineTo(cx - w * 0.03f, cy + w * 0.46f)
                    lineTo(cx + w * 0.15f, cy + w * 0.20f)
                    lineTo(cx + w * 0.03f, cy + w * 0.24f)
                    close()
                }
                drawPath(path, boltColor)
            }
            WeatherApi.Category.SNOW -> {
                drawPuffyCloud(cx, cy - w * 0.04f, w)
                for (i in -1..1) {
                    drawSnowflake(cx + i * w * 0.17f, cy + w * 0.28f, w * 0.045f)
                }
            }
        }
    }
}

/** Sole in stile "3D puffy": sfera lucida con luce direzionale, alone morbido e faccina sorridente opzionale. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPuffySun(cx: Float, cy: Float, w: Float, withFace: Boolean) {
    // alone soffuso
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFFFFD98A).copy(alpha = 0.5f), Color.Transparent),
            center = Offset(cx, cy),
            radius = w * 0.58f
        ),
        radius = w * 0.52f,
        center = Offset(cx, cy)
    )
    // raggi tozzi e arrotondati
    for (i in 0 until 8) {
        val angle = Math.toRadians((i * 45).toDouble())
        val x1 = cx + (w * 0.30f) * cos(angle).toFloat()
        val y1 = cy + (w * 0.30f) * sin(angle).toFloat()
        val x2 = cx + (w * 0.40f) * cos(angle).toFloat()
        val y2 = cy + (w * 0.40f) * sin(angle).toFloat()
        drawLine(Color(0xFFFFB84A), Offset(x1, y1), Offset(x2, y2), strokeWidth = w * 0.06f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    }
    // sfera solare lucida (gradiente diagonale che simula luce dall'alto a sinistra)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFFFFF0C2), Color(0xFFFFCB5C), Color(0xFFF2A93B)),
            center = Offset(cx - w * 0.08f, cy - w * 0.08f),
            radius = w * 0.34f
        ),
        radius = w * 0.28f,
        center = Offset(cx, cy)
    )
    // riflesso lucido
    drawCircle(color = Color.White.copy(alpha = 0.6f), radius = w * 0.07f, center = Offset(cx - w * 0.1f, cy - w * 0.1f))

    if (withFace) {
        val eyeColor = Color(0xFF8A5A1E)
        drawCircle(eyeColor, radius = w * 0.018f, center = Offset(cx - w * 0.07f, cy - w * 0.01f))
        drawCircle(eyeColor, radius = w * 0.018f, center = Offset(cx + w * 0.07f, cy - w * 0.01f))
        val smile = androidx.compose.ui.graphics.Path().apply {
            moveTo(cx - w * 0.06f, cy + w * 0.06f)
            quadraticTo(cx, cy + w * 0.11f, cx + w * 0.06f, cy + w * 0.06f)
        }
        drawPath(smile, color = eyeColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.015f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
    }
}

/** Nuvola in stile "3D puffy": lobi con luce direzionale e ombra proiettata morbida sotto. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPuffyCloud(
    cx: Float, cy: Float, w: Float, tint: Color = Color(0xFFFFFFFF), shadowTint: Color = Color(0xFFB9C0D4)
) {
    // ombra proiettata morbida sotto la nuvola
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFF6B7080).copy(alpha = 0.16f), Color.Transparent),
            center = Offset(cx, cy + w * 0.28f),
            radius = w * 0.32f
        ),
        topLeft = Offset(cx - w * 0.3f, cy + w * 0.18f),
        size = androidx.compose.ui.geometry.Size(w * 0.6f, w * 0.2f)
    )

    fun lobe(dx: Float, dy: Float, r: Float) {
        val center = Offset(cx + dx, cy + dy)
        // luce dall'alto a sinistra su ogni lobo per un effetto "gonfio"
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White, tint, blend(tint, shadowTint, 0.4f)),
                center = Offset(center.x - r * 0.35f, center.y - r * 0.35f),
                radius = r * 1.5f
            ),
            radius = r,
            center = center
        )
    }

    lobe(-w * 0.17f, w * 0.06f, w * 0.17f)
    lobe(w * 0.19f, w * 0.06f, w * 0.16f)
    lobe(w * 0.01f, -w * 0.06f, w * 0.23f)

    // base morbida della nuvola
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(tint, blend(tint, shadowTint, 0.25f))),
        topLeft = Offset(cx - w * 0.28f, cy + w * 0.01f),
        size = androidx.compose.ui.geometry.Size(w * 0.56f, w * 0.15f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.08f, w * 0.08f)
    )
    // riflesso lucido in alto
    drawCircle(color = Color.White.copy(alpha = 0.55f), radius = w * 0.06f, center = Offset(cx - w * 0.06f, cy - w * 0.13f))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSnowflake(cx: Float, cy: Float, r: Float) {
    val color = Color(0xFF9FC3E8)
    for (i in 0 until 3) {
        val angle = Math.toRadians((i * 60).toDouble())
        val dx = (r * cos(angle)).toFloat()
        val dy = (r * sin(angle)).toFloat()
        drawLine(color, Offset(cx - dx, cy - dy), Offset(cx + dx, cy + dy), strokeWidth = r * 0.28f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    }
}

private fun blend(a: Color, b: Color, ratio: Float): Color = Color(
    red = a.red + (b.red - a.red) * ratio,
    green = a.green + (b.green - a.green) * ratio,
    blue = a.blue + (b.blue - a.blue) * ratio,
    alpha = 1f
)

@Composable
fun HourlyRow(hourly: List<HourPoint>, count: Int) {
    val items = hourly.take(count)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(items) { p ->
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0x22000000))
                    .padding(vertical = 14.dp, horizontal = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(p.hour.take(5), color = Color(0xFF23262F), fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                WeatherGlyph(p.code, paletteFor(WeatherApi.category(p.code)), size = 32.dp)
                Spacer(Modifier.height(8.dp))
                Text("${p.temp.toInt()}°", color = Color(0xFF23262F), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun DailyList(daily: List<DayPoint>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0x22000000))
    ) {
        daily.forEachIndexed { i, day ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(WeatherApi.dayLabel(day.date, i), color = Color(0xFF23262F), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(WeatherApi.shortDate(day.date), color = Color(0xFF6B7080), fontSize = 12.sp)
                }
                WeatherGlyph(day.code, paletteFor(WeatherApi.category(day.code)), size = 30.dp)
                Text(WeatherApi.describe(day.code), color = Color(0xFF6B7080), fontSize = 13.sp, modifier = Modifier.weight(1f).padding(start = 12.dp))
                Text("${day.minTemp.toInt()}°", color = Color(0xFF6B7080), fontSize = 14.sp)
                Spacer(Modifier.width(8.dp))
                Text("${day.maxTemp.toInt()}°", color = Color(0xFF23262F), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            if (i != daily.lastIndex) {
                Divider(color = Color(0x11000000), thickness = 1.dp, modifier = Modifier.padding(horizontal = 18.dp))
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
html,body{margin:0;padding:0;background:#0D0F1F;}
#map{position:fixed;top:0;left:0;right:0;bottom:0;background:#0D0F1F;}
#playbtn{position:absolute;bottom:16px;left:16px;z-index:999;background:#fff;border:none;border-radius:20px;padding:10px 16px;font-family:sans-serif;font-weight:bold;}
</style>
</head>
<body>
<div id="map"></div>
<button id="playbtn">⏸ Pausa</button>
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<script>
var map = L.map('map').setView([$lat, $lon], 6);
setTimeout(function() { map.invalidateSize(); }, 300);
L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 18 }).addTo(map);

var radarLayer = null;
var frames = [];
var frameIndex = 0;
var playing = true;
var timer = null;

function showFrame(i) {
  if (radarLayer) map.removeLayer(radarLayer);
  var f = frames[i];
  radarLayer = L.tileLayer(f.host + f.path + '/256/{z}/{x}/{y}/2/1_1.png', { opacity: 0.75, maxNativeZoom: 6 });
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
  });

document.getElementById('playbtn').addEventListener('click', function() {
  playing = !playing;
  if (playing) { startPlaying(); this.innerText = '⏸ Pausa'; }
  else { clearInterval(timer); this.innerText = '▶ Play'; }
});
</script>
</body>
</html>
"""

@Composable
fun BottomBar(selected: Int, onSelect: (Int) -> Unit) {
    NavigationBar(containerColor = Color(0xFFF5F6FA)) {
        NavigationBarItem(selected = selected == 0, onClick = { onSelect(0) }, icon = {}, label = { Text("Meteo") })
        NavigationBarItem(selected = selected == 1, onClick = { onSelect(1) }, icon = {}, label = { Text("Radar") })
    }
}
