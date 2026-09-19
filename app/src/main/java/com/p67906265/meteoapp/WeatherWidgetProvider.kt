package com.p67906265.meteoapp

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class WeatherWidgetProvider : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WeatherWidgetRefreshScheduler.schedule(context)
        WeatherWidgetRefreshScheduler.refreshNow(context)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        super.onUpdate(context, manager, ids)
        WeatherWidgetRefreshScheduler.schedule(context)
        updateWidgets(context, manager, ids)
    }

    private fun updateWidgets(context: Context, manager: AppWidgetManager, ids: IntArray) {
        if (ids.isEmpty()) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val place = WidgetLocationResolver.resolve(context)
                val weather = WeatherApi.fetch(place.lat, place.lon, place.city)
                ids.forEach { id -> manager.updateAppWidget(id, buildViews(context, weather)) }
            } catch (_: Exception) {
                ids.forEach { id -> manager.updateAppWidget(id, errorViews(context)) }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun buildViews(context: Context, weather: WeatherData): RemoteViews {
        val views = baseViews(context)
        val today = weather.daily.firstOrNull()
        views.setTextViewText(R.id.widget_temperature, "${weather.currentTemp.toInt()}°")
        views.setTextViewText(
            R.id.widget_min_max,
            today?.let { "${it.minTemp.toInt()}° / ${it.maxTemp.toInt()}°" } ?: ""
        )
        views.setTextViewText(R.id.widget_condition, WeatherApi.describe(weather.currentCode))
        views.setTextViewText(R.id.widget_city, weather.cityName)
        views.setTextViewText(R.id.widget_icon, weatherEmoji(weather.currentCode))
        views.setTextViewText(R.id.widget_date, formattedDate())
        views.setInt(R.id.widget_root, "setBackgroundResource", backgroundFor(weather.currentCode))
        return views
    }

    private fun errorViews(context: Context): RemoteViews = baseViews(context).apply {
        setTextViewText(R.id.widget_temperature, "--°")
        setTextViewText(R.id.widget_min_max, "")
        setTextViewText(R.id.widget_condition, "Apri Meteo per aggiornare")
        setTextViewText(R.id.widget_city, "Meteo")
        setTextViewText(R.id.widget_icon, "☁️")
        setTextViewText(R.id.widget_date, formattedDate())
    }

    private fun baseViews(context: Context): RemoteViews {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return RemoteViews(context.packageName, R.layout.widget_weather_large).apply {
            setOnClickPendingIntent(R.id.widget_root, pendingIntent)
        }
    }

    private fun backgroundFor(code: Int): Int = when (WeatherApi.category(code)) {
        WeatherApi.Category.CLEAR -> R.drawable.widget_bg_clear
        WeatherApi.Category.CLOUDY, WeatherApi.Category.FOG -> R.drawable.widget_bg_cloudy
        WeatherApi.Category.RAIN -> R.drawable.widget_bg_rain
        WeatherApi.Category.STORM -> R.drawable.widget_bg_storm
        WeatherApi.Category.SNOW -> R.drawable.widget_bg_snow
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

    companion object {
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, WeatherWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isNotEmpty()) {
                val intent = Intent(context, WeatherWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(intent)
            }
        }
    }
}
