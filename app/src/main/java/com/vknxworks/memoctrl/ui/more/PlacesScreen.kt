package com.vknxworks.memoctrl.ui.more

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.vknxworks.memoctrl.R
import com.vknxworks.memoctrl.container
import com.vknxworks.memoctrl.data.model.MemoEntity
import com.vknxworks.memoctrl.ui.components.CtrlAlertDialog
import com.vknxworks.memoctrl.ui.components.LoadingView
import com.vknxworks.memoctrl.ui.components.MessageView
import com.vknxworks.memoctrl.ui.components.ScreenTopBar
import com.vknxworks.memoctrl.ui.memos.MemoCard
import com.vknxworks.memoctrl.ui.nav.LocalNavigator
import com.vknxworks.memoctrl.ui.theme.CtrlColors
import com.vknxworks.memoctrl.ui.theme.CtrlType
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.tan

private const val MIN_ZOOM = 1.0
private const val MAX_ZOOM = 18.0

/** Longitude as a fraction of the world's width (Web Mercator), 0 at 180° west. */
internal fun worldX(longitude: Double): Double = (longitude + 180.0) / 360.0

/** Latitude as a fraction of the world's height (Web Mercator), 0 at the top. */
internal fun worldY(latitude: Double): Double {
    val lat = latitude.coerceIn(-85.0511, 85.0511) * PI / 180.0
    return (1.0 - ln(tan(lat) + 1.0 / kotlin.math.cos(lat)) / PI) / 2.0
}

/**
 * The memos that have a location, as dots on a map; tapping a dot shows the memo. The map pictures come
 * from openstreetmap.org and are only loaded after the user turned that on here (it tells that website
 * which areas are looked at). Until then the dots are drawn on an empty background, in the right places
 * relative to each other.
 */
