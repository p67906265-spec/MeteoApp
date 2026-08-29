package com.p67906265.meteoapp

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class HourPoint(val hour: String, val temp: Double)

data class WeatherData(
    val currentTemp: Double,
    val currentCode: Int,
    val hourly: List<HourPoint>
)

object WeatherApi {

    fun fetch(lat: Double, lon: Double): WeatherData {
        val url = URL(
            "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,weather_code" +
                "&hourly=temperature_2m" +
                "&forecast_days=1&timezone=auto"
        )
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 10000
        conn.readTimeout = 10000

        val text = conn.inputStream.bufferedReader().use { it.readText() }
        val json = JSONObject(text)

        val current = json.getJSONObject("current")
        val currentTemp = current.getDouble("temperature_2m")
        val currentCode = current.getInt("weather_code")

        val hourly = json.getJSONObject("hourly")
        val times = hourly.getJSONArray("time")
        val temps = hourly.getJSONArray("temperature_2m")

        val points = mutableListOf<HourPoint>()
        for (i in 0 until times.length()) {
            val timeStr = times.getString(i) // e.g. 2026-08-29T14:00
            val hourLabel = timeStr.substringAfter("T")
            points.add(HourPoint(hourLabel, temps.getDouble(i)))
        }

        return WeatherData(currentTemp, currentCode, points)
    }

    /** Testo descrittivo semplificato dal WMO weather code */
    fun describe(code: Int): String = when (code) {
        0 -> "Sereno"
        1, 2 -> "Poco nuvoloso"
        3 -> "Nuvoloso"
        45, 48 -> "Nebbia"
        51, 53, 55 -> "Pioviggine"
        61, 63, 65 -> "Pioggia"
        71, 73, 75 -> "Neve"
        80, 81, 82 -> "Rovesci"
        95, 96, 99 -> "Temporale"
        else -> "Variabile"
    }
}
