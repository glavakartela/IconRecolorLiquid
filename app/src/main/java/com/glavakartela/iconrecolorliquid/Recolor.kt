package com.glavakartela.iconrecolorliquid

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.util.DisplayMetrics
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt

/** A launchable activity of an installed app. */
data class AppEntry(val component: ComponentName, val label: String)

/**
 * How an icon without a usable monochrome layer becomes a glyph mask.
 * GLYPH keeps a simple glyph on a background, GRADIENT keeps the details (games), AUTO picks one.
 */
enum class Method { AUTO, GLYPH, GRADIENT }

enum class Look { LIQUID, LIQUID_ORIGINAL }

/** Icon style: [bg] tile color, [fg] glyph color, [glass] strength of the glass effect (0..1). */
data class IconStyle(
    val bg: Int,
    val fg: Int,
    val shape: IconShape = IconShapes.LAUNCHER,
    val look: Look = Look.LIQUID,
    val glass: Float = 0.85f,
)

/** Finds launcher apps and builds recolored glass icons from them. */
object Recolor {

    /** All apps that have a launcher icon, except this one. */
    fun loadApps(ctx: Context): List<AppEntry> {
        val pm = ctx.packageManager
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(query, PackageManager.ResolveInfoFlags.of(0L))
            .map { ri ->
                AppEntry(
                    ComponentName(ri.activityInfo.packageName, ri.activityInfo.name),
                    ri.loadLabel(pm).toString(),
                )
            }
            .filter { it.component.packageName != ctx.packageName }
            .sortedBy { it.label.lowercase() }
    }

    /**
     * The app's own icon from its resources. PackageManager would return the launcher-themed icon,
     * where the monochrome layer is no longer visible.
     */
    private fun rawIcon(ctx: Context, comp: ComponentName): Drawable {
        val pm = ctx.packageManager
        try {
            val ai = pm.getActivityInfo(comp, PackageManager.ComponentInfoFlags.of(0L))
            val id = ai.iconResource
            if (id != 0) {
                val res = pm.getResourcesForApplication(ai.applicationInfo)
                val d = res.getDrawableForDensity(id, DisplayMetrics.DENSITY_XXXHIGH, null)
                if (d != null) return d
            }
        } catch (e: Exception) {
        }
        return try {
            pm.getActivityIcon(comp)
        } catch (e: Exception) {
            pm.defaultActivityIcon
        }
    }

    /**
     * Glyph mask (0..1) on a size x size canvas: the app's monochrome layer when usable,
     * otherwise derived from the colored icon.
     */
    fun glyphMaskFor(ctx: Context, comp: ComponentName, size: Int, method: Method): FloatArray {
        val icon = rawIcon(ctx, comp)
        val n = size * size
        val mono = (icon as? AdaptiveIconDrawable)?.monochrome
        val monoPixels: IntArray? = if (mono != null && method == Method.AUTO) {
            val m = IntArray(n)
            layerBitmap(mono, size).getPixels(m, 0, size, 0, 0, size, size)
            if (monoUsable(m, size)) m else null
        } else {
            null
        }
        return if (monoPixels != null) {
            FloatArray(n) { Color.alpha(monoPixels[it]) / 255f }
        } else {
            silhouette(composite(icon, size), size, method)
        }
    }

