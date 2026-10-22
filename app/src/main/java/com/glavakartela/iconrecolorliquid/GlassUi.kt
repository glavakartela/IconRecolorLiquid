package com.glavakartela.iconrecolorliquid

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.channels.Channel
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.derivedStateOf
import kotlin.math.abs
import kotlinx.coroutines.delay
import androidx.compose.animation.core.animate
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import com.example.liquidglass.LiquidGlassView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

internal val LocalDark = compositionLocalOf { true }

internal val LocalBackdropView = compositionLocalOf<View?> { null }

@Composable
internal fun GlassBackdrop(onView: (View) -> Unit, content: @Composable () -> Unit) {
    val parent = rememberCompositionContext()
    val body by rememberUpdatedState(content)
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { c ->
            ComposeView(c).apply {
                setParentCompositionContext(parent)
                setContent { body() }
            }
        },
        update = { onView(it) },
    )
}

private class PassiveGlassView(context: Context) : LiquidGlassView(context) {
    override fun onTouchEvent(event: MotionEvent): Boolean = false
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
internal fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    strong: Boolean = false,
    tint: Color? = null,
    tintAlpha: Float? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    val cornerPx = remember(shape, density) {
        val r = (shape as? CornerBasedShape)?.topStart?.toPx(Size(10000f, 10000f), density)
            ?: (28f * density.density)
        if (r > 400f * density.density) 999f * density.density else r
    }
    val backdrop = LocalBackdropView.current
    val tintArgb = (tint ?: if (strong) Color.White else null)?.toArgb() ?: 0
    val tintStrength = tintAlpha ?: when {
        tint != null -> if (strong) 0.45f else 0.35f
        strong -> 0.10f
        else -> 0f
    }
    Box(modifier) {
        AndroidView(
            modifier = Modifier.matchParentSize(),
            factory = { c ->
                PassiveGlassView(c).apply {
                    enablePressEffect = false
                    enableDynamicBackground = true
                    dispersionStrength = 0.12f
                }
            },
            update = { g ->
                g.cornerRadius = cornerPx
                g.visibility = if (backdrop == null) View.INVISIBLE else View.VISIBLE
                if (backdrop != null) g.backdropSource = backdrop
                if (tintStrength > 0f) g.setGlassTint(tintArgb, tintStrength) else g.glassTint = 0
            },
        )
        content()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Modifier.springClick(onClick: () -> Unit, onLongClick: (() -> Unit)? = null): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 520f),
        label = "press",
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .combinedClickable(
            interactionSource = source,
            indication = null,
            onClick = onClick,
            onLongClick = onLongClick,
        )
}

@Composable
internal fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    prominent: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    GlassPanel(
        modifier.springClick(onClick),
        shape = RoundedCornerShape(percent = 50),
        strong = prominent,
        tint = if (prominent) primary else null,
    ) {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
internal fun GlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    GlassPanel(modifier.size(46.dp).springClick(onClick), shape = CircleShape, strong = true) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center, content = content)
    }
}

