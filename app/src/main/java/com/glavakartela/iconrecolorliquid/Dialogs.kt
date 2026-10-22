package com.glavakartela.iconrecolorliquid

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
import androidx.compose.ui.window.Dialog
import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource

/** Base dialog with a glass surface. */
@Composable
internal fun GlassDialog(
    onDismiss: () -> Unit,
    title: String,
    confirm: (@Composable () -> Unit)? = null,
    dismiss: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val enter = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { enter.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = 520f)) }
    Dialog(
        onDismissRequest = {
            scope.launch {
                enter.animateTo(0f, tween(170))
                onDismiss()
            }
        },
    ) {
        GlassPanel(
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val t = enter.value
                    alpha = t.coerceIn(0f, 1f)
                    translationY = (1f - t) * 72.dp.toPx()
                    scaleX = 0.94f + 0.06f * t
                    scaleY = 0.94f + 0.06f * t
                },
            shape = RoundedCornerShape(32.dp),
            strong = true,
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                content()
                if (confirm != null || dismiss != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        dismiss?.invoke()
                        confirm?.invoke()
                    }
                }
            }
        }
    }
}

@Composable
internal fun ColorDialog(title: String, initial: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) } }
    var h by remember { mutableFloatStateOf(hsv[0]) }
    var s by remember { mutableFloatStateOf(hsv[1]) }
    var v by remember { mutableFloatStateOf(hsv[2]) }
    var hex by remember { mutableStateOf(hexOf(initial)) }
    fun current() = android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))

    GlassDialog(
        onDismiss = onDismiss,
        title = title,
        confirm = { TextButton(onClick = { onPick(current()) }) { Text(stringResource(R.string.done)) } },
        dismiss = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(current())),
        )
        Slider(
            value = h,
            onValueChange = { h = it; hex = hexOf(current()) },
            valueRange = 0f..360f,
        )
        Slider(value = s, onValueChange = { s = it; hex = hexOf(current()) })
        Slider(value = v, onValueChange = { v = it; hex = hexOf(current()) })
        OutlinedTextField(
            value = hex,
            onValueChange = { t ->
                hex = t
                parseHex(t)?.let { c ->
                    android.graphics.Color.colorToHSV(c, hsv)
                    h = hsv[0]
                    s = hsv[1]
                    v = hsv[2]
                }
            },
            label = { Text("HEX") },
            singleLine = true,
        )
    }
}

