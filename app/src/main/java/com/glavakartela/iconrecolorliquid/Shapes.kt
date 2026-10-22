package com.glavakartela.iconrecolorliquid

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Icon shape. q == null means the launcher decides; otherwise q holds 24 values 0..63:
 * the distance from the center to the edge in 24 directions, 15 degrees apart.
 */
data class IconShape(val name: String, val q: List<Int>?) {
    val isLauncher: Boolean get() = q == null
    val code: String? get() = q?.let { ShapeCodec.encode(it) }

    /**
     * Name for the UI in the current language. Built-in shapes are stored under stable keys,
     * custom ones as "Custom N" / "Imported N" (older versions saved "Своя N" / "Импорт N").
     */
    fun label(ctx: android.content.Context): String {
        val res = when (name) {
            "launcher", "Лаунчер" -> R.string.shape_launcher
            "circle", "Круг" -> R.string.shape_circle
            "squircle", "Сквиркл" -> R.string.shape_squircle
            "diamond", "Ромб" -> R.string.shape_diamond
            "hexagon", "Шестиугольник" -> R.string.shape_hexagon
            "octagon", "Восьмиугольник" -> R.string.shape_octagon
            "flower", "Цветок" -> R.string.shape_flower
            "star", "Звезда" -> R.string.shape_star
            "clover", "Клевер" -> R.string.shape_clover
            "square", "Квадрат" -> R.string.shape_square
            else -> 0
        }
        if (res != 0) return ctx.getString(res)
        val m = Regex("^(Custom|Imported|Своя|Импорт)(?: (\\d+))?$").find(name) ?: return name
        val kind = if (m.groupValues[1] == "Custom" || m.groupValues[1] == "Своя") {
            ctx.getString(R.string.shape_custom)
        } else {
            ctx.getString(R.string.shape_imported)
        }
        val n = m.groupValues[2]
        return if (n.isEmpty()) kind else "$kind $n"
    }
}

/** Shape code: "icrec-" plus 25 characters (24 shape values and a check character). */
object ShapeCodec {
    const val PREFIX = "icrec-"
    const val N = 24
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    private fun check(q: List<Int>): Int {
        var s = 0
        for (i in q.indices) s = (s + q[i] * (i + 1)) % 64
        return s
    }

    fun encode(q: List<Int>): String {
        val sb = StringBuilder(PREFIX)
        for (v in q) sb.append(ALPHABET[v.coerceIn(0, 63)])
        sb.append(ALPHABET[check(q)])
        return sb.toString()
    }

    /** Returns null when the code is invalid or corrupted. */
    fun decode(text: String): List<Int>? {
        val t = text.trim()
        if (!t.startsWith(PREFIX, ignoreCase = true)) return null
        val body = t.substring(PREFIX.length)
        if (body.length != N + 1) return null
        val vals = ArrayList<Int>(N + 1)
        for (ch in body) {
            val i = ALPHABET.indexOf(ch)
            if (i < 0) return null
            vals.add(i)
        }
        val q = vals.subList(0, N).toList()
        return if (check(q) == vals[N]) q else null
    }
}

/** Built-in shapes and helpers for turning outlines into shape values. */
object IconShapes {
    const val R_MIN = 0.15f
    const val R_MAX = 1.45f

    val LAUNCHER = IconShape("launcher", null)

    fun radius(q: Int): Float = R_MIN + (R_MAX - R_MIN) * q.coerceIn(0, 63) / 63f

    fun quantize(r: Float): Int =
        (((r - R_MIN) / (R_MAX - R_MIN)) * 63f).roundToInt().coerceIn(0, 63)

    /** Largest radius in direction theta that keeps the shape inside the square of side 2. */
    fun squareLimit(theta: Double): Double = 1.0 / max(abs(cos(theta)), abs(sin(theta)))

    private fun profile(f: (Double) -> Double): List<Int> =
        List(ShapeCodec.N) { i -> quantize(f(2.0 * PI * i / ShapeCodec.N).toFloat()) }

    private fun polygon(th: Double, sides: Int): Double {
        val seg = 2.0 * PI / sides
        var m = th % seg
        if (m < 0) m += seg
        return cos(PI / sides) / cos(m - PI / sides)
    }

