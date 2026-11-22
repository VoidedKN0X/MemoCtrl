package com.vknxworks.memoctrl.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import com.vknxworks.memoctrl.ui.components.CtrlAlertDialog
import com.vknxworks.memoctrl.ui.components.ctrlOutlinedFieldColors
import com.vknxworks.memoctrl.ui.theme.CtrlShapes
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vknxworks.memoctrl.data.model.MemoLocation
import androidx.compose.ui.res.stringResource
import com.vknxworks.memoctrl.R

/**
 * Where a memo was written: a place name and coordinates, typed in. MemoCtrl has no location permission and
 * never reads the phone's position; a location already on a memo (from another app or the web) is kept.
 */
@Composable
fun LocationDialog(location: MemoLocation?, onConfirm: (MemoLocation?) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(location?.placeholder.orEmpty()) }
    var lat by rememberSaveable { mutableStateOf(location?.latitude?.toString().orEmpty()) }
    var lon by rememberSaveable { mutableStateOf(location?.longitude?.toString().orEmpty()) }
    val candidate = MemoLocation.fromInput(name, lat, lon)

    CtrlAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.LocationOn, null) },
        title = { Text(stringResource(R.string.location_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.location_place)) }, singleLine = true, shape = CtrlShapes.Button, colors = ctrlOutlinedFieldColors(), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    lat, { lat = it },
                    label = { Text(stringResource(R.string.location_latitude)) },
                    supportingText = { Text(stringResource(R.string.location_latitude_range)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = CtrlShapes.Button,
                    colors = ctrlOutlinedFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    lon, { lon = it },
                    label = { Text(stringResource(R.string.location_longitude)) },
                    supportingText = { Text(stringResource(R.string.location_longitude_range)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = CtrlShapes.Button,
                    colors = ctrlOutlinedFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (location != null) {
                    TextButton(onClick = { onConfirm(null) }) { Text(stringResource(R.string.location_remove), color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = { TextButton(enabled = candidate != null, onClick = { onConfirm(candidate) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