@Composable
internal fun GlassChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    GlassPanel(
        modifier.springClick(onClick),
        shape = RoundedCornerShape(percent = 50),
        tint = if (selected) primary else null,
    ) {
        Box(Modifier.padding(horizontal = 16.dp, vertical = 9.dp), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/**
 * Pill switcher with a liquid thumb. The motion comes from the Rust spring engine ([ThumbEngine]):
 * the thumb has two independent edges, so it stretches while travelling and squashes a little on arrival.
 *
 * Performance rules: the animation state is read only in draw lambdas (never in composition or layout),
 * frames run only while something moves, and the thumb is drawn as a real pill (rounded corners stay
 * circular at any width, unlike a scaled view).
 */
@Composable
internal fun GlassSwitcher(
    count: Int,
    selected: Int,
    onSelect: (Int) -> Unit,
    itemW: Dp,
    itemH: Dp,
    pad: Dp,
    modifier: Modifier = Modifier,
    barShape: Shape = RoundedCornerShape(percent = 50),
    onPosition: ((Float) -> Unit)? = null,
    item: @Composable (index: Int) -> Unit,
) {
    val density = LocalDensity.current
    val itemWpx = with(density) { itemW.toPx() }
    val dark = LocalDark.current
    val engine = remember(count) { ThumbEngine(count, selected.coerceIn(0, count - 1)) }
    DisposableEffect(engine) { onDispose { engine.free() } }
    // Bumped once per simulated frame; read only inside draw lambdas.
    var tick by remember { mutableIntStateOf(0) }
    val wake = remember(engine) { Channel<Unit>(Channel.CONFLATED) }
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnPosition by rememberUpdatedState(onPosition)

    LaunchedEffect(engine) {
        while (true) {
            wake.receive()
            var last = 0L
            while (true) {
                val now = withFrameNanos { it }
                val dt = if (last == 0L) 1f / 120f else ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
                last = now
                engine.step(dt)
                val s = engine.state
                currentOnPosition?.invoke(
                    (0.5f * (s[ThumbEngine.S_LEFT] + s[ThumbEngine.S_RIGHT]) - 0.5f)
                        .coerceIn(0f, (count - 1).toFloat()),
                )
                tick++
                if (s[ThumbEngine.S_SETTLED] > 0.5f) break
            }
        }
    }
    LaunchedEffect(engine, selected) {
        engine.select(selected.coerceIn(0, count - 1))
        wake.trySend(Unit)
    }

    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    LaunchedEffect(engine, pressed) {
        engine.press(pressed)
        wake.trySend(Unit)
    }

    GlassPanel(modifier, shape = barShape) {
        Box(
            Modifier
                .padding(pad)
                .pointerInput(engine, itemWpx) {
                    var startFinger = 0f
                    var startCenter = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { o ->
                            val s = engine.state
                            startFinger = o.x
                            startCenter = 0.5f * (s[ThumbEngine.S_LEFT] + s[ThumbEngine.S_RIGHT])
                            engine.drag(startCenter)
                            wake.trySend(Unit)
                        },
                        onDragEnd = {
                            val idx = engine.release()
                            currentOnSelect(idx)
                            wake.trySend(Unit)
                        },
                        onDragCancel = {
                            val idx = engine.release()
                            currentOnSelect(idx)
                            wake.trySend(Unit)
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            engine.drag(startCenter + (change.position.x - startFinger) / itemWpx)
                            wake.trySend(Unit)
                        },
                    )
                }
                .drawBehind {
                    tick // redraw on every simulated frame
                    val s = engine.state
                    val w = size.width / count
                    drawLiquidThumb(
                        left = s[ThumbEngine.S_LEFT] * w,
                        right = s[ThumbEngine.S_RIGHT] * w,
                        height = size.height * s[ThumbEngine.S_SCALE_Y],
                        glow = s[ThumbEngine.S_GLOW],
                        dark = dark,
                    )
                },
        ) {
            Row {
                for (i in 0 until count) {
                    Box(
                        Modifier
                            .size(itemW, itemH)
                            .graphicsLayer {
                                tick
                                val s = engine.state
                                val over = (minOf(s[ThumbEngine.S_RIGHT], i + 1f) - maxOf(s[ThumbEngine.S_LEFT], i.toFloat()))
                                    .coerceIn(0f, 1f)
                                val moving = (s[ThumbEngine.S_SPEED] / 1.2f).coerceIn(0f, 1f)
                                val press01 = s[ThumbEngine.S_PRESS].coerceIn(0f, 1f)
                                alpha = 1f - over * (0.5f * moving + 0.2f * press01)
                            }
                            .clickable(interactionSource = press, indication = null) { onSelect(i) },
                        contentAlignment = Alignment.Center,
                    ) {
                        item(i)
                    }
                }
            }
        }
    }
}

/** Liquid pill: soft fill that brightens while moving, top sheen and a glassy rim. */
private fun DrawScope.drawLiquidThumb(left: Float, right: Float, height: Float, glow: Float, dark: Boolean) {
    val w = right - left
    if (w <= 1f || height <= 1f) return
    val top = (size.height - height) / 2f
    val tl = Offset(left, top)
    val sz = Size(w, height)
    val r = CornerRadius(height / 2f, height / 2f)
    if (!dark) {
        drawRoundRect(Color.Black.copy(alpha = 0.06f + 0.04f * glow), tl + Offset(0f, 1.5.dp.toPx()), sz, r)
    }
    val fill = if (dark) 0.15f + 0.20f * glow else 0.50f + 0.25f * glow
    drawRoundRect(Color.White.copy(alpha = fill), tl, sz, r)
    drawRoundRect(
        brush = Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.10f + 0.16f * glow), Color.Transparent),
            startY = top,
            endY = top + height * 0.65f,
        ),
        topLeft = tl,
        size = sz,
        cornerRadius = r,
    )
    drawRoundRect(
        brush = Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.70f),
                Color.White.copy(alpha = 0.04f),
                Color.White.copy(alpha = 0.40f),
            ),
            start = Offset(left, top),
            end = Offset(right, top + height),
        ),
        topLeft = tl,
        size = sz,
        cornerRadius = r,
        style = Stroke(width = 1.2.dp.toPx()),
    )
}

