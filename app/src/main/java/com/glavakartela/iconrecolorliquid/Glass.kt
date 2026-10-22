package com.glavakartela.iconrecolorliquid

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint

/**
 * "Liquid glass" icon renderer. The pixel work (refraction, rim band, sheen, two-color picture)
 * is done by the Rust engine in rust/iconcore; this object only prepares inputs and wraps the result.
 *
 * The tile behaves like a thick drop of glass: the picture is refracted and slightly dispersed
 * near the edge (lens profile after QWEA0/Liquid-Glass-Android, MIT), a translucent light band
 * runs along the rim, and a diagonal sheen lightens the lower-right. Light comes from the top-left.
 */
object Glass {
    private val shapeAlphaCache = android.util.LruCache<String, FloatArray>(16)

    /** Glass icon from two colors and a glyph mask (mask is size x size, only the visible 72/108 part is used). */
    fun duo(mask: FloatArray, size: Int, bg: Int, fg: Int, shape: IconShape, strength: Float): Bitmap {
        val inset = size * 18 / 108
        val v = size - 2 * inset
        val t = FloatArray(v * v)
        for (y in 0 until v) System.arraycopy(mask, (y + inset) * size + inset, t, y * v, v)
        return render(NativeCore.duoContent(t, v, bg, fg), v, shape, strength)
    }

    /** Glass over the app's original icon (opaque size x size canvas): colors are kept as they are. */
    fun overOriginal(canvas: Bitmap, shape: IconShape, strength: Float): Bitmap {
        val size = canvas.width
        val inset = size * 18 / 108
        val v = size - 2 * inset
        val px = IntArray(v * v)
        canvas.getPixels(px, 0, v, inset, inset, v, v)
        return render(px, v, shape, strength)
    }

    /**
     * Glass on the app's logo only. [plate] is the opaque background (it stays flat, just cut to the shape),
     * [logo] is the foreground layer with its own alpha: it gets the rim, refraction and sheen.
     * An icon whose foreground fills the whole tile has no separate logo, so the whole tile gets the glass.
     */
    fun overLogo(plate: Bitmap, logo: Bitmap, shape: IconShape, strength: Float): Bitmap {
        val size = plate.width
        val inset = size * 18 / 108
        val v = size - 2 * inset
        val px = IntArray(v * v)
        logo.getPixels(px, 0, v, inset, inset, v, v)
        var solid = 0
        for (p in px) if ((p ushr 24) > 242) solid++
        if (solid > 0.9f * v * v) {
            val whole = plate.copy(Bitmap.Config.ARGB_8888, true)
            Canvas(whole).drawBitmap(logo, 0f, 0f, null)
            return overOriginal(whole, shape, strength)
        }
        val alpha = FloatArray(v * v) { (px[it] ushr 24) / 255f }
        val content = IntArray(v * v) { px[it] or (0xFF shl 24) }
        val out = Recolor.masked(plate, shape)
        val c = Canvas(out)
        c.drawBitmap(renderAlpha(content, alpha, v, strength), 0f, 0f, null)
        return out
    }

    private fun renderAlpha(content: IntArray, alpha: FloatArray, v: Int, strength: Float): Bitmap {
        val out = NativeCore.render(content, alpha, v, strength)
        val bmp = Bitmap.createBitmap(v, v, Bitmap.Config.ARGB_8888)
        bmp.setPixels(out, 0, v, 0, 0, v, v)
        return bmp
    }

    private fun render(content: IntArray, v: Int, shape: IconShape, strength: Float): Bitmap {
        val out = NativeCore.render(content, shapeAlpha(shape, v), v, strength)
        val bmp = Bitmap.createBitmap(v, v, Bitmap.Config.ARGB_8888)
        bmp.setPixels(out, 0, v, 0, 0, v, v)
        return bmp
    }

