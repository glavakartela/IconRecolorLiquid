package com.glavakartela.iconrecolorliquid

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.ui.unit.IntOffset
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import androidx.compose.ui.draw.drawWithContent
import kotlin.math.abs
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal val PRESETS = listOf(
    0xFF1E3A5F to 0xFFFFD166,
    0xFFF4EDE4 to 0xFF3D2C8D,
    0xFF0F2E2A to 0xFF9BE7C4,
    0xFF2A1A3A to 0xFFFF8FB1,
    0xFFFFE6E1 to 0xFF8B1E3F,
    0xFF111111 to 0xFFF5F5F5,
)

@Composable
fun LiquidScreen(resumeTick: Int) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val TABS: List<Pair<ImageVector, String>> = listOf(
        Icons.Filled.Home to stringResource(R.string.tab_icons),
        Icons.Filled.Star to stringResource(R.string.tab_style),
        Icons.Filled.Create to stringResource(R.string.tab_shapes),
    )

    var themeMode by remember { mutableIntStateOf(Prefs.themeMode(ctx)) }
    var colorUi by remember { mutableStateOf(Prefs.colorUi(ctx)) }
    var settingsOpen by remember { mutableStateOf(false) }
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        1 -> false
        2 -> true
        else -> systemDark
    }

    var tab by remember { mutableIntStateOf(0) }
    var apps by remember { mutableStateOf(emptyList<AppEntry>()) }
    var query by remember { mutableStateOf("") }
    var wallpaperMode by remember { mutableStateOf(false) }
    var useSystemSeed by remember { mutableStateOf(false) }
    var darkIcons by remember { mutableStateOf(false) }
    var bg by remember { mutableIntStateOf(PRESETS[0].first.toInt()) }
    var fg by remember { mutableIntStateOf(PRESETS[0].second.toInt()) }
    var shape by remember { mutableStateOf(Prefs.selectedShape(ctx)) }
    var customs by remember { mutableStateOf(Prefs.customShapes(ctx)) }
    var look by remember { mutableStateOf(Prefs.look(ctx)) }
    var glass by remember { mutableFloatStateOf(Prefs.glass(ctx)) }
    var picking by remember { mutableIntStateOf(-1) }
    var drawing by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var editApp by remember { mutableStateOf<AppEntry?>(null) }
    var methodTick by remember { mutableIntStateOf(0) }
    var pinTick by remember { mutableIntStateOf(0) }
    var shizukuStatus by remember { mutableStateOf(ctx.getString(R.string.shizuku_not_connected)) }
    var bgBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var bgSource by remember { mutableIntStateOf(Prefs.bgSource(ctx)) }
    var bgBlur by remember { mutableIntStateOf(Prefs.bgBlur(ctx)) }
    var sysBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.Default) { Recolor.loadApps(ctx) }
        bgBitmap = withContext(Dispatchers.IO) { Backgrounds.loadSaved(ctx) }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val b = Backgrounds.import(ctx, uri)
                if (b != null) {
                    bgBitmap = b
                    bgSource = 2
                    Prefs.setBgSource(ctx, 2)
                } else {
                    toast(ctx, ctx.getString(R.string.toast_image_open_failed))
                }
            }
        }
    }

    val sysColors = remember(resumeTick) { Palette.wallpaperColors(ctx) }
    val imageSeed = remember(bgBitmap) { bgBitmap?.let { WallpaperSeed.fromBitmap(it) } }
    val seed: Int? = when {
        imageSeed != null && !useSystemSeed -> imageSeed
        sysColors.isNotEmpty() -> sysColors[0]
        else -> null
    }
    val scheme = remember(seed, dark, colorUi) {
        appColorScheme(if (colorUi) seed ?: Palette.NEUTRAL else Palette.NEUTRAL, dark)
    }

    val activity = ctx as? ComponentActivity
    DisposableEffect(dark) {
        activity?.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
        )
        onDispose { }
    }

    val style = remember(wallpaperMode, seed, darkIcons, bg, fg, shape, look, glass) {
        if (wallpaperMode) {
            val (b, f) = if (seed != null) {
                Palette.fromSeed(seed, darkIcons)
            } else {
                Palette.system(ctx, 1, darkIcons)
            }
            IconStyle(b, f, shape, look, glass)
        } else {
            IconStyle(bg, fg, shape, look, glass)
        }
    }
    val grayNote = if (seed != null && Tonal.toLch(seed).second < 8.0) stringResource(R.string.seed_gray) else ""
    val seedInfo = when {
        seed == null -> stringResource(R.string.seed_unknown)
        imageSeed != null && !useSystemSeed -> stringResource(R.string.seed_from_image, hexOf(seed), grayNote)
        else -> stringResource(R.string.seed_from_system, hexOf(seed), grayNote)
    }
    val pinned = remember(resumeTick, pinTick) { Shortcuts.pinnedKeys(ctx) }
    val shown = remember(apps, query) {
        apps.filter { it.label.contains(query.trim(), ignoreCase = true) }
    }
    val wallpaperImage = remember(bgBitmap) { bgBitmap?.asImageBitmap() }
    var wallTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(bgSource, resumeTick, wallTick) {
        sysBitmap = if (bgSource == 1) withContext(Dispatchers.IO) { Backgrounds.systemWallpaper(ctx) } else null
    }
    val bgImage = remember(bgSource, sysBitmap, bgBitmap) {
        when (bgSource) {
            1 -> sysBitmap?.asImageBitmap()
            2 -> bgBitmap?.asImageBitmap()
            else -> null
        }
    }

    fun selectShape(s: IconShape) {
        shape = s
        Prefs.setSelectedShape(ctx, s)
    }

    fun addShape(q: List<Int>, prefix: String) {
        val existing = (IconShapes.presets + customs).firstOrNull { it.q == q }
        if (existing != null) {
            selectShape(existing)
            return
        }
        val s = IconShape("$prefix ${customs.size + 1}", q)
        customs = customs + s
        Prefs.saveCustomShapes(ctx, customs)
        selectShape(s)
    }

    fun deleteShape(s: IconShape) {
        customs = customs - s
        Prefs.saveCustomShapes(ctx, customs)
        if (shape == s) selectShape(IconShapes.LAUNCHER)
    }

    val topPad = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 84.dp
    val bottomPad = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 176.dp
    val base = if (dark) Color(0xFF0B0B10) else Color(0xFFF2F2F7)

    // Page position in tabs, written once per frame by the thumb (Rust spring engine) and read only
    // in draw lambdas: pages follow the thumb exactly and nothing recomposes while it moves.
    var tabPosState by remember { mutableFloatStateOf(tab.toFloat()) }
    val tabPos: () -> Float = { tabPosState }

    MaterialTheme(colorScheme = scheme) {
        var bgView by remember { mutableStateOf<View?>(null) }
        var contentView by remember { mutableStateOf<View?>(null) }
        CompositionLocalProvider(LocalDark provides dark, LocalBackdropView provides contentView) {
            Box(Modifier.fillMaxSize()) {
                GlassBackdrop(onView = { bgView = it }) {
                    GlassBackground(style.bg, style.fg, bgImage, bgBlur, dark)
                }
                GlassBackdrop(onView = { contentView = it }) {
                CompositionLocalProvider(LocalBackdropView provides bgView) {
                Box(Modifier.fillMaxSize()) {
                GlassBackground(style.bg, style.fg, bgImage, bgBlur, dark)

                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val w = constraints.maxWidth
                    for (t in 0 until TABS.size) key(t) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { translationX = (t - tabPos()) * w }
                            .drawWithContent { if (abs(t - tabPos()) < 1f) drawContent() },
                    ) {
                    when (t) {
                        0 -> IconsTab(
                            topPad = topPad,
                            bottomPad = bottomPad,
                            shown = shown,
                            style = style,
                            pinned = pinned,
                            methodTick = methodTick,
                            query = query,
                            onQuery = { query = it },
                            shizukuStatus = shizukuStatus,
                            onConnect = {
                                scope.launch {
                                    shizukuStatus = ShizukuHelper.connect(ctx)
                                    for (i in 0 until 10) {
                                        if (ShizukuHelper.isConnected()) {
                                            shizukuStatus = ctx.getString(R.string.shizuku_connected)
                                            break
                                        }
                                        delay(300)
                                    }
                                }
                            },
                            onGrant = {
                                scope.launch {
                                    val r = withContext(Dispatchers.IO) {
                                        ShizukuHelper.grantShortcutPermission(ctx)
                                    }
                                    toast(ctx, r)
                                }
                            },
                            onSettings = { openShortcutPermission(ctx) },
                            onPin = { app ->
                                pin(ctx, app, style)
                                pinTick++
                            },
                            onLong = { editApp = it },
                        )

                        1 -> StyleTab(
                            topPad = topPad,
                            bottomPad = bottomPad,
                            apps = apps,
                            style = style,
                            wallpaperImage = wallpaperImage,
                            methodTick = methodTick,
                            onPickWallpaper = {
                                picker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            },
                            onClearWallpaper = {
                                Backgrounds.clear(ctx)
                                bgBitmap = null
                                useSystemSeed = false
                                if (bgSource == 2) {
                                    bgSource = 0
                                    Prefs.setBgSource(ctx, 0)
                                }
                            },
                            onLook = {
                                look = it
                                Prefs.setLook(ctx, it)
                            },
                            onGlass = {
                                glass = it
                                Prefs.setGlass(ctx, it)
                            },
                            wallpaperMode = wallpaperMode,
                            onMode = { wallpaperMode = it },
                            bg = bg,
                            fg = fg,
                            onPickBg = { picking = 0 },
                            onPickFg = { picking = 1 },
                            onPreset = { a, b ->
                                bg = a
                                fg = b
                            },
                            hasImage = imageSeed != null,
                            useSystem = useSystemSeed,
                            onSource = { useSystemSeed = it },
                            seedInfo = seedInfo,
                            darkIcons = darkIcons,
                            onDark = { darkIcons = it },
                        )

                        else -> ShapesTab(
                            topPad = topPad,
                            bottomPad = bottomPad,
                            style = style,
                            shape = shape,
                            customs = customs,
                            onSelect = { selectShape(it) },
                            onDraw = { drawing = true },
                            onImport = { importing = true },
                            onDelete = { deleteShape(it) },
                            onCopy = { copyText(ctx, it) },
                        )
                    }
                    }
                    }
                }

                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(topPad)
                        .background(Brush.verticalGradient(listOf(base.copy(alpha = 0.92f), Color.Transparent))),
                )
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(bottomPad)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, base.copy(alpha = 0.40f)))),
                )
                }
                }
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Liquid",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            stringResource(R.string.app_tagline),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    GlassIconButton(onClick = { settingsOpen = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings))
                    }
                }

                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    GlassButton(
                        onClick = {
                            val n = Shortcuts.updatePinned(ctx, style)
                            toast(
                                ctx,
                                when {
                                    n > 0 -> ctx.getString(R.string.toast_updated, n)
                                    n == 0 -> ctx.getString(R.string.toast_no_pinned)
                                    else -> ctx.getString(R.string.toast_update_refused)
                                },
                            )
                        },
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(stringResource(R.string.update_pinned), style = MaterialTheme.typography.labelLarge)
                    }
                    GlassTabBar(
                        TABS,
                        tab,
                        onSelect = { tab = it },
                        onPosition = { tabPosState = it },
                    )
                }
            }

            if (settingsOpen) {
                SettingsDialog(
                    themeMode = themeMode,
                    onTheme = {
                        themeMode = it
                        Prefs.setThemeMode(ctx, it)
                    },
                    colorUi = colorUi,
                    onColorUi = {
                        colorUi = it
                        Prefs.setColorUi(ctx, it)
                    },
                    bgSource = bgSource,
                    onBgSource = { s ->
                        if (s == 2 && bgBitmap == null) {
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        } else {
                            bgSource = s
                            Prefs.setBgSource(ctx, s)
                        }
                    },
                    bgBlur = bgBlur,
                    onBgBlur = {
                        bgBlur = it
                        Prefs.setBgBlur(ctx, it)
                    },
                    needAccess = bgSource == 1 && sysBitmap == null,
                    onAccess = {
                        scope.launch {
                            if (!ShizukuHelper.isConnected()) {
                                toast(ctx, ShizukuHelper.connect(ctx))
                                var tries = 0
                                while (!ShizukuHelper.isConnected() && tries < 15) {
                                    delay(200)
                                    tries++
                                }
                            }
                            if (android.os.Environment.isExternalStorageManager()) {
                                wallTick++
                            } else if (ShizukuHelper.isConnected()) {
                                toast(ctx, ctx.getString(R.string.toast_granting))
                                withContext(Dispatchers.IO) { ShizukuHelper.grantAllFilesAccess(ctx) }
                                wallTick++
                            } else {
                                runCatching {
                                    ctx.startActivity(
                                        android.content.Intent(
                                            android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                            android.net.Uri.parse("package:${ctx.packageName}"),
                                        ),
                                    )
                                }
                            }
                        }
                    },
                    onResetMethods = {
                        Prefs.clearMethods(ctx)
                        methodTick++
                        toast(ctx, ctx.getString(R.string.toast_methods_reset))
                    },
                    onDismiss = { settingsOpen = false },
                )
            }
            if (picking >= 0) {
                ColorDialog(
                    title = stringResource(if (picking == 0) R.string.color_bg_title else R.string.color_glyph_title),
                    initial = if (picking == 0) bg else fg,
                    onPick = { c ->
                        if (picking == 0) bg = c else fg = c
                        picking = -1
                    },
                    onDismiss = { picking = -1 },
                )
            }
            if (drawing) {
                DrawShapeDialog(
                    style = style,
                    onSave = { q ->
                        addShape(q, "Custom")
                        drawing = false
                    },
                    onDismiss = { drawing = false },
                )
            }
            if (importing) {
                ImportShapeDialog(
                    onImport = { q ->
                        addShape(q, "Imported")
                        importing = false
                    },
                    onDismiss = { importing = false },
                )
            }
            editApp?.let { app ->
                val current = remember(app, methodTick) { Prefs.method(ctx, app.component) }
                AppMethodDialog(
                    app = app,
                    current = current,
                    onSelect = { m ->
                        Prefs.setMethod(ctx, app.component, m)
                        methodTick++
                    },
                    onDismiss = { editApp = null },
                )
            }
        }
    }
}