@Composable
internal fun GlassSegmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val pad = 4.dp
        val itemW = (maxWidth - pad * 2) / options.size
        GlassSwitcher(
            count = options.size,
            selected = selected,
            onSelect = onSelect,
            itemW = itemW,
            itemH = 40.dp,
            pad = pad,
            modifier = Modifier.fillMaxWidth(),
        ) { i ->
            val base = if (i == selected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(
                options[i],
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                color = base,
            )
        }
    }
}

@Composable
internal fun GlassTabBar(
    tabs: List<Pair<ImageVector, String>>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onPosition: ((Float) -> Unit)? = null,
) {
    GlassSwitcher(
        count = tabs.size,
        selected = selected,
        onSelect = onSelect,
        itemW = 96.dp,
        itemH = 58.dp,
        pad = 5.dp,
        modifier = modifier,
        onPosition = onPosition,
    ) { i ->
        val (icon, label) = tabs[i]
        val tint = if (i == selected) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp), tint = tint)
            Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
        }
    }
}

@Composable
internal fun GlassBackground(bg: Int, fg: Int, image: ImageBitmap?, blur: Int, dark: Boolean) {
    val base = if (dark) Color(0xFF0B0B10) else Color(0xFFF2F2F7)
    Box(Modifier.fillMaxSize()) {
        if (image == null) {
            Aurora(bg, fg, base, dark)
        } else {
            Box(Modifier.fillMaxSize().background(base))
            val radius = when (blur) {
                0 -> 0.dp
                1 -> 14.dp
                else -> 40.dp
            }
            Image(
                image,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val z = if (blur > 0) 1.15f else 1f
                        scaleX = z
                        scaleY = z
                    }
                    .then(if (blur > 0) Modifier.blur(radius) else Modifier),
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            base.copy(alpha = if (image == null) 0.10f else 0.18f),
                            base.copy(alpha = if (image == null) 0.50f else 0.42f),
                        ),
                    ),
                ),
        )
    }
}

@Composable
private fun Aurora(bg: Int, fg: Int, base: Color, dark: Boolean) {
    val cBg by animateColorAsState(Color(bg), tween(700), label = "bgColor")
    val cFg by animateColorAsState(Color(fg), tween(700), label = "fgColor")
    val transition = rememberInfiniteTransition(label = "aurora")
    val p by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(22000, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift",
    )
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val a = if (dark) 0.55f else 0.45f
        drawRect(base)
        fun blob(c: Color, cx: Float, cy: Float, r: Float) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(c.copy(alpha = a), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = r,
                ),
                radius = r,
                center = Offset(cx, cy),
            )
        }
        blob(cBg, w * (0.18f + 0.25f * p), h * (0.16f + 0.10f * p), w * 0.95f)
        blob(cFg, w * (0.88f - 0.30f * p), h * (0.46f + 0.14f * p), w * 0.85f)
        blob(cBg, w * (0.30f + 0.20f * p), h * (0.86f - 0.12f * p), w * 0.90f)
    }
}