    /** Shape coverage 0..1 on a v x v canvas (anti-aliasing comes from Canvas), cached per shape. */
    private fun shapeAlpha(shape: IconShape, v: Int): FloatArray {
        val key = "${shape.code ?: "L"}|$v"
        shapeAlphaCache.get(key)?.let { return it }
        val bmp = Bitmap.createBitmap(v, v, Bitmap.Config.ARGB_8888)
        val q = shape.q ?: IconShapes.squircle.q!!
        Canvas(bmp).drawPath(Recolor.shapePath(q, v), Paint(Paint.ANTI_ALIAS_FLAG))
        val px = IntArray(v * v)
        bmp.getPixels(px, 0, v, 0, 0, v, v)
        bmp.recycle()
        val out = FloatArray(v * v) { (px[it] ushr 24) / 255f }
        shapeAlphaCache.put(key, out)
        return out
    }
}

/** JNI bridge to the Rust engine (libiconcore.so). The library is required: there is no Kotlin fallback. */
object NativeCore {
    init {
        System.loadLibrary("iconcore")
    }

    private external fun nativeDuoContent(mask: FloatArray, v: Int, bg: Int, fg: Int, out: IntArray)
    private external fun nativeRender(content: IntArray, alpha: FloatArray, v: Int, strength: Float, out: IntArray)

    private external fun nativeThumbNew(count: Int, selected: Int): Int
    private external fun nativeThumbFree(id: Int)
    private external fun nativeThumbSelect(id: Int, idx: Int)
    private external fun nativeThumbPress(id: Int, pressed: Boolean)
    private external fun nativeThumbDrag(id: Int, center: Float)
    private external fun nativeThumbRelease(id: Int): Int
    private external fun nativeThumbStep(id: Int, dt: Float, out: FloatArray)

    internal fun thumbNew(count: Int, selected: Int): Int = nativeThumbNew(count, selected)
    internal fun thumbFree(id: Int) = nativeThumbFree(id)
    internal fun thumbSelect(id: Int, idx: Int) = nativeThumbSelect(id, idx)
    internal fun thumbPress(id: Int, pressed: Boolean) = nativeThumbPress(id, pressed)
    internal fun thumbDrag(id: Int, center: Float) = nativeThumbDrag(id, center)
    internal fun thumbRelease(id: Int): Int = nativeThumbRelease(id)
    internal fun thumbStep(id: Int, dt: Float, out: FloatArray) = nativeThumbStep(id, dt, out)

    fun duoContent(mask: FloatArray, v: Int, bg: Int, fg: Int): IntArray =
        IntArray(v * v).also { nativeDuoContent(mask, v, bg, fg, it) }

    fun render(content: IntArray, alpha: FloatArray, v: Int, strength: Float): IntArray =
        IntArray(v * v).also { nativeRender(content, alpha, v, strength, it) }
}

/**
 * Spring engine of the liquid thumb (rust/iconcore/src/motion.rs). One instance per switcher.
 * [state] holds the last result of [step]: see the S_* indices below. Positions are in items.
 */
class ThumbEngine(count: Int, selected: Int) {
    private val id = NativeCore.thumbNew(count, selected)
    val state = FloatArray(STATE_LEN).also {
        it[S_LEFT] = selected.toFloat()
        it[S_RIGHT] = selected + 1f
        it[S_SCALE_Y] = 1f
        it[S_SETTLED] = 1f
        it[S_SELECTED] = selected.toFloat()
    }

    fun step(dt: Float) { if (id >= 0) NativeCore.thumbStep(id, dt, state) }
    fun select(idx: Int) { if (id >= 0) NativeCore.thumbSelect(id, idx) }
    fun press(pressed: Boolean) { if (id >= 0) NativeCore.thumbPress(id, pressed) }
    fun drag(center: Float) { if (id >= 0) NativeCore.thumbDrag(id, center) }
    fun release(): Int = if (id >= 0) NativeCore.thumbRelease(id) else 0
    fun free() { if (id >= 0) NativeCore.thumbFree(id) }

    companion object {
        const val STATE_LEN = 8
        const val S_LEFT = 0
        const val S_RIGHT = 1
        const val S_SCALE_Y = 2
        const val S_GLOW = 3
        const val S_PRESS = 4
        const val S_SPEED = 5
        const val S_SETTLED = 6
        const val S_SELECTED = 7
    }
}
