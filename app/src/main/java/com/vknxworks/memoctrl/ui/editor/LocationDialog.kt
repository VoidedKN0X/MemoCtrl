package com.vknxworks.memoctrl.ui.editor

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.vknxworks.memoctrl.data.model.MemoLocation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import androidx.compose.ui.res.stringResource
import com.vknxworks.memoctrl.R

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

/**
 * One fix from the phone's satellite receiver (GPS). Network location is never asked for: on many
 * phones that sends nearby Wi-Fi and cell towers to an outside location service.
 */
private suspend fun currentLocation(context: Context): Location? {
    if (!hasLocationPermission(context)) return null
    val manager = context.getSystemService(LocationManager::class.java) ?: return null
    if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) return null
    return withTimeoutOrNull(45_000) {
        suspendCancellableCoroutine { cont ->
            val signal = CancellationSignal()
            cont.invokeOnCancellation { signal.cancel() }
            try {
                manager.getCurrentLocation(LocationManager.GPS_PROVIDER, signal, context.mainExecutor) { loc ->
                    if (cont.isActive) cont.resume(loc)
                }
            } catch (_: SecurityException) {
                if (cont.isActive) cont.resume(null)
            } catch (_: IllegalArgumentException) {
                if (cont.isActive) cont.resume(null)
            }
        }
    }
}

/** Where a memo was written: a place name and coordinates, typed in or taken from the phone's GPS. */
@Composable
fun LocationDialog(location: MemoLocation?, onConfirm: (MemoLocation?) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf(location?.placeholder.orEmpty()) }
    var lat by rememberSaveable { mutableStateOf(location?.latitude?.toString().orEmpty()) }
    var lon by rememberSaveable { mutableStateOf(location?.longitude?.toString().orEmpty()) }
    var locating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var gpsOff by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    val candidate = MemoLocation.fromInput(name, lat, lon)
    DisposableEffect(Unit) { onDispose { job?.cancel() } }

    fun locate() {
        if (locating) return
        error = null
        gpsOff = false
        val manager = context.getSystemService(LocationManager::class.java)
        if (manager == null || !manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            error = context.getString(R.string.location_gps_off)
            gpsOff = true
            return
        }
        job = scope.launch {
            locating = true
            try {
                val fix = currentLocation(context)
                if (fix == null) {
                    error = context.getString(R.string.location_no_fix)
                } else {
                    lat = fix.latitude.toString()
                    lon = fix.longitude.toString()
                    if (name.isBlank()) name = MemoLocation(latitude = fix.latitude, longitude = fix.longitude).coordinatesText
                }
            } catch (e: CancellationException) {
                throw e
            } finally {
                locating = false
            }
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it } || hasLocationPermission(context)) locate()
        else error = context.getString(R.string.location_denied)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.LocationOn, null) },
        title = { Text(stringResource(R.string.location_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(
                    enabled = !locating,
                    onClick = {
                        if (hasLocationPermission(context)) locate()
                        else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (locating) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(stringResource(if (locating) R.string.location_finding else R.string.location_use_current))
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (error != null && gpsOff) {
                    TextButton(onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) } }) {
                        Text(stringResource(R.string.location_settings))
                    }
                }
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.location_place)) }, singleLine = true, enabled = !locating, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    lat, { lat = it },
                    label = { Text(stringResource(R.string.location_latitude)) },
                    supportingText = { Text(stringResource(R.string.location_latitude_range)) },
                    singleLine = true,
                    enabled = !locating,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    lon, { lon = it },
                    label = { Text(stringResource(R.string.location_longitude)) },
                    supportingText = { Text(stringResource(R.string.location_longitude_range)) },
                    singleLine = true,
                    enabled = !locating,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (location != null) {
                    TextButton(onClick = { onConfirm(null) }) { Text(stringResource(R.string.location_remove), color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = { TextButton(enabled = candidate != null && !locating, onClick = { onConfirm(candidate) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