    /** Visible center 72dp of the 108dp canvas, cut to [shape] (squircle for the launcher shape). */
    fun masked(full: Bitmap, shape: IconShape): Bitmap {
        val size = full.width
        val inset = size * 18 / 108
        val vis = size - 2 * inset
        val out = Bitmap.createBitmap(vis, vis, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val q = shape.q ?: IconShapes.squircle.q!!
        c.drawPath(shapePath(q, vis), p)
        p.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        c.drawBitmap(full, Rect(inset, inset, inset + vis, inset + vis), Rect(0, 0, vis, vis), p)
        return out
    }

    /** Closed path of a shape on a vis x vis canvas. */
    internal fun shapePath(q: List<Int>, vis: Int): Path {
        val pts = IconShapes.outline(q)
        val half = vis / 2f
        val path = Path()
        for (k in 0 until pts.size / 2) {
            val x = half + pts[2 * k] * half
            val y = half + pts[2 * k + 1] * half
            if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }

    /** Finished icon (visible 72dp part) in the chosen shape and look. Cached. */
    fun preview(ctx: Context, comp: ComponentName, style: IconStyle, size: Int = 288): Bitmap {
        val key = previewKey(ctx, comp, style, size)
        IconCache.get(key)?.let { return it }
        val method = Prefs.method(ctx, comp)
        val bmp = when (style.look) {
            Look.LIQUID -> Glass.duo(
                glyphMaskFor(ctx, comp, size, method), size, style.bg, style.fg, style.shape, style.glass,
            )
            Look.LIQUID_ORIGINAL -> {
                val (plate, logo) = logoLayers(ctx, comp, size)
                Glass.overLogo(plate, logo, style.shape, style.glass)
            }
        }
        IconCache.put(key, bmp)
        return bmp
    }

    /** The preview if it was already drawn, without computing it. */
    fun cachedPreview(ctx: Context, comp: ComponentName, style: IconStyle, size: Int = 288): Bitmap? =
        IconCache.get(previewKey(ctx, comp, style, size))

    private fun previewKey(ctx: Context, comp: ComponentName, style: IconStyle, size: Int): String {
        val colors = style.look != Look.LIQUID_ORIGINAL
        return listOf(
            comp.flattenToString(),
            if (colors) style.bg else 0,
            if (colors) style.fg else 0,
            style.shape.code ?: "L",
            style.look.name,
            (style.glass * 20).toInt(),
            Prefs.method(ctx, comp).name,
            size,
        ).joinToString("|")
    }

    /** The app's original icon in the launcher shape, for the before/after card. */
    fun original(ctx: Context, comp: ComponentName, size: Int = 288): Bitmap {
        val key = "o|" + comp.flattenToString() + "|" + size
        IconCache.get(key)?.let { return it }
        val icon = rawIcon(ctx, comp)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        if (icon is AdaptiveIconDrawable) {
            icon.background?.let { it.setBounds(0, 0, size, size); it.draw(c) }
            icon.foreground?.let { it.setBounds(0, 0, size, size); it.draw(c) }
        } else {
            val inset = size * 18 / 108
            icon.setBounds(inset, inset, size - inset, size - inset)
            icon.draw(c)
        }
        val out = masked(bmp, IconShapes.LAUNCHER)
        IconCache.put(key, out)
        return out
    }

    /** Original icon on an opaque size x size canvas, the base for glass-over-original. */
    fun originalCanvas(ctx: Context, comp: ComponentName, size: Int): Bitmap {
        val icon = rawIcon(ctx, comp)
        val raw = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(raw)
        if (icon is AdaptiveIconDrawable) {
            icon.background?.let { it.setBounds(0, 0, size, size); it.draw(c) }
            icon.foreground?.let { it.setBounds(0, 0, size, size); it.draw(c) }
        } else {
            val inset = size * 18 / 108
            icon.setBounds(inset, inset, size - inset, size - inset)
            icon.draw(c)
        }
        val px = IntArray(size * size)
        raw.getPixels(px, 0, size, 0, 0, size, size)
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val oc = Canvas(out)
        oc.drawColor(opaqueEdgeColor(px, size))
        oc.drawBitmap(raw, 0f, 0f, null)
        return out
    }

    /**
     * The icon split into an opaque plate (adaptive background, or the edge color) and the logo layer
     * with its own alpha (adaptive foreground, or the whole legacy icon). Both are size x size canvases.
     */
    fun logoLayers(ctx: Context, comp: ComponentName, size: Int): Pair<Bitmap, Bitmap> {
        val icon = rawIcon(ctx, comp)
        val logo = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val plateRaw = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        if (icon is AdaptiveIconDrawable) {
            icon.background?.let { it.setBounds(0, 0, size, size); it.draw(Canvas(plateRaw)) }
            icon.foreground?.let { it.setBounds(0, 0, size, size); it.draw(Canvas(logo)) }
        } else {
            val inset = size * 18 / 108
            icon.setBounds(inset, inset, size - inset, size - inset)
            icon.draw(Canvas(logo))
        }
        val src = if (icon is AdaptiveIconDrawable && icon.background != null) plateRaw else logo
        val px = IntArray(size * size)
        src.getPixels(px, 0, size, 0, 0, size, size)
        val plate = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val pc = Canvas(plate)
        pc.drawColor(opaqueEdgeColor(px, size))
        pc.drawBitmap(plateRaw, 0f, 0f, null)
        return plate to logo
    }

    fun shortcutIcon(ctx: Context, comp: ComponentName, style: IconStyle, size: Int): Icon =
        Icon.createWithBitmap(preview(ctx, comp, style, size))

    private class Composite(val full: IntArray, val base: IntArray, val hasBase: Boolean)

    private class Frame(val found: Boolean, val width: Int)

    private fun composite(icon: Drawable, size: Int): Composite {
        val n = size * size
        val base = IntArray(n)
        val full = IntArray(n)
        var hasBase = false
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        if (icon is AdaptiveIconDrawable) {
            val bgLayer = icon.background
            if (bgLayer != null) {
                val layer = layerBitmap(bgLayer, size)
                hasBase = opaqueShare(layer, size) > 0.9f
                c.drawBitmap(layer, 0f, 0f, null)
            }
            bmp.getPixels(base, 0, size, 0, 0, size, size)
            icon.foreground?.let { it.setBounds(0, 0, size, size); it.draw(c) }
            bmp.getPixels(full, 0, size, 0, 0, size, size)
        } else {
            val inset = size * 18 / 108
            icon.setBounds(inset, inset, size - inset, size - inset)
            val raw = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            icon.draw(Canvas(raw))
            val rawPx = IntArray(n)
            raw.getPixels(rawPx, 0, size, 0, 0, size, size)
            c.drawColor(opaqueEdgeColor(rawPx, size))
            c.drawBitmap(raw, 0f, 0f, null)
            bmp.getPixels(full, 0, size, 0, 0, size, size)
        }
        return Composite(full, base, hasBase)
    }

    /**
     * Glyph mask for icons without a monochrome layer: what the icon adds on top of its base,
     * or a gradient mask for illustrations. Only the visible area is analyzed.
     */
    private fun silhouette(c: Composite, size: Int, method: Method): FloatArray {
        val n = size * size
        val edge = edgeMap(size)
        val lab = labMap(c.full)
        val frame = detectFrame(lab, edge, size)

        val ringBase = IntArray(n)
        ringBase.fill(ringColor(c.full, size))
        var mask = FloatArray(n)
        val candidates = if (c.hasBase) listOf(c.base, ringBase) else listOf(ringBase)
        for (b in candidates) {
            mask = polarityMask(c.full, b, edge)
            if (coverage(mask, edge) in 0.004f..0.8f) break
        }
        if (frame.found) {
            for (i in 0 until n) mask[i] *= frameFactor(edge[i], frame.width)
        }

        val cov = coverage(mask, edge)
        val cells = cellCount(lab, mask, edge)
        val gradient = when (method) {
            Method.GLYPH -> false
            Method.GRADIENT -> true
            Method.AUTO -> cov > 0.6f || (cells >= 6 && cov > 0.25f) || cov < 0.004f
        }
        return if (gradient) gradientMask(lab, edge, frame, size) else fitGlyph(mask, size)
    }

    private fun edgeMap(size: Int): IntArray {
        val inset = size * 18 / 108
        val last = size - 1 - inset
        return IntArray(size * size) { i ->
            val x = i % size
            val y = i / size
            minOf(minOf(x - inset, y - inset), minOf(last - x, last - y))
        }
    }

    private fun labMap(px: IntArray): FloatArray {
        val out = FloatArray(px.size * 3)
        for (i in px.indices) Tonal.labInto(px[i], out, i * 3)
        return out
    }

    private fun labDist(lab: FloatArray, i: Int, m: FloatArray): Float {
        val a = lab[3 * i] - m[0]
        val b = lab[3 * i + 1] - m[1]
        val c = lab[3 * i + 2] - m[2]
        return sqrt(a * a + b * b + c * c)
    }

    /** Detects a thin single-color frame at the edge that differs from the rest (stock calculator). */
    private fun detectFrame(lab: FloatArray, edge: IntArray, size: Int): Frame {
        val vis = size - 2 * (size * 18 / 108)
        val w = max(2, (0.07f * vis).toInt())
        val mo = FloatArray(3)
        val mi = FloatArray(3)
        var no = 0
        var ni = 0
        for (i in edge.indices) {
            val e = edge[i]
            if (e < 0 || e >= 3 * w) continue
            val m = if (e < w) mo else mi
            m[0] += lab[3 * i]
            m[1] += lab[3 * i + 1]
            m[2] += lab[3 * i + 2]
            if (e < w) no++ else ni++
        }
        if (no == 0 || ni == 0) return Frame(false, w)
        for (k in 0 until 3) {
            mo[k] = mo[k] / no
            mi[k] = mi[k] / ni
        }
        var dev = 0f
        for (i in edge.indices) {
            if (edge[i] in 0 until w) dev += labDist(lab, i, mo)
        }
        dev /= no
        val gap = sqrt(
            (mo[0] - mi[0]) * (mo[0] - mi[0]) +
                (mo[1] - mi[1]) * (mo[1] - mi[1]) +
                (mo[2] - mi[2]) * (mo[2] - mi[2]),
        )
        return Frame(gap > 28f && dev < 14f, w)
    }

    private fun frameFactor(edge: Int, w: Int): Float = smooth(w - 1f, w + 2f, edge.toFloat())

    /** Mask of the glyph by its difference from the base, keeping pixels close to the dominant glyph tone. */
    private fun polarityMask(full: IntArray, base: IntArray, edge: IntArray): FloatArray {
        val n = full.size

        val d = FloatArray(n)
        val dh = IntArray(256)
        var cnt = 0
        for (i in 0 until n) {
            val p = full[i]
            val q = base[i]
            val dr = Color.red(p) - Color.red(q)
            val dg = Color.green(p) - Color.green(q)
            val db = Color.blue(p) - Color.blue(q)
            val v = sqrt((dr * dr + dg * dg + db * db).toFloat())
            d[i] = v
            if (edge[i] >= 0 && v > 24f) {
                dh[(v * 255f / 442f).toInt().coerceIn(0, 255)]++
                cnt++
            }
        }
        var acc = 0
        var bin = 255
        val limit = (cnt * 0.1f).toInt()
        while (bin > 0 && acc < limit) {
            acc += dh[bin]
            bin--
        }
        val hi = max(bin * 442f / 255f, 60f)
        val present = FloatArray(n) { if (edge[it] >= 0) smooth(0.25f, 0.55f, d[it] / hi) else 0f }

        val lh = IntArray(64)
        var inks = 0
        for (i in 0 until n) {
            if (present[i] > 0.5f) {
                lh[luma(full[i]) * 63 / 255]++
                inks++
            }
        }
        if (inks == 0) return present
        var best = 0
        var bestScore = -1
        for (b in 0 until 64) {
            val score = lh[b] * 2 + (if (b > 0) lh[b - 1] else 0) + (if (b < 63) lh[b + 1] else 0)
            if (score > bestScore) {
                bestScore = score
                best = b
            }
        }
        val dominant = (best + 0.5f) / 64f

        return FloatArray(n) {
            val l = luma(full[it]) / 255f
            present[it] * (1f - smooth(0.28f, 0.5f, abs(l - dominant)))
        }
    }

    private fun coverage(mask: FloatArray, edge: IntArray): Float {
        var on = 0
        var total = 0
        for (i in mask.indices step 2) {
            if (edge[i] < 0) continue
            total++
            if (mask[i] > 0.5f) on++
        }
        return if (total == 0) 0f else on.toFloat() / total
    }

    private fun cellCount(lab: FloatArray, mask: FloatArray, edge: IntArray): Int {
        val cnt = IntArray(7 * 12 * 12)
        var ink = 0
        for (i in mask.indices) {
            if (edge[i] < 0 || mask[i] <= 0.5f) continue
            val l = (lab[3 * i] / 15f).toInt().coerceIn(0, 6)
            val a = ((lab[3 * i + 1] + 120f) / 20f).toInt().coerceIn(0, 11)
            val b = ((lab[3 * i + 2] + 120f) / 20f).toInt().coerceIn(0, 11)
            cnt[(l * 12 + a) * 12 + b]++
            ink++
        }
        if (ink == 0) return 0
        val need = ink * 0.04f
        return cnt.count { it >= need }
    }

    private fun cell(lab: FloatArray, i: Int): Int {
        val l = (lab[3 * i] / 25f).toInt().coerceIn(0, 3)
        val a = ((lab[3 * i + 1] + 120f) / 30f).toInt().coerceIn(0, 7)
        val b = ((lab[3 * i + 2] + 120f) / 30f).toInt().coerceIn(0, 7)
        return (l * 8 + a) * 8 + b
    }

    /**
     * For illustrations (games): the dominant edge color is the background and everything else
     * is tinted by its distance from it, so details and recognizability stay.
     */
    private fun gradientMask(lab: FloatArray, edge: IntArray, frame: Frame, size: Int): FloatArray {
        val n = edge.size
        val vis = size - 2 * (size * 18 / 108)
        val w = frame.width
        val lo = if (frame.found) w + 2 else 0
        val ringEnd = max(lo + 4, (0.12f * vis).toInt())

        val cnt = IntArray(256)
        var ringPx = 0
        for (i in 0 until n) {
            val e = edge[i]
            if (e >= lo && e < ringEnd) {
                cnt[cell(lab, i)]++
                ringPx++
            }
        }
        val useRing = ringPx > 50
        if (!useRing) {
            cnt.fill(0)
            for (i in 0 until n) if (edge[i] >= lo) cnt[cell(lab, i)]++
        }
        var best = 0
        for (k in 1 until 256) if (cnt[k] > cnt[best]) best = k
        val dom = FloatArray(3)
        var sn = 0
        for (i in 0 until n) {
            val e = edge[i]
            val inPool = if (useRing) (e >= lo && e < ringEnd) else (e >= lo)
            if (inPool && cell(lab, i) == best) {
                dom[0] += lab[3 * i]
                dom[1] += lab[3 * i + 1]
                dom[2] += lab[3 * i + 2]
                sn++
            }
        }
        if (sn > 0) for (k in 0 until 3) dom[k] = dom[k] / sn

        val de = FloatArray(n)
        val dh = IntArray(256)
        var total = 0
        for (i in 0 until n) {
            val v = labDist(lab, i, dom)
            de[i] = v
            if (edge[i] >= lo) {
                dh[v.toInt().coerceIn(0, 255)]++
                total++
            }
        }
        var acc = 0
        var bin = 0
        val target = (total * 0.85f).toInt()
        while (bin < 255 && acc + dh[bin] < target) {
            acc += dh[bin]
            bin++
        }
        val hi = max(bin.toFloat(), 25f)

        return FloatArray(n) {
            var t = smooth(0.12f, 0.85f, de[it] / hi)
            if (frame.found) t *= frameFactor(edge[it], w)
            t
        }
    }

    /** Centers the glyph and scales it to the size of Pixel themed icons. */
    private fun fitGlyph(mask: FloatArray, size: Int): FloatArray {
        var minX = size
        var minY = size
        var maxX = -1
        var maxY = -1
        for (y in 0 until size) {
            for (x in 0 until size) {
                if (mask[y * size + x] > 0.5f) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        if (maxX < 0) return mask

        val w = (maxX - minX + 1).toFloat()
        val h = (maxY - minY + 1).toFloat()
        val target = size * 42f / 108f
        val s = (target / max(w, h)).coerceIn(0.6f, 1.6f)
        val gx = (minX + maxX + 1) / 2f
        val gy = (minY + maxY + 1) / 2f
        val center = size / 2f

        val out = FloatArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                out[y * size + x] = sample(
                    mask, size,
                    gx + (x + 0.5f - center) / s,
                    gy + (y + 0.5f - center) / s,
                )
            }
        }
        return out
    }

    private fun sample(m: FloatArray, size: Int, fx: Float, fy: Float): Float {
        val x = fx - 0.5f
        val y = fy - 0.5f
        val x0 = floor(x).toInt()
        val y0 = floor(y).toInt()
        val tx = x - x0
        val ty = y - y0
        fun at(ix: Int, iy: Int): Float =
            if (ix < 0 || iy < 0 || ix >= size || iy >= size) 0f else m[iy * size + ix]
        val top = at(x0, y0) * (1 - tx) + at(x0 + 1, y0) * tx
        val bottom = at(x0, y0 + 1) * (1 - tx) + at(x0 + 1, y0 + 1) * tx
        return top * (1 - ty) + bottom * ty
    }

    /** A monochrome layer is unusable when it is a solid fill of most of the icon. */
    private fun monoUsable(m: IntArray, size: Int): Boolean {
        val inset = size * 18 / 108
        var on = 0
        var total = 0
        for (y in inset until size - inset step 2) {
            for (x in inset until size - inset step 2) {
                total++
                if (Color.alpha(m[y * size + x]) > 128) on++
            }
        }
        if (total == 0) return false
        return (on.toFloat() / total) in 0.01f..0.55f
    }

    private fun opaqueEdgeColor(px: IntArray, size: Int): Int {
        val inset = size * 18 / 108
        val last = size - 1 - inset
        val band = max(3, ((size - 2 * inset) * 0.1f).toInt())
        var r = 0L
        var g = 0L
        var b = 0L
        var cnt = 0
        for (y in inset..last) {
            for (x in inset..last) {
                val e = minOf(minOf(x - inset, y - inset), minOf(last - x, last - y))
                if (e >= band) continue
                val p = px[y * size + x]
                if (Color.alpha(p) > 200) {
                    r += Color.red(p)
                    g += Color.green(p)
                    b += Color.blue(p)
                    cnt++
                }
            }
        }
        if (cnt == 0) return Color.WHITE
        return Color.rgb((r / cnt).toInt(), (g / cnt).toInt(), (b / cnt).toInt())
    }

    private fun luma(p: Int): Int =
        (Color.red(p) * 54 + Color.green(p) * 183 + Color.blue(p) * 19) shr 8

    private fun layerBitmap(d: Drawable, size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, size, size)
        d.draw(Canvas(bmp))
        return bmp
    }

    private fun opaqueShare(layer: Bitmap, size: Int): Float {
        val px = IntArray(size * size)
        layer.getPixels(px, 0, size, 0, 0, size, size)
        val inset = size * 18 / 108
        var on = 0
        var total = 0
        for (y in inset until size - inset step 3) {
            for (x in inset until size - inset step 3) {
                total++
                if (Color.alpha(px[y * size + x]) > 230) on++
            }
        }
        return if (total == 0) 0f else on.toFloat() / total
    }

    private fun ringColor(px: IntArray, size: Int): Int {
        val a = size * 18 / 108 + size / 50
        val b = size - 1 - a
        var r = 0L
        var g = 0L
        var bl = 0L
        var n = 0
        for (i in a..b) {
            for (p in intArrayOf(px[a * size + i], px[b * size + i], px[i * size + a], px[i * size + b])) {
                r += Color.red(p)
                g += Color.green(p)
                bl += Color.blue(p)
                n++
            }
        }
        return Color.rgb((r / n).toInt(), (g / n).toInt(), (bl / n).toInt())
    }

    private fun smooth(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}

/** Cache of finished icons, so scrolling and tab changes do not recompute them. */
object IconCache {
    private val cache = android.util.LruCache<String, Bitmap>(160)
    fun get(key: String): Bitmap? = cache.get(key)
    fun put(key: String, bmp: Bitmap) {
        cache.put(key, bmp)
    }
}
