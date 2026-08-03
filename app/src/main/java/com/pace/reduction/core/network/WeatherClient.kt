package com.pace.reduction.core.network

import com.pace.reduction.BuildConfig
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
private data class WeatherResponse(val current: CurrentWeather)

@Serializable
private data class CurrentWeather(
    @SerialName("temperature_2m") val temperatureCelsius: Double,
    val precipitation: Double,
    @SerialName("weather_code") val weatherCode: Int,
    @SerialName("wind_speed_10m") val windSpeedKmh: Double,
)

data class WeatherSnapshot(
    val temperatureCelsius: Double,
    val precipitationMm: Double,
    val weatherCode: Int,
    val windSpeedKmh: Double,
) {
    val suitableForOutdoorReset: Boolean
        get() = precipitationMm <= 0.2 && windSpeedKmh < 35.0 && temperatureCelsius in -5.0..32.0 &&
            weatherCode !in setOf(65, 67, 71, 73, 75, 77, 82, 85, 86, 95, 96, 99)
}

object WeatherCoordinates {
    fun round(value: Double): Double = kotlin.math.round(value * 100.0) / 100.0
}

class WeatherClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun current(latitude: Double, longitude: Double): WeatherSnapshot = withContext(Dispatchers.IO) {
        val roundedLatitude = WeatherCoordinates.round(latitude)
        val roundedLongitude = WeatherCoordinates.round(longitude)
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("api.open-meteo.com")
            .addPathSegments("v1/forecast")
            .addQueryParameter("latitude", roundedLatitude.toString())
            .addQueryParameter("longitude", roundedLongitude.toString())
            .addQueryParameter("current", "temperature_2m,precipitation,weather_code,wind_speed_10m")
            .addQueryParameter("timezone", "auto")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Pace/${BuildConfig.VERSION_NAME}")
            .build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Weather service returned ${response.code}" }
            val body = response.body.string().take(MAX_RESPONSE_CHARS)
            val current = json.decodeFromString<WeatherResponse>(body).current
            require(current.temperatureCelsius in -100.0..70.0)
            require(current.precipitation in 0.0..1_000.0)
            require(current.weatherCode in 0..99)
            require(current.windSpeedKmh in 0.0..500.0)
            WeatherSnapshot(
                temperatureCelsius = current.temperatureCelsius,
                precipitationMm = current.precipitation,
                weatherCode = current.weatherCode,
                windSpeedKmh = current.windSpeedKmh,
            )
        }
    }

    private companion object {
        const val MAX_RESPONSE_CHARS = 64 * 1024
    }
}