private fun LazyGridScope.fullWidth(content: @Composable () -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) { content() }
}

@Composable
private fun IconsTab(
    topPad: Dp,
    bottomPad: Dp,
    shown: List<AppEntry>,
    style: IconStyle,
    pinned: Set<String>,
    methodTick: Int,
    query: String,
    onQuery: (String) -> Unit,
    shizukuStatus: String,
    onConnect: () -> Unit,
    onGrant: () -> Unit,
    onSettings: () -> Unit,
    onPin: (AppEntry) -> Unit,
    onLong: (AppEntry) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(84.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topPad, bottom = bottomPad),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        fullWidth { ShizukuPanel(shizukuStatus, onConnect, onGrant, onSettings) }
        fullWidth { GlassSearch(query, onQuery) }
        fullWidth {
            Column(Modifier.padding(top = 6.dp)) {
                Text(stringResource(R.string.apps_count, shown.size), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.apps_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(shown, key = { it.component.flattenToString() }) { app ->
            AppCell(
                app = app,
                style = style,
                pinned = app.component.flattenToString() in pinned,
                methodTick = methodTick,
                onClick = { onPin(app) },
                onLongClick = { onLong(app) },
            )
        }
    }
}

@Composable
private fun GlassSearch(query: String, onQuery: (String) -> Unit) {
    GlassPanel(Modifier.fillMaxWidth(), shape = RoundedCornerShape(percent = 50)) {
        TextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            placeholder = { Text(stringResource(R.string.search_app)) },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun ShizukuPanel(
    status: String,
    onConnect: () -> Unit,
    onGrant: () -> Unit,
    onSettings: () -> Unit,
) {
    SectionPanel(stringResource(R.string.perm_title)) {
        Text(
            stringResource(R.string.perm_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(status, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton(onClick = onConnect, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.connect)) }
            GlassButton(onClick = onGrant, modifier = Modifier.weight(1f), prominent = true) { Text(stringResource(R.string.allow)) }
        }
        GlassChip(stringResource(R.string.open_settings_manually), selected = false, onClick = onSettings)
    }
}

@Composable
private fun AppCell(
    app: AppEntry,
    style: IconStyle,
    pinned: Boolean,
    methodTick: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .springClick(onClick, onLongClick)
            .padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            IconBitmap(app, style, 64.dp, methodTick)
            if (pinned) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = stringResource(R.string.pinned),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(22.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape),
                )
            }
        }
        Text(
            app.label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun StyleTab(
    topPad: Dp,
    bottomPad: Dp,
    apps: List<AppEntry>,
    style: IconStyle,
    wallpaperImage: ImageBitmap?,
    methodTick: Int,
    onPickWallpaper: () -> Unit,
    onClearWallpaper: () -> Unit,
    onLook: (Look) -> Unit,
    onGlass: (Float) -> Unit,
    wallpaperMode: Boolean,
    onMode: (Boolean) -> Unit,
    bg: Int,
    fg: Int,
    onPickBg: () -> Unit,
    onPickFg: () -> Unit,
    onPreset: (Int, Int) -> Unit,
    hasImage: Boolean,
    useSystem: Boolean,
    onSource: (Boolean) -> Unit,
    seedInfo: String,
    darkIcons: Boolean,
    onDark: (Boolean) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topPad, bottom = bottomPad),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { PreviewPanel(apps, style, wallpaperImage, methodTick, onPickWallpaper, onClearWallpaper) }
        item { LookPanel(style.look, onLook, style.glass, onGlass) }
        if (style.look != Look.LIQUID_ORIGINAL) {
            item {
                ColorsPanel(
                    wallpaperMode, onMode, bg, fg, onPickBg, onPickFg, onPreset,
                    hasImage, useSystem, onSource, seedInfo, darkIcons, onDark,
                )
            }
        }
        item { BeforeAfterPanel(apps, style, methodTick) }
    }
}

@Composable
private fun PreviewPanel(
    apps: List<AppEntry>,
    style: IconStyle,
    wallpaper: ImageBitmap?,
    methodTick: Int,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    GlassPanel(Modifier.fillMaxWidth(), shape = RoundedCornerShape(32.dp)) {
        Column {
            Box(Modifier.fillMaxWidth().height(300.dp)) {
                if (wallpaper != null) {
                    Image(
                        wallpaper,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.20f)))
                } else {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(listOf(Color(0xFF1B1B1F), Color(0xFF3A3A44)))),
                    )
                }
                Column(
                    Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        apps.take(8).chunked(4).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                row.forEach { app ->
                                    Column(Modifier.width(64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        IconBitmap(app, style, 56.dp, methodTick)
                                        Text(
                                            app.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(top = 4.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(28.dp))
                            .background(Color.White.copy(alpha = 0.16f))
                            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(28.dp))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        apps.drop(8).take(4).forEach { app -> IconBitmap(app, style, 52.dp, methodTick) }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.example_on_wallpaper), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                GlassChip(stringResource(if (wallpaper == null) R.string.choose_wallpaper else R.string.change), selected = true, onClick = onPick)
                if (wallpaper != null) GlassChip(stringResource(R.string.reset), selected = false, onClick = onClear)
            }
        }
    }
}

