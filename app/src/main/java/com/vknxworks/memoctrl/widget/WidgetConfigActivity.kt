package com.vknxworks.memoctrl.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.memoctrl.container
import com.vknxworks.memoctrl.ui.components.Group
import com.vknxworks.memoctrl.ui.components.GroupChoice
import com.vknxworks.memoctrl.ui.components.GroupSwitch
import com.vknxworks.memoctrl.ui.components.PillButton
import com.vknxworks.memoctrl.ui.components.WindowPrivacy
import com.vknxworks.memoctrl.ui.theme.Gutter
import com.vknxworks.memoctrl.ui.theme.MemoCtrlTheme
import kotlinx.coroutines.flow.first
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.vknxworks.memoctrl.R

/** Settings of one "Memos" widget: which tag it shows, pinned memos only, and how many. */
class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val id = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Backing out leaves the widget off the home screen.
        setResult(Activity.RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val c = container
        val start = Widgets.config(this, id)
        setContent {
            val privacy by c.settings.settings.collectAsStateWithLifecycle()
            WindowPrivacy(this, privacy.hideScreenshots, privacy.hideRecents || privacy.appLock)
            val settings by c.settings.settings.collectAsStateWithLifecycle()
            MemoCtrlTheme(settings.theme, settings.dynamicColor) {
                var tag by remember { mutableStateOf(start.tag) }
                var pinned by remember { mutableStateOf(start.pinnedOnly) }
                var max by remember { mutableStateOf(start.maxItems) }
                val tags by produceState(emptyList<String>()) {
                    c.sessions.ready.first { it }
                    // Tag names stay hidden while the app lock is on.
                    value = if (settings.appLock) emptyList() else c.sessions.active.value?.repo?.let { runCatching { it.listTags() }.getOrNull() }.orEmpty()
                }
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())) {
                        Text(stringResource(R.string.widget_config_title), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(start = Gutter, top = 16.dp, bottom = 8.dp))
                        Group {
                            GroupChoice(
                                stringResource(R.string.widget_config_tag),
                                listOf<String?>(null).map { it to stringResource(R.string.widget_config_all) } + (tags + listOfNotNull(start.tag)).distinct().map { it to "#$it" },
                                tag,
                                icon = Icons.Outlined.Tag,
                            ) { tag = it }
                            GroupSwitch(stringResource(R.string.widget_config_pinned), checked = pinned, icon = Icons.Outlined.PushPin) { pinned = it }
                            GroupChoice(
                                stringResource(R.string.widget_config_max),
                                listOf(3, 5, 10, 20, 30).map { it to pluralStringResource(R.plurals.widget_config_count, it, it) },
                                max,
                                icon = Icons.Outlined.FormatListNumbered,
                            ) { max = it }
                        }
                        PillButton(
                            stringResource(R.string.widget_config_add),
                            onClick = {
                                Widgets.saveConfig(this@WidgetConfigActivity, id, Widgets.Config(tag, pinned, max))
                                Widgets.update(this@WidgetConfigActivity, intArrayOf(id), memory = false)
                                setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                                finish()
                            },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 16.dp),
                        )
                    }
                }
            }
        }
    }
}
