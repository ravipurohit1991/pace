package com.pace.reduction.feature

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.pace.reduction.PaceUiState
import com.pace.reduction.PaceViewModel
import com.pace.reduction.R
import com.pace.reduction.core.network.SafeLinks
import com.pace.reduction.core.network.WeatherClient
import com.pace.reduction.core.network.WeatherSnapshot
import com.pace.reduction.core.location.TriggerGeofenceManager
import kotlin.coroutines.resume
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

@Composable
internal fun WeatherAndPlacesSection(uiState: PaceUiState, viewModel: PaceViewModel) {
    val context = LocalContext.current
    val triggerPlaceSavedText = stringResource(R.string.trigger_place_saved)
    val locationUnavailableText = stringResource(R.string.location_unavailable)
    val locationDeniedText = stringResource(R.string.location_denied_manual)
    val backgroundDeniedText = stringResource(R.string.background_denied_manual)
    val scope = rememberCoroutineScope()
    val weatherClient = remember { WeatherClient() }
    var weather by remember { mutableStateOf<WeatherSnapshot?>(null) }
    var weatherLoading by remember { mutableStateOf(false) }
    var weatherFailed by remember { mutableStateOf(false) }
    var weatherExplained by rememberSaveable { mutableStateOf(false) }
    var placeLabel by rememberSaveable { mutableStateOf("") }
    var placeRadius by rememberSaveable { mutableIntStateOf(200) }
    var placeStatus by rememberSaveable { mutableStateOf<String?>(null) }
    var automaticEducationForId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingAutomaticId by rememberSaveable { mutableStateOf<String?>(null) }

    fun loadWeather() {
        scope.launch {
            weatherLoading = true
            weatherFailed = false
            val result = runCatching {
                val location = currentLocation(context, highAccuracy = false)
                    ?: error("Location unavailable")
                weatherClient.current(location.latitude, location.longitude)
            }
            weather = result.getOrNull()
            weatherFailed = result.isFailure
            weatherLoading = false
        }
    }

    val weatherPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) loadWeather() else weatherFailed = true
    }
    val placePermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            scope.launch {
                val location = runCatching { currentLocation(context, highAccuracy = true) }.getOrNull()
                if (location != null) {
                    viewModel.saveTriggerPlace(placeLabel, location.latitude, location.longitude, placeRadius)
                    placeLabel = ""
                    placeStatus = triggerPlaceSavedText
                } else {
                    placeStatus = locationUnavailableText
                }
            }
        } else {
            placeStatus = locationDeniedText
        }
    }
    val backgroundPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val id = pendingAutomaticId
        if (granted && id != null) {
            uiState.triggerPlaces.firstOrNull { it.id == id }?.let {
                viewModel.updateTriggerPlace(it.id, it.label, it.enabled, true)
            }
        } else {
            placeStatus = backgroundDeniedText
        }
        pendingAutomaticId = null
        automaticEducationForId = null
    }
    val backgroundSettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val id = pendingAutomaticId
        if (id != null && TriggerGeofenceManager.hasRequiredPermission(context)) {
            uiState.triggerPlaces.firstOrNull { it.id == id }?.let {
                viewModel.updateTriggerPlace(it.id, it.label, it.enabled, true)
            }
        } else {
            placeStatus = backgroundDeniedText
        }
        pendingAutomaticId = null
        automaticEducationForId = null
    }

    SectionCard {
        Text(stringResource(R.string.weather_reset_title), style = MaterialTheme.typography.titleLarge)
        if (!weatherExplained) {
            Text(stringResource(R.string.weather_permission_explanation))
            Button(onClick = { weatherExplained = true }) { Text(stringResource(R.string.continue_action)) }
        } else {
            when {
                weatherLoading -> CircularProgressIndicator()
                weather != null -> {
                    val current = requireNotNull(weather)
                    Text(stringResource(R.string.weather_summary, current.temperatureCelsius, current.windSpeedKmh))
                    Text(
                        stringResource(
                            if (current.suitableForOutdoorReset) R.string.weather_outdoor_suggestion
                            else R.string.weather_indoor_suggestion,
                        ),
                    )
                    OutlinedButton(onClick = { openWeatherSource(context) }) {
                        Icon(Icons.Outlined.Cloud, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.powered_by_open_meteo))
                    }
                }
                weatherFailed -> {
                    Text(stringResource(R.string.weather_fallback))
                    OutlinedButton(onClick = { requestWeather(context, weatherPermission::launch, ::loadWeather) }) {
                        Text(stringResource(R.string.try_again))
                    }
                }
                else -> Button(onClick = { requestWeather(context, weatherPermission::launch, ::loadWeather) }) {
                    Text(stringResource(R.string.use_weather))
                }
            }
        }
    }

    SectionCard {
        Text(stringResource(R.string.trigger_places_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.trigger_places_explanation))
        OutlinedTextField(
            value = placeLabel,
            onValueChange = { placeLabel = it.take(80) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.trigger_place_label)) },
        )
        Text(stringResource(R.string.trigger_radius, placeRadius))
        Slider(
            value = placeRadius.toFloat(),
            onValueChange = { placeRadius = (it / 50).toInt() * 50 },
            valueRange = 100f..500f,
            steps = 7,
        )
        Button(
            onClick = {
                placePermissions.launch(
                    arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION),
                )
            },
            enabled = placeLabel.isNotBlank() && uiState.triggerPlaces.size < 20,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.LocationOn, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.save_current_trigger_place))
        }
        placeStatus?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        uiState.triggerPlaces.forEach { place ->
            var editedLabel by rememberSaveable(place.id, place.label) { mutableStateOf(place.label) }
            OutlinedTextField(
                value = editedLabel,
                onValueChange = { editedLabel = it.take(80) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.trigger_place_row, place.label, place.radiusMeters)) },
                trailingIcon = {
                    IconButton(
                        onClick = { viewModel.updateTriggerPlace(place.id, editedLabel, place.enabled, place.automaticCueEnabled) },
                    ) { Icon(Icons.Outlined.Save, contentDescription = stringResource(R.string.save_place_name)) }
                },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.place_enabled), modifier = Modifier.weight(1f))
                Switch(
                    checked = place.enabled,
                    onCheckedChange = { viewModel.updateTriggerPlace(place.id, editedLabel, it, place.automaticCueEnabled && it) },
                )
                Text(stringResource(R.string.automatic_cue))
                Switch(
                    checked = place.automaticCueEnabled,
                    onCheckedChange = { enabled ->
                        if (!enabled) viewModel.updateTriggerPlace(place.id, editedLabel, place.enabled, false)
                        else automaticEducationForId = place.id
                    },
                )
                IconButton(onClick = { viewModel.deleteTriggerPlace(place.id) }) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.delete_trigger_place, place.label),
                    )
                }
            }
            if (automaticEducationForId == place.id) {
                Text(stringResource(R.string.background_location_education))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        pendingAutomaticId = place.id
                        when {
                            TriggerGeofenceManager.hasRequiredPermission(context) -> {
                                viewModel.updateTriggerPlace(place.id, editedLabel, place.enabled, true)
                                pendingAutomaticId = null
                                automaticEducationForId = null
                            }
                            Build.VERSION.SDK_INT >= 30 -> backgroundSettings.launch(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
                            )
                            Build.VERSION.SDK_INT >= 29 -> backgroundPermission.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                            else -> viewModel.updateTriggerPlace(place.id, editedLabel, place.enabled, true)
                        }
                    }) { Text(stringResource(R.string.continue_action)) }
                    OutlinedButton(onClick = { automaticEducationForId = null }) { Text(stringResource(R.string.cancel)) }
                }
            }
        }
        Text(stringResource(R.string.geofence_limit_note), style = MaterialTheme.typography.bodySmall)
    }
}

private fun requestWeather(context: Context, requestPermission: (String) -> Unit, load: () -> Unit) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
        load()
    } else {
        requestPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
    }
}

@Suppress("MissingPermission")
private suspend fun currentLocation(context: Context, highAccuracy: Boolean): android.location.Location? =
    suspendCancellableCoroutine { continuation ->
        val cancellation = CancellationTokenSource()
        continuation.invokeOnCancellation { cancellation.cancel() }
        LocationServices.getFusedLocationProviderClient(context)
            .getCurrentLocation(
                if (highAccuracy) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                cancellation.token,
            )
            .addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
            .addOnFailureListener { if (continuation.isActive) continuation.resume(null) }
    }

private fun openWeatherSource(context: Context) {
    val uri = SafeLinks.requireAllowed("https://open-meteo.com/")
    runCatching { CustomTabsIntent.Builder().build().launchUrl(context, uri) }
        .onFailure { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
}