@Composable
private fun LookPanel(look: Look, onLook: (Look) -> Unit, glass: Float, onGlass: (Float) -> Unit) {
    var tmp by remember(glass) { mutableFloatStateOf(glass) }
    SectionPanel(stringResource(R.string.look_title)) {
        GlassSegmented(
            options = listOf(stringResource(R.string.look_glass), stringResource(R.string.look_original)),
            selected = look.ordinal,
            onSelect = { onLook(Look.entries[it]) },
        )
        Text(
            stringResource(
                when (look) {
                    Look.LIQUID -> R.string.look_glass_desc
                    Look.LIQUID_ORIGINAL -> R.string.look_original_desc
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.glass_strength), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(
                "${(tmp * 100).toInt()}%",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = tmp,
            onValueChange = { tmp = it },
            onValueChangeFinished = { onGlass(tmp) },
            valueRange = 0.2f..1f,
        )
    }
}

@Composable
private fun ColorsPanel(
    wallpaperMode: Boolean,
    onMode: (Boolean) -> Unit,
    bg: Int,
    fg: Int,
    onPickBg: () -> Unit,
    onPickFg: () -> Unit,
    onPreset: (Int, Int) -> Unit,
    hasImage: Boolean,
    useSystem: Boolean,
    onSource: (Boolean) -> Unit,
    seedInfo: String,
    darkIcons: Boolean,
    onDark: (Boolean) -> Unit,
) {
    SectionPanel(stringResource(R.string.colors_title)) {
        GlassSegmented(
            options = listOf(stringResource(R.string.colors_own), stringResource(R.string.colors_wallpaper)),
            selected = if (wallpaperMode) 1 else 0,
            onSelect = { onMode(it == 1) },
        )

        AnimatedVisibility(visible = !wallpaperMode) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SwatchButton(stringResource(R.string.swatch_bg), bg, Modifier.weight(1f), onPickBg)
                    SwatchButton(stringResource(R.string.swatch_glyph), fg, Modifier.weight(1f), onPickFg)
                }
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PRESETS.forEach { (a, b) ->
                        PresetPill(a.toInt(), b.toInt()) { onPreset(a.toInt(), b.toInt()) }
                    }
                }
            }
        }

        AnimatedVisibility(visible = wallpaperMode) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassSegmented(
                    options = listOf(stringResource(R.string.source_image), stringResource(R.string.source_system)),
                    selected = if (hasImage && !useSystem) 0 else 1,
                    onSelect = { if (it == 1 || hasImage) onSource(it == 1) },
                )
                Text(
                    seedInfo,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SwitchRow(stringResource(R.string.dark_icons), stringResource(R.string.dark_icons_sub), darkIcons, onDark)
            }
        }
    }
}