    val presets: List<IconShape> by lazy {
        listOf(
            IconShape("circle", profile { 1.0 }),
            IconShape("squircle", profile { th -> (abs(cos(th)).pow(4.0) + abs(sin(th)).pow(4.0)).pow(-0.25) }),
            IconShape("diamond", profile { th -> 1.0 / (abs(cos(th)) + abs(sin(th))) }),
            IconShape("hexagon", profile { th -> polygon(th, 6) }),
            IconShape("octagon", profile { th -> polygon(th, 8) }),
            IconShape("flower", profile { th -> 0.82 + 0.18 * cos(8 * th) }),
            IconShape("star", profile { th -> 0.55 + 0.45 * (0.5 + 0.5 * cos(5 * th)).pow(1.5) }),
            IconShape("clover", profile { th -> 0.74 + 0.26 * cos(4 * th) }),
            IconShape("square", profile { th -> (abs(cos(th)).pow(8.0) + abs(sin(th)).pow(8.0)).pow(-0.125) }),
        )
    }

    val squircle: IconShape get() = presets[1]

    fun radii(q: List<Int>, count: Int): FloatArray {
        val n = q.size
        val r = FloatArray(n) { radius(q[it]) }
        return FloatArray(count) { k ->
            val pos = k.toFloat() * n / count
            val i = pos.toInt()
            val t = pos - i
            catmull(r[(i - 1 + n) % n], r[i % n], r[(i + 1) % n], r[(i + 2) % n], t).coerceIn(R_MIN, R_MAX)
        }
    }

    fun outline(q: List<Int>, count: Int = 240): FloatArray {
        val n = q.size
        val r = FloatArray(n) { radius(q[it]) }
        val out = FloatArray(count * 2)
        for (k in 0 until count) {
            val pos = k.toFloat() * n / count
            val i = pos.toInt()
            val t = pos - i
            val rr = catmull(
                r[(i - 1 + n) % n], r[i % n], r[(i + 1) % n], r[(i + 2) % n], t,
            ).coerceIn(R_MIN, R_MAX)
            val th = 2.0 * PI * k / count
            out[2 * k] = (rr * cos(th)).toFloat()
            out[2 * k + 1] = (rr * sin(th)).toFloat()
        }
        return out
    }

    private fun catmull(p0: Float, p1: Float, p2: Float, p3: Float, t: Float): Float {
        val t2 = t * t
        val t3 = t2 * t
        return 0.5f * (
            2f * p1 + (-p0 + p2) * t +
                (2f * p0 - 5f * p1 + 4f * p2 - p3) * t2 +
                (-p0 + 3f * p1 - 3f * p2 + p3) * t3
            )
    }

    fun fromStroke(pts: List<Pair<Float, Float>>): List<Int>? {
        if (pts.size < 8) return null

        val dense = ArrayList<Pair<Float, Float>>()
        for (i in 0 until pts.size - 1) {
            val (x0, y0) = pts[i]
            val (x1, y1) = pts[i + 1]
            val steps = max(1, (hypot(x1 - x0, y1 - y0) / 2f).toInt())
            for (s in 0 until steps) {
                val t = s.toFloat() / steps
                dense.add(Pair(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t))
            }
        }
        dense.add(pts.last())

        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for ((x, y) in dense) {
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
        }
        if (maxX - minX < 8f || maxY - minY < 8f) return null
        val cx = (minX + maxX) / 2f
        val cy = (minY + maxY) / 2f

        val n = ShapeCodec.N
        val rad = FloatArray(n)
        for ((x, y) in dense) {
            val dx = x - cx
            val dy = y - cy
            val th = atan2(dy, dx)
            var bin = (th / (2f * PI.toFloat()) * n).roundToInt()
            bin = ((bin % n) + n) % n
            rad[bin] = max(rad[bin], hypot(dx, dy))
        }
        if (rad.all { it <= 0f }) return null

        val filled = rad.copyOf()
        for (i in 0 until n) {
            if (rad[i] > 0f) continue
            var a = 1
            while (rad[(i - a + n) % n] <= 0f) a++
            var b = 1
            while (rad[(i + b) % n] <= 0f) b++
            val ra = rad[(i - a + n) % n]
            val rb = rad[(i + b) % n]
            filled[i] = (ra * b + rb * a) / (a + b)
        }

        val sm = FloatArray(n) {
            0.25f * filled[(it - 1 + n) % n] + 0.5f * filled[it] + 0.25f * filled[(it + 1) % n]
        }

        var s = Float.MAX_VALUE
        for (i in 0 until n) {
            val th = 2.0 * PI * i / n
            s = min(s, (squareLimit(th) / max(sm[i], 1e-3f)).toFloat())
        }
        return List(n) { quantize(sm[it] * s) }
    }
}