/** Lets the user draw a custom icon shape. */
@Composable
internal fun DrawShapeDialog(style: IconStyle, onSave: (List<Int>) -> Unit, onDismiss: () -> Unit) {
    var stroke by remember { mutableStateOf(listOf<Offset>()) }
    val profile = remember(stroke) { IconShapes.fromStroke(stroke.map { it.x to it.y }) }
    val fill = Color(style.bg)
    val line = MaterialTheme.colorScheme.primary
    val guide = MaterialTheme.colorScheme.outline

    GlassDialog(
        onDismiss = onDismiss,
        title = stringResource(R.string.draw_shape_title),
        confirm = {
            TextButton(enabled = profile != null, onClick = { profile?.let(onSave) }) { Text(stringResource(R.string.save)) }
        },
        dismiss = {
            Row {
                TextButton(onClick = { stroke = emptyList() }) { Text(stringResource(R.string.clear)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    ) {
        Text(
            stringResource(R.string.draw_shape_hint),
            style = MaterialTheme.typography.bodySmall,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(22.dp))
                .background(Color.Black.copy(alpha = 0.25f))
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { o -> stroke = listOf(o) },
                        onDrag = { change, _ ->
                            stroke = stroke + change.position
                            change.consume()
                        },
                    )
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val lim = size.minDimension / 2f * 0.92f

                drawRect(
                    color = guide,
                    topLeft = Offset(cx - lim, cy - lim),
                    size = Size(lim * 2f, lim * 2f),
                    style = Stroke(width = 1.dp.toPx()),
                )
                if (stroke.size > 1) {
                    val p = Path()
                    p.moveTo(stroke[0].x, stroke[0].y)
                    for (i in 1 until stroke.size) p.lineTo(stroke[i].x, stroke[i].y)
                    drawPath(p, color = line.copy(alpha = 0.55f), style = Stroke(width = 2.dp.toPx()))
                }
                profile?.let { q ->
                    val pts = IconShapes.outline(q)
                    val path = Path()
                    for (k in 0 until pts.size / 2) {
                        val x = cx + pts[2 * k] * lim
                        val y = cy + pts[2 * k + 1] * lim
                        if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    path.close()
                    drawPath(path, color = fill.copy(alpha = 0.85f))
                    drawPath(path, color = line, style = Stroke(width = 2.dp.toPx()))
                }
            }
        }
    }
}

/** Imports a shape from an icrec- code. */
@Composable
internal fun ImportShapeDialog(onImport: (List<Int>) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val q = remember(text) { ShapeCodec.decode(text) }
    GlassDialog(
        onDismiss = onDismiss,
        title = stringResource(R.string.shape_code_dialog_title),
        confirm = { TextButton(enabled = q != null, onClick = { q?.let(onImport) }) { Text(stringResource(R.string.add)) } },
        dismiss = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            label = { Text("icrec-…") },
            isError = text.isNotBlank() && q == null,
            supportingText = {
                Text(
                    stringResource(
                        when {
                            text.isBlank() -> R.string.code_hint
                            q == null -> R.string.code_invalid
                            else -> R.string.code_valid
                        },
                    ),
                )
            },
        )
    }
}

/** Chooses the recolor method for a single app. */
@Composable
internal fun AppMethodDialog(
    app: AppEntry,
    current: Method,
    onSelect: (Method) -> Unit,
    onDismiss: () -> Unit,
) {
    GlassDialog(
        onDismiss = onDismiss,
        title = app.label,
        confirm = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
    ) {
        Text(
            stringResource(R.string.method_hint),
            style = MaterialTheme.typography.bodyMedium,
        )
        listOf(
            Method.AUTO to stringResource(R.string.method_auto),
            Method.GLYPH to stringResource(R.string.method_glyph),
            Method.GRADIENT to stringResource(R.string.method_gradient),
        ).forEach { (m, name) ->
            GlassChip(name, current == m, onClick = { onSelect(m) })
        }
    }
}

/** Languages offered in settings: BCP-47 tag (empty = follow the system) and the name in that language. */
private val LANGUAGES = listOf(
    "uk" to "Українська",
    "ru" to "Русский",
    "en" to "English",
    "de" to "Deutsch",
    "es" to "Español",
    "fr" to "Français",
    "pl" to "Polski",
)

private fun currentLanguage(ctx: Context): String {
    val list = ctx.getSystemService(LocaleManager::class.java).applicationLocales
    return if (list.isEmpty) "" else list.get(0).language
}

/** Applies the per-app language (Android 13+). The system recreates the activity by itself. */
private fun setLanguage(ctx: Context, tag: String) {
    ctx.getSystemService(LocaleManager::class.java).applicationLocales =
        if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
}

@Composable
internal fun SettingsDialog(
    themeMode: Int,
    onTheme: (Int) -> Unit,
    colorUi: Boolean,
    onColorUi: (Boolean) -> Unit,
    bgSource: Int,
    onBgSource: (Int) -> Unit,
    bgBlur: Int,
    onBgBlur: (Int) -> Unit,
    needAccess: Boolean,
    onAccess: () -> Unit,
    onResetMethods: () -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    var lang by remember { mutableStateOf(currentLanguage(ctx)) }
    GlassDialog(
        onDismiss = onDismiss,
        title = stringResource(R.string.settings),
        confirm = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
    ) {
        Text(stringResource(R.string.theme), style = MaterialTheme.typography.titleSmall)
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(0 to stringResource(R.string.theme_system), 1 to stringResource(R.string.theme_light), 2 to stringResource(R.string.theme_dark)).forEach { (m, name) ->
                GlassChip(name, themeMode == m, onClick = { onTheme(m) })
            }
        }
        Text(stringResource(R.string.language), style = MaterialTheme.typography.titleSmall)
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GlassChip(stringResource(R.string.language_system), lang.isEmpty(), onClick = {
                lang = ""
                setLanguage(ctx, "")
            })
            LANGUAGES.forEach { (tag, name) ->
                GlassChip(name, lang == tag, onClick = {
                    lang = tag
                    setLanguage(ctx, tag)
                })
            }
        }
        Text(stringResource(R.string.background), style = MaterialTheme.typography.titleSmall)
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(0 to stringResource(R.string.bg_colorful), 1 to stringResource(R.string.bg_wallpaper), 2 to stringResource(R.string.bg_custom)).forEach { (m, name) ->
                GlassChip(name, bgSource == m, onClick = { onBgSource(m) })
            }
        }
        if (bgSource != 0) {
            Text(stringResource(R.string.blur), style = MaterialTheme.typography.titleSmall)
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(0 to stringResource(R.string.blur_none), 1 to stringResource(R.string.blur_weak), 2 to stringResource(R.string.blur_strong)).forEach { (m, name) ->
                    GlassChip(name, bgBlur == m, onClick = { onBgBlur(m) })
                }
            }
        }
        if (needAccess) {
            Text(
                stringResource(R.string.access_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            GlassButton(onClick = onAccess, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.access_button)) }
        }
        SwitchRow(stringResource(R.string.ui_color), stringResource(R.string.ui_color_sub), colorUi, onColorUi)
        GlassButton(onClick = onResetMethods, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.reset_methods))
        }
    }
}
