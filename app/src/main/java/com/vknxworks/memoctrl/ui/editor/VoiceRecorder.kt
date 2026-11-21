package com.vknxworks.memoctrl.ui.editor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.vknxworks.memoctrl.R
import com.vknxworks.memoctrl.ui.components.CtrlAlertDialog
import com.vknxworks.memoctrl.ui.lock.AppLock
import com.vknxworks.memoctrl.ui.theme.CtrlType
import kotlinx.coroutines.delay
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** One recording in progress: the phone's own recorder writing AAC sound into an .m4a file in the app's cache. */
private class Recording(context: Context) {
    val file: File = File(File(context.cacheDir, "camera").also { it.mkdirs() }, "Voice ${LocalDateTime.now().format(STAMP)}.m4a")
    private var recorder: MediaRecorder? = MediaRecorder(context)
    val startedAt: Long

    init {
        val r = recorder!!
        try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(44_100)
            r.setAudioEncodingBitRate(96_000)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
        } catch (e: Exception) {
            runCatching { r.release() }
            recorder = null
            runCatching { file.delete() }
            throw e
        }
        startedAt = android.os.SystemClock.elapsedRealtime()
    }

    val running: Boolean get() = recorder != null

    /** Ends the recording. True when the file holds sound that can be kept. */
    fun stop(): Boolean {
        val r = recorder ?: return file.length() > 0
        recorder = null
        // stop() throws when nothing was recorded yet (stopped right after starting).
        val ok = runCatching { r.stop() }.isSuccess
        runCatching { r.release() }
        if (!ok) runCatching { file.delete() }
        return ok && file.length() > 0
    }

    fun discard() {
        stop()
        runCatching { file.delete() }
    }

    private companion object {
        val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH.mm.ss")
    }
}

/**
 * Records a voice memo with the phone's microphone and hands the sound file to [onRecorded], which
 * attaches it to the memo like any other file. The microphone is only used while this dialog shows
 * "Recording"; leaving the app stops the recording and keeps what was said until then.
 */
@Composable
fun VoiceRecorderDialog(onDismiss: () -> Unit, onRecorded: (Uri) -> Unit) {
    val context = LocalContext.current
    var recording by remember { mutableStateOf<Recording?>(null) }
    var stopped by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var denied by remember { mutableStateOf(false) }
    var seconds by remember { mutableLongStateOf(0L) }

    fun start() {
        try {
            recording = Recording(context)
        } catch (e: Exception) {
            failed = true
        }
    }

    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) start() else denied = true
    }
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) start()
        else {
            AppLock.expectReturn()
            ask.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    LaunchedEffect(recording, stopped) {
        val r = recording ?: return@LaunchedEffect
        while (!stopped) {
            seconds = (android.os.SystemClock.elapsedRealtime() - r.startedAt) / 1000
            delay(250)
        }
    }
    // Android cuts the microphone off for apps in the background: stop cleanly and keep what there is.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && recording?.running == true) {
                recording?.stop()
                stopped = true
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            // Closed any other way than "Attach" (the screen went away): nothing is kept.
            recording?.let { if (it.running) it.discard() }
        }
    }

    fun finish(keep: Boolean) {
        val r = recording
        recording = null
        if (r != null) {
            val ok = r.stop()
            if (keep && ok) {
                onRecorded(FileProvider.getUriForFile(context, context.packageName + ".files", r.file))
            } else {
                r.discard()
            }
        }
        onDismiss()
    }

    CtrlAlertDialog(
        onDismissRequest = {},
        title = {
            Text(
                stringResource(
                    when {
                        denied -> R.string.voice_denied_title
                        failed -> R.string.voice_failed_title
                        stopped -> R.string.voice_stopped
                        else -> R.string.voice_recording
                    },
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    denied -> Text(stringResource(R.string.voice_denied_body))
                    failed -> Text(stringResource(R.string.voice_failed_body))
                    else -> {
                        Text("%d:%02d".format(seconds / 60, seconds % 60), style = CtrlType.ReadoutSmall, color = MaterialTheme.colorScheme.onSurface)
                        Text(stringResource(if (stopped) R.string.voice_stopped_body else R.string.voice_recording_body))
                    }
                }
            }
        },
        confirmButton = {
            if (denied || failed) TextButton(onClick = { finish(keep = false) }) { Text(stringResource(R.string.close)) }
            else TextButton(onClick = { finish(keep = true) }, enabled = recording != null) { Text(stringResource(R.string.voice_attach)) }
        },
        dismissButton = if (denied || failed) null else { { TextButton(onClick = { finish(keep = false) }) { Text(stringResource(R.string.voice_discard)) } } },
    )
}
