package com.p67906265.meteoapp

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class HourPoint(val hour: String, val temp: Double, val code: Int)
data class DayPoint(val date: String, val maxTemp: Double, val minTemp: Double, val code: Int)

data class WeatherData(
    val cityName: String,
    val currentTemp: Double,
    val currentCode: Int,
    val feelsLike: Double,
    val windSpeed: Double,
    val windDirection: Int,
    val humidity: Int,
    val pressure: Double,
    val hourly: List<HourPoint>,
    val daily: List<DayPoint>
)

data class CityResult(val name: String, val admin: String, val country: String, val lat: Double, val lon: Double)

object WeatherApi {

    private fun get(urlStr: String): String {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        return conn.inputStream.bufferedReader().use { it.readText() }
    }

    fun searchCity(query: String): List<CityResult> {
        val q = URLEncoder.encode(query, "UTF-8")
        val text = get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=8&language=it&format=json")
        val json = JSONObject(text)
        if (!json.has("results")) return emptyList()
        val results = json.getJSONArray("results")
        val list = mutableListOf<CityResult>()
        for (i in 0 until results.length()) {
            val r = results.getJSONObject(i)
            list.add(
                CityResult(
                    name = r.getString("name"),
                    admin = r.optString("admin1", ""),
                    country = r.optString("country", ""),
                    lat = r.getDouble("latitude"),
                    lon = r.getDouble("longitude")
                )
            )
        }
        return list
    }

    fun fetch(lat: Double, lon: Double, cityName: String): WeatherData {
        val text = get(
            "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,weather_code,apparent_temperature,wind_speed_10m,wind_direction_10m,relative_humidity_2m,surface_pressure" +
                "&hourly=temperature_2m,weather_code" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min" +
                "&forecast_days=7&timezone=auto"
        )
        val json = JSONObject(text)

        val current = json.getJSONObject("current")
        val currentTemp = current.getDouble("temperature_2m")
        val currentCode = current.getInt("weather_code")
        val feelsLike = current.optDouble("apparent_temperature", currentTemp)
        val windSpeed = current.optDouble("wind_speed_10m", 0.0)
        val windDirection = current.optInt("wind_direction_10m", 0)
        val humidity = current.optInt("relative_humidity_2m", 0)
        val pressure = current.optDouble("surface_pressure", 0.0)

        val hourly = json.getJSONObject("hourly")
        val times = hourly.getJSONArray("time")
        val temps = hourly.getJSONArray("temperature_2m")
        val codes = hourly.getJSONArray("weather_code")

        val hourPoints = mutableListOf<HourPoint>()
        for (i in 0 until times.length()) {
            val timeStr = times.getString(i)
            val hourLabel = timeStr.substringAfter("T")
            hourPoints.add(HourPoint(hourLabel, temps.getDouble(i), codes.getInt(i)))
        }

        val daily = json.getJSONObject("daily")
        val dTimes = daily.getJSONArray("time")
        val dMax = daily.getJSONArray("temperature_2m_max")
        val dMin = daily.getJSONArray("temperature_2m_min")
        val dCodes = daily.getJSONArray("weather_code")

        val dayPoints = mutableListOf<DayPoint>()
        for (i in 0 until dTimes.length()) {
            dayPoints.add(DayPoint(dTimes.getString(i), dMax.getDouble(i), dMin.getDouble(i), dCodes.getInt(i)))
        }

        return WeatherData(cityName, currentTemp, currentCode, feelsLike, windSpeed, windDirection, humidity, pressure, hourPoints, dayPoints)
    }

    fun describe(code: Int): String = when (code) {
        0 -> "Sereno"
        1, 2 -> "Poco nuvoloso"
        3 -> "Coperto"
        45, 48 -> "Nebbia"
        51, 53, 55 -> "Pioviggine"
        61, 63, 65 -> "Pioggia"
        71, 73, 75 -> "Neve"
        80, 81, 82 -> "Rovesci"
        95, 96, 99 -> "Temporale"
        else -> "Variabile"
    }

    enum class Category { CLEAR, CLOUDY, RAIN, STORM, SNOW, FOG }

    fun category(code: Int): Category = when {
        code == 0 || code == 1 -> Category.CLEAR
        code == 2 || code == 3 -> Category.CLOUDY
        code == 45 || code == 48 -> Category.FOG
        code in 51..67 || code in 80..82 -> Category.RAIN
        code in 71..77 -> Category.SNOW
        code in 95..99 -> Category.STORM
        else -> Category.CLOUDY
    }

    fun windDirLabel(deg: Int): String {
        val dirs = arrayOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")
        val idx = (((deg % 360) + 360) % 360 / 45.0).let { Math.round(it).toInt() % 8 }
        return dirs[idx]
    }

    fun dayLabel(isoDate: String, index: Int): String {
        if (index == 0) return "Oggi"
        return try {
            val parts = isoDate.split("-")
            val cal = java.util.Calendar.getInstance()
            cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
            val days = arrayOf("Domenica", "Lunedì", "Martedì", "Mercoledì", "Giovedì", "Venerdì", "Sabato")
            days[cal.get(java.util.Calendar.DAY_OF_WEEK) - 1]
        } catch (e: Exception) {
            isoDate
        }
    }

    fun shortDate(isoDate: String): String {
        return try {
            val parts = isoDate.split("-")
            "${parts[2]} ${monthAbbr(parts[1].toInt())}"
        } catch (e: Exception) {
            isoDate
        }
    }

    private fun monthAbbr(m: Int): String {
        val months = arrayOf("gen", "feb", "mar", "apr", "mag", "giu", "lug", "ago", "set", "ott", "nov", "dic")
        return months[(m - 1).coerceIn(0, 11)]
    }
}