@Composable
fun PlacesScreen() {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val session by c.sessions.active.collectAsStateWithLifecycle()
    val repo = session?.repo ?: return
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val all by remember(repo) { repo.observeMemos() }.collectAsStateWithLifecycle(initialValue = null)
    val placed = remember(all) { all?.filter { it.location?.hasValidCoordinates == true } }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var asking by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.places_title)) {
            IconButton(onClick = {
                if (settings.mapTiles) scope.launch { c.settings.update { it.copy(mapTiles = false) } } else asking = true
            }) {
                Icon(
                    Icons.Outlined.Map,
                    stringResource(if (settings.mapTiles) R.string.places_hide_map else R.string.places_show_map),
                    tint = if (settings.mapTiles) CtrlColors.AccentText else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        when {
            placed == null -> LoadingView()
            placed.isEmpty() -> MessageView(Icons.Outlined.LocationOff, stringResource(R.string.places_empty), stringResource(R.string.places_empty_body))
            else -> {
                val selected = placed.firstOrNull { it.identifier == selectedId }
                MemoMap(placed, selectedId, settings.mapTiles, Modifier.weight(1f).fillMaxWidth()) { selectedId = it }
                if (selected != null) {
                    MemoCard(selected, onOpen = { nav.memo(selected.identifier) })
                } else {
                    Text(
                        stringResource(if (settings.mapTiles) R.string.places_hint else R.string.places_hint_no_map),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }

    if (asking) CtrlAlertDialog(
        onDismissRequest = { asking = false },
        title = { Text(stringResource(R.string.places_show_map_title)) },
        text = { Text(stringResource(R.string.places_show_map_body)) },
        confirmButton = {
            TextButton(onClick = {
                asking = false
                scope.launch { c.settings.update { it.copy(mapTiles = true) } }
            }) { Text(stringResource(R.string.places_show_map)) }
        },
        dismissButton = { TextButton(onClick = { asking = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun MemoMap(memos: List<MemoEntity>, selectedId: String?, tiles: Boolean, modifier: Modifier, onSelect: (String?) -> Unit) {
    val density = LocalDensity.current
    // Map pictures are 256 pixels wide; on sharp screens they are drawn somewhat larger so labels stay readable.
    val tilePx = 256.0 * (density.density / 1.5).coerceAtLeast(1.0)
    val points = remember(memos) {
        memos.mapNotNull { m -> m.location?.let { Triple(m.identifier, worldX(it.longitude), worldY(it.latitude)) } }
    }
    BoxWithConstraints(modifier.clipToBounds().background(CtrlColors.Surface)) {
        val w = constraints.maxWidth.toDouble()
        val h = constraints.maxHeight.toDouble()
        // Start with every dot in view.
        val start = remember(points.size, w, h) {
            val minX = points.minOf { it.second }
            val maxX = points.maxOf { it.second }
            val minY = points.minOf { it.third }
            val maxY = points.maxOf { it.third }
            val dx = (maxX - minX).coerceAtLeast(1e-9)
            val dy = (maxY - minY).coerceAtLeast(1e-9)
            val fit = minOf(log2(w / (tilePx * dx)), log2(h / (tilePx * dy))) - 0.6
            Triple((minX + maxX) / 2, (minY + maxY) / 2, if (points.size == 1 || fit > 14.0) 14.0 else fit.coerceIn(MIN_ZOOM, MAX_ZOOM))
        }
        var cx by remember(start) { mutableDoubleStateOf(start.first) }
        var cy by remember(start) { mutableDoubleStateOf(start.second) }
        var zoom by remember(start) { mutableDoubleStateOf(start.third) }

        val world = tilePx * 2.0.pow(zoom)
        val left = cx * world - w / 2
        val top = cy * world - h / 2

        Box(
            Modifier.fillMaxSize().pointerInput(Unit) {
                detectTransformGestures { centroid, pan, gestureZoom, _ ->
                    val before = tilePx * 2.0.pow(zoom)
                    val newZoom = (zoom + log2(gestureZoom.toDouble())).coerceIn(MIN_ZOOM, MAX_ZOOM)
                    val after = tilePx * 2.0.pow(newZoom)
                    // Keep the place under the fingers where it is while zooming, then move by the drag.
                    val fx = (cx * before - size.width / 2.0 + centroid.x) / before
                    val fy = (cy * before - size.height / 2.0 + centroid.y) / before
                    zoom = newZoom
                    cx = ((fx * after - centroid.x + size.width / 2.0 - pan.x) / after).let { it - floor(it) }
                    cy = ((fy * after - centroid.y + size.height / 2.0 - pan.y) / after).coerceIn(0.0, 1.0)
                }
            },
        ) {
            if (tiles) {
                val z = floor(zoom).toInt().coerceIn(0, 19)
                val n = 1 shl z
                val tileSize = tilePx * 2.0.pow(zoom - z)
                val firstX = floor(left / tileSize).toInt()
                val lastX = floor((left + w) / tileSize).toInt()
                val firstY = floor(top / tileSize).toInt().coerceAtLeast(0)
                val lastY = floor((top + h) / tileSize).toInt().coerceAtMost(n - 1)
                val sizeDp = with(density) { (tileSize + 1).toFloat().toDp() }
                for (ty in firstY..lastY) {
                    for (tx in firstX..lastX) {
                        val wrapped = ((tx % n) + n) % n
                        key(z, tx, ty) {
                            AsyncImage(
                                model = "https://tile.openstreetmap.org/$z/$wrapped/$ty.png",
                                contentDescription = null,
                                contentScale = ContentScale.FillBounds,
                                modifier = Modifier
                                    .offset { IntOffset((tx * tileSize - left).roundToInt(), (ty * tileSize - top).roundToInt()) }
                                    .size(sizeDp),
                            )
                        }
                    }
                }
            }
            val dot = 22.dp
            val half = with(density) { dot.toPx() / 2 }
            points.forEach { (id, x, y) ->
                // The nearest copy of the dot when the map wraps around the date line.
                var px = x * world - left
                if (px < -world / 2) px += world else if (px > w + world / 2) px -= world
                val py = y * world - top
                if (px > -half * 2 && px < w + half * 2 && py > -half * 2 && py < h + half * 2) {
                    val isSelected = id == selectedId
                    key(id) {
                        Box(
                            Modifier
                                .offset { IntOffset((px - half).roundToInt(), (py - half).roundToInt()) }
                                .size(dot)
                                .clip(CircleShape)
                                .background(if (isSelected) CtrlColors.OnAccent else CtrlColors.Accent)
                                .border(3.dp, if (isSelected) CtrlColors.Accent else CtrlColors.OnAccent, CircleShape)
                                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.places_open_dot)) { onSelect(if (isSelected) null else id) },
                        )
                    }
                }
            }
            if (tiles) {
                // Required by OpenStreetMap's licence wherever its map is shown.
                Text(
                    "© OpenStreetMap contributors",
                    style = CtrlType.Mono,
                    color = androidx.compose.ui.graphics.Color.Black,
                    modifier = Modifier.align(Alignment.BottomEnd).background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.75f)).padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}
