package com.glavakartela.iconrecolorliquid

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Color sources: wallpaper colors and seed-based palettes. */
object Palette {
    val NEUTRAL: Int = Color.rgb(128, 128, 128)

    fun wallpaperColors(ctx: Context): List<Int> = try {
        val wm = WallpaperManager.getInstance(ctx)
        val wc = wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
            ?: wm.getWallpaperColors(WallpaperManager.FLAG_LOCK)
        if (wc == null) emptyList() else listOf(wc.primaryColor.toArgb())
    } catch (e: Exception) {
        emptyList()
    }

    fun fromSeed(seed: Int, dark: Boolean): Pair<Int, Int> {
        val (_, chroma, hue) = Tonal.toLch(seed)
        val bgChroma = (chroma * 0.6).coerceAtMost(26.0)
        val fgChroma = (chroma * 0.4).coerceAtMost(22.0)
        return if (dark) {
            Pair(Tonal.color(22.0, bgChroma, hue), Tonal.color(90.0, fgChroma, hue))
        } else {
            Pair(Tonal.color(90.0, bgChroma, hue), Tonal.color(30.0, fgChroma, hue))
        }
    }

    fun system(ctx: Context, accent: Int, dark: Boolean): Pair<Int, Int> {
        val ids: Pair<Int, Int> = when (accent) {
            2 -> if (dark) Pair(android.R.color.system_accent2_800, android.R.color.system_accent2_100)
                 else Pair(android.R.color.system_accent2_100, android.R.color.system_accent2_700)
            3 -> if (dark) Pair(android.R.color.system_accent3_800, android.R.color.system_accent3_100)
                 else Pair(android.R.color.system_accent3_100, android.R.color.system_accent3_700)
            else -> if (dark) Pair(android.R.color.system_accent1_800, android.R.color.system_accent1_100)
                    else Pair(android.R.color.system_accent1_100, android.R.color.system_accent1_700)
        }
        return Pair(ctx.getColor(ids.first), ctx.getColor(ids.second))
    }
}

/** Picks a seed color from a wallpaper bitmap. */
object WallpaperSeed {
    fun fromBitmap(src: Bitmap): Int {
        val w = 64
        val h = max(1, src.height * w / max(1, src.width))
        val small = Bitmap.createScaledBitmap(src, w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)

        val bins = 36
        val weight = DoubleArray(bins)
        var chromatic = 0
        for (p in px) {
            val (l, c, hue) = Tonal.toLch(p)
            if (c >= 14.0 && l in 8.0..95.0) {
                chromatic++
                weight[binOf(hue, bins)] += c
            }
        }
        if (chromatic < px.size * 0.03) return Palette.NEUTRAL

        var best = 0
        var bestW = -1.0
        for (b in 0 until bins) {
            val s = weight[b] * 2 + weight[(b + bins - 1) % bins] + weight[(b + 1) % bins]
            if (s > bestW) {
                bestW = s
                best = b
            }
        }
        var sumC = 0.0
        var cnt = 0
        var sx = 0.0
        var sy = 0.0
        for (p in px) {
            val (l, c, hue) = Tonal.toLch(p)
            if (c >= 14.0 && l in 8.0..95.0) {
                val b = binOf(hue, bins)
                if (b == best || b == (best + 1) % bins || b == (best + bins - 1) % bins) {
                    sumC += c
                    cnt++
                    sx += cos(hue) * c
                    sy += sin(hue) * c
                }
            }
        }
        if (cnt == 0) return Palette.NEUTRAL
        return Tonal.color(55.0, (sumC / cnt).coerceAtMost(60.0), atan2(sy, sx))
    }

    private fun binOf(hue: Double, bins: Int): Int {
        val deg = (Math.toDegrees(hue) + 360.0) % 360.0
        return (deg / (360.0 / bins)).toInt().coerceIn(0, bins - 1)
    }
}

/** Tonal color math (Lab/LCH conversions and tone-based colors). */
object Tonal {
    private const val XN = 0.95047
    private const val YN = 1.0
    private const val ZN = 1.08883

    private fun srgbToLinear(c: Int): Double {
        val v = c / 255.0
        return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    private val LIN = DoubleArray(256) { srgbToLinear(it) }

    private fun linearToSrgb(v: Double): Int {
        val c = v.coerceIn(0.0, 1.0)
        val s = if (c <= 0.0031308) 12.92 * c else 1.055 * c.pow(1 / 2.4) - 0.055
        return (s * 255.0 + 0.5).toInt().coerceIn(0, 255)
    }

    private fun f(t: Double): Double =
        if (t > 0.008856) cbrt(t) else 7.787 * t + 16.0 / 116.0

    private fun finv(t: Double): Double {
        val t3 = t * t * t
        return if (t3 > 0.008856) t3 else (t - 16.0 / 116.0) / 7.787
    }

    fun toLch(argb: Int): Triple<Double, Double, Double> {
        val lab = FloatArray(3)
        labInto(argb, lab, 0)
        val a = lab[1].toDouble()
        val b = lab[2].toDouble()
        return Triple(lab[0].toDouble(), sqrt(a * a + b * b), atan2(b, a))
    }

    fun labInto(argb: Int, out: FloatArray, o: Int) {
        val r = LIN[Color.red(argb)]
        val g = LIN[Color.green(argb)]
        val b = LIN[Color.blue(argb)]
        val x = 0.4124564 * r + 0.3575761 * g + 0.1804375 * b
        val y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b
        val z = 0.0193339 * r + 0.1191920 * g + 0.9503041 * b
        val fx = f(x / XN)
        val fy = f(y / YN)
        val fz = f(z / ZN)
        out[o] = (116.0 * fy - 16.0).toFloat()
        out[o + 1] = (500.0 * (fx - fy)).toFloat()
        out[o + 2] = (200.0 * (fy - fz)).toFloat()
    }

    private fun labToLinear(l: Double, a: Double, b: Double): DoubleArray {
        val fy = (l + 16.0) / 116.0
        val fx = fy + a / 500.0
        val fz = fy - b / 200.0
        val x = finv(fx) * XN
        val y = finv(fy) * YN
        val z = finv(fz) * ZN
        return doubleArrayOf(
            3.2404542 * x - 1.5371385 * y - 0.4985314 * z,
            -0.9692660 * x + 1.8760108 * y + 0.0415560 * z,
            0.0556434 * x - 0.2040259 * y + 1.0572252 * z,
        )
    }

    private fun inGamut(rgb: DoubleArray): Boolean = rgb.all { it >= -0.001 && it <= 1.001 }

    fun color(tone: Double, chroma: Double, hue: Double): Int {
        val l = tone.coerceIn(0.0, 100.0)
        fun lin(c: Double) = labToLinear(l, c * cos(hue), c * sin(hue))
        var lo = 0.0
        var hi = chroma.coerceAtLeast(0.0)
        if (!inGamut(lin(hi))) {
            repeat(24) {
                val mid = (lo + hi) / 2
                if (inGamut(lin(mid))) lo = mid else hi = mid
            }
            hi = lo
        }
        val rgb = lin(hi)
        return Color.rgb(linearToSrgb(rgb[0]), linearToSrgb(rgb[1]), linearToSrgb(rgb[2]))
    }
}