@Composable
private fun BeforeAfterPanel(apps: List<AppEntry>, style: IconStyle, methodTick: Int) {
    val sample = apps.take(5)
    SectionPanel(stringResource(R.string.before_after_title)) {
        for ((label, st) in listOf(stringResource(R.string.before) to null, stringResource(R.string.after) to style)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    sample.forEach { IconBitmap(it, st, 54.dp, methodTick) }
                }
            }
        }
    }
}

@Composable
private fun ShapesTab(
    topPad: Dp,
    bottomPad: Dp,
    style: IconStyle,
    shape: IconShape,
    customs: List<IconShape>,
    onSelect: (IconShape) -> Unit,
    onDraw: () -> Unit,
    onImport: () -> Unit,
    onDelete: (IconShape) -> Unit,
    onCopy: (String) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topPad, bottom = bottomPad),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SectionPanel(stringResource(R.string.shape_title)) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    (listOf(IconShapes.LAUNCHER) + IconShapes.presets + customs).forEach { s ->
                        ShapeThumb(s, s == shape, Color(style.bg)) { onSelect(s) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassButton(onClick = onDraw, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.draw)) }
                    GlassButton(onClick = onImport, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.paste_code)) }
                }
            }
        }
        item {
            SectionPanel(stringResource(R.string.shape_code_title)) {
                val code = shape.code
                if (code != null) {
                    Text(
                        stringResource(R.string.shape_code_share),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(code, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassChip(stringResource(R.string.copy_code), selected = true, onClick = { onCopy(code) })
                        if (shape in customs) GlassChip(stringResource(R.string.delete_shape), selected = false, onClick = { onDelete(shape) })
                    }
                } else {
                    Text(
                        stringResource(R.string.shape_launcher_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ShapeThumb(shape: IconShape, selected: Boolean, fill: Color, onClick: () -> Unit) {
    val q = shape.q ?: IconShapes.squircle.q!!
    val outline = remember(q) { IconShapes.outline(q) }
    val ring = MaterialTheme.colorScheme.primary
    val edge = Color.White.copy(alpha = 0.45f)
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.1f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 400f),
        label = "thumb",
    )
    Column(
        Modifier
            .width(76.dp)
            .springClick(onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(
            Modifier
                .size(56.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
        ) {
            val half = size.minDimension / 2f
            val path = Path()
            for (k in 0 until outline.size / 2) {
                val x = half + outline[2 * k] * half * 0.9f
                val y = half + outline[2 * k + 1] * half * 0.9f
                if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            drawPath(path, color = fill)
            drawPath(
                path,
                color = if (selected) ring else edge,
                style = Stroke(width = if (selected) 3.dp.toPx() else 1.dp.toPx()),
            )
        }
        Text(
            shape.label(LocalContext.current),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionPanel(title: String, content: @Composable ColumnScope.() -> Unit) {
    GlassPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
internal fun IconBitmap(app: AppEntry, style: IconStyle?, size: Dp, methodTick: Int = 0) {
    val ctx = LocalContext.current
    val first = remember(app, style, methodTick) {
        if (style == null) null else Recolor.cachedPreview(ctx, app.component, style)?.asImageBitmap()
    }
    val bitmap by produceState<ImageBitmap?>(first, app, style, methodTick) {
        val cached = if (style == null) null else Recolor.cachedPreview(ctx, app.component, style)
        value = cached?.asImageBitmap() ?: withContext(Dispatchers.Default) {
            runCatching {
                val bmp = if (style == null) {
                    Recolor.original(ctx, app.component)
                } else {
                    Recolor.preview(ctx, app.component, style)
                }
                bmp.asImageBitmap()
            }.getOrNull()
        }
    }
    val b = bitmap
    val shown by animateFloatAsState(if (b != null) 1f else 0f, tween(260), label = "icon")
    Box(
        Modifier
            .size(size)
            .graphicsLayer {
                alpha = shown
                scaleX = 0.88f + 0.12f * shown
                scaleY = 0.88f + 0.12f * shown
            },
    ) {
        if (b != null) Image(b, contentDescription = null, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun SwatchButton(label: String, color: Int, modifier: Modifier, onClick: () -> Unit) {
    GlassPanel(modifier.springClick(onClick), shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(color))
                    .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelLarge)
                Text(
                    hexOf(color),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PresetPill(a: Int, b: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .springClick(onClick)
            .clip(CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape),
    ) {
        Box(Modifier.size(22.dp, 44.dp).background(Color(a)))
        Box(Modifier.size(22.dp, 44.dp).background(Color(b)))
    }
}

@Composable
internal fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

internal fun hexOf(c: Int): String = String.format("#%06X", 0xFFFFFF and c)

internal fun parseHex(t: String): Int? = runCatching {
    android.graphics.Color.parseColor(if (t.startsWith("#")) t else "#$t")
}.getOrNull()

internal fun toast(ctx: Context, text: String) {
    Toast.makeText(ctx, text, Toast.LENGTH_LONG).show()
}

private fun copyText(ctx: Context, text: String) {
    val cm = ctx.getSystemService(ClipboardManager::class.java)
    cm.setPrimaryClip(ClipData.newPlainText("icrec", text))
    toast(ctx, ctx.getString(R.string.toast_code_copied))
}

private fun pin(ctx: Context, app: AppEntry, style: IconStyle) {
    val sent = runCatching { Shortcuts.pin(ctx, app, style) }.getOrDefault(false)
    toast(
        ctx,
        if (sent) {
            ctx.getString(R.string.toast_pin_confirm)
        } else {
            ctx.getString(R.string.toast_pin_unsupported)
        },
    )
}

private fun openShortcutPermission(ctx: Context) {
    val pkg = ctx.packageName
    val tries = listOf(
        Intent("miui.intent.action.APP_PERM_EDITOR")
            .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
            .putExtra("extra_pkgname", pkg),
        Intent("miui.intent.action.APP_PERM_EDITOR")
            .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.AppPermissionsEditorActivity")
            .putExtra("extra_pkgname", pkg),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")),
    )
    for (intent in tries) {
        try {
            ctx.startActivity(intent)
            return
        } catch (e: Exception) {
        }
    }
}
