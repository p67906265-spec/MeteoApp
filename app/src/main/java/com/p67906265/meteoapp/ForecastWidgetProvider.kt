package com.p67906265.meteoapp

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class ForecastWidgetProvider : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WeatherWidgetRefreshScheduler.schedule(context)
        WeatherWidgetRefreshScheduler.refreshNow(context)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        super.onUpdate(context, manager, ids)
        WeatherWidgetRefreshScheduler.schedule(context)
        if (ids.isEmpty()) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val place = WidgetLocationResolver.resolve(context)
                val weather = WeatherApi.fetchWithRetry(place.lat, place.lon, place.city)
                ids.forEach { manager.updateAppWidget(it, buildViews(context, weather)) }
            } catch (_: Exception) {
                ids.forEach { manager.updateAppWidget(it, errorViews(context)) }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun buildViews(context: Context, weather: WeatherData): RemoteViews {
        val views = baseViews(context)
        val today = weather.daily.firstOrNull()
        views.setTextViewText(R.id.landscape_current_icon, weatherEmoji(weather.currentCode))
        views.setTextViewText(R.id.landscape_condition, WeatherApi.describe(weather.currentCode))
        views.setTextViewText(
            R.id.landscape_today_range,
            today?.let { "${it.minTemp.toInt()}° ~ ${it.maxTemp.toInt()}°" } ?: ""
        )
        views.setTextViewText(R.id.landscape_temperature, "${weather.currentTemp.toInt()}°")
        views.setTextViewText(R.id.landscape_city, weather.cityName)
        views.setTextViewText(R.id.landscape_date, formattedDate())
        views.setInt(R.id.landscape_tint, "setBackgroundColor", tintFor(weather.currentCode))

        val days = weather.daily.drop(1).take(4)
        val dayIds = intArrayOf(R.id.forecast_day_1, R.id.forecast_day_2, R.id.forecast_day_3, R.id.forecast_day_4)
        val iconIds = intArrayOf(R.id.forecast_icon_1, R.id.forecast_icon_2, R.id.forecast_icon_3, R.id.forecast_icon_4)
        val tempIds = intArrayOf(R.id.forecast_temp_1, R.id.forecast_temp_2, R.id.forecast_temp_3, R.id.forecast_temp_4)
        dayIds.indices.forEach { index ->
            val day = days.getOrNull(index)
            views.setTextViewText(dayIds[index], day?.let { shortDay(it.date) } ?: "–")
            views.setTextViewText(iconIds[index], day?.let { weatherEmoji(it.code) } ?: "")
            views.setTextViewText(tempIds[index], day?.let { "${it.minTemp.toInt()}°/${it.maxTemp.toInt()}°" } ?: "")
        }
        return views
    }

    private fun errorViews(context: Context): RemoteViews = baseViews(context).apply {
        val city = context.getSharedPreferences("meteo_preferences", Context.MODE_PRIVATE)
            .getString("widget_city", "Roma") ?: "Roma"
        setTextViewText(R.id.landscape_current_icon, "☁️")
        setTextViewText(R.id.landscape_condition, "Aggiornamento in attesa")
        setTextViewText(R.id.landscape_today_range, "")
        setTextViewText(R.id.landscape_temperature, "--°")
        setTextViewText(R.id.landscape_city, city)
        setTextViewText(R.id.landscape_date, formattedDate())
    }

    private fun baseViews(context: Context): RemoteViews {
        val pendingIntent = PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return RemoteViews(context.packageName, R.layout.widget_weather_landscape).apply {
            setOnClickPendingIntent(R.id.landscape_widget_root, pendingIntent)
        }
    }

    private fun tintFor(code: Int): Int = when (WeatherApi.category(code)) {
        WeatherApi.Category.CLEAR -> Color.argb(28, 10, 35, 60)
        WeatherApi.Category.CLOUDY, WeatherApi.Category.FOG -> Color.argb(80, 45, 55, 70)
        WeatherApi.Category.RAIN -> Color.argb(105, 15, 45, 75)
        WeatherApi.Category.STORM -> Color.argb(125, 25, 20, 55)
        WeatherApi.Category.SNOW -> Color.argb(55, 75, 110, 140)
    }

    private fun weatherEmoji(code: Int): String = when (WeatherApi.category(code)) {
        WeatherApi.Category.CLEAR -> "☀️"
        WeatherApi.Category.CLOUDY -> "☁️"
        WeatherApi.Category.FOG -> "🌫️"
        WeatherApi.Category.RAIN -> "🌧️"
        WeatherApi.Category.STORM -> "⛈️"
        WeatherApi.Category.SNOW -> "❄️"
    }

    private fun formattedDate(): String = LocalDate.now().format(
        DateTimeFormatter.ofPattern("dd/M  EEE", Locale.ITALIAN)
    )

    private fun shortDay(isoDate: String): String = try {
        LocalDate.parse(isoDate).format(DateTimeFormatter.ofPattern("EEE", Locale.ITALIAN))
            .replaceFirstChar { it.uppercase() }
    } catch (_: Exception) { "–" }

    companion object {
        suspend fun updateAllNow(context: Context): Boolean {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, ForecastWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isEmpty()) return true

            val provider = ForecastWidgetProvider()
            return try {
                val place = WidgetLocationResolver.resolve(context)
                val weather = WeatherApi.fetchWithRetry(place.lat, place.lon, place.city)
                ids.forEach { manager.updateAppWidget(it, provider.buildViews(context, weather)) }
                true
            } catch (_: Exception) {
                ids.forEach { manager.updateAppWidget(it, provider.errorViews(context)) }
                false
            }
        }

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, ForecastWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isNotEmpty()) {
                context.sendBroadcast(Intent(context, ForecastWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                })
            }
        }
    }
}
