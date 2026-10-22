//! Pixel engine of the glass look: two-color picture with depth, refraction, rim band, sheen.
//! Pixels are ARGB u32 (not premultiplied), masks and coverage are f32 in 0..1, canvases are v x v.


const DIR_X: f32 = -0.5609;
const DIR_Y: f32 = -0.8279;
const FALLOFF: f32 = 2.2;
const DISPERSION: f32 = 0.08;

fn box_h(s: &[f32], o: &mut [f32], v: usize, r: usize) {
    let inv = 1.0 / (2 * r + 1) as f32;
    let last = v as isize - 1;
    for y in 0..v {
        let row = y * v;
        let mut sum = 0.0f32;
        for k in -(r as isize)..=(r as isize) {
            sum += s[row + k.clamp(0, last) as usize];
        }
        for x in 0..v {
            o[row + x] = sum * inv;
            sum += s[row + (x + r + 1).min(v - 1)] - s[row + x.saturating_sub(r)];
        }
    }
}

fn box_v(s: &[f32], o: &mut [f32], v: usize, r: usize) {
    let inv = 1.0 / (2 * r + 1) as f32;
    let last = v as isize - 1;
    for x in 0..v {
        let mut sum = 0.0f32;
        for k in -(r as isize)..=(r as isize) {
            sum += s[k.clamp(0, last) as usize * v + x];
        }
        for y in 0..v {
            o[y * v + x] = sum * inv;
            sum += s[(y + r + 1).min(v - 1) * v + x] - s[y.saturating_sub(r) * v + x];
        }
    }
}

/// Gaussian approximation: three box-blur passes using running sums.
fn blur(src: &[f32], v: usize, sigma: f32) -> Vec<f32> {
    let r = (((4.0 * sigma * sigma + 1.0).sqrt() - 1.0) / 2.0).round().max(1.0) as usize;
    let mut a = src.to_vec();
    let mut tmp = vec![0.0f32; v * v];
    let mut out = vec![0.0f32; v * v];
    for _ in 0..3 {
        box_h(&a, &mut tmp, v, r);
        box_v(&tmp, &mut out, v, r);
        std::mem::swap(&mut a, &mut out);
    }
    a
}

/// 1-D squared distance transform (Felzenszwalb & Huttenlocher), lower envelope of parabolas.
fn dt1d(f: &[f64], d: &mut [f64], v: &mut [usize], z: &mut [f64]) {
    let n = f.len();
    let mut k = 0usize;
    v[0] = 0;
    z[0] = f64::NEG_INFINITY;
    z[1] = f64::INFINITY;
    for q in 1..n {
        loop {
            let p = v[k];
            let s = ((f[q] + (q * q) as f64) - (f[p] + (p * p) as f64)) / (2.0 * (q as f64 - p as f64));
            if s <= z[k] {
                k -= 1;
            } else {
                k += 1;
                v[k] = q;
                z[k] = s;
                z[k + 1] = f64::INFINITY;
                break;
            }
        }
    }
    k = 0;
    for q in 0..n {
        while z[k + 1] < q as f64 {
            k += 1;
        }
        let dq = q as f64 - v[k] as f64;
        d[q] = dq * dq + f[v[k]];
    }
}

/// Exact Euclidean distance in pixels from each inside point to the shape edge (0 outside).
/// The canvas border counts as outside: shapes touch the canvas edge, and without this the rim
/// band vanished in the middle of every side (the old chamfer metric had the same flaw).
fn distance_inside(a: &[f32], v: usize) -> Vec<f32> {
    const INF: f64 = 1e12;
    let n = v + 2;
    let mut g = vec![0.0f64; n * n];
    for y in 0..v {
        for x in 0..v {
            if a[y * v + x] > 0.5 {
                g[(y + 1) * n + x + 1] = INF;
            }
        }
    }
    let mut buf = vec![0.0f64; n];
    let mut out = vec![0.0f64; n];
    let mut vs = vec![0usize; n];
    let mut zs = vec![0.0f64; n + 1];
    for x in 0..n {
        for y in 0..n {
            buf[y] = g[y * n + x];
        }
        dt1d(&buf, &mut out, &mut vs, &mut zs);
        for y in 0..n {
            g[y * n + x] = out[y];
        }
    }
    for y in 0..n {
        buf.copy_from_slice(&g[y * n..(y + 1) * n]);
        dt1d(&buf, &mut out, &mut vs, &mut zs);
        g[y * n..(y + 1) * n].copy_from_slice(&out);
    }
    let mut d = vec![0.0f32; v * v];
    for y in 0..v {
        for x in 0..v {
            let sq = g[(y + 1) * n + x + 1];
            if sq > 0.0 {
                d[y * v + x] = (sq.sqrt() - 0.5).max(0.0) as f32;
            }
        }
    }
    d
}

/// One channel (bit shift 16, 8 or 0) with bilinear interpolation.
fn sample(px: &[u32], v: usize, shift: u32, fx: f32, fy: f32) -> f32 {
    let lim = v as f32 - 1.001;
    let x = fx.clamp(0.0, lim);
    let y = fy.clamp(0.0, lim);
    let (x0, y0) = (x as usize, y as usize);
    let (tx, ty) = (x - x0 as f32, y - y0 as f32);
    let c = |ix: usize, iy: usize| ((px[iy * v + ix] >> shift) & 0xFF) as f32;
    let top = c(x0, y0) * (1.0 - tx) + c(x0 + 1, y0) * tx;
    let bottom = c(x0, y0 + 1) * (1.0 - tx) + c(x0 + 1, y0 + 1) * tx;
    top * (1.0 - ty) + bottom * ty
}

fn ch(c: u32, shift: u32) -> f32 {
    ((c >> shift) & 0xFF) as f32
}

/// Two-color picture with depth: soft shadow under the glyph, highlight on its upper edge.
pub fn duo_content(t: &[f32], v: usize, bg: u32, fg: u32) -> Vec<u32> {
    let blurred = blur(t, v, 1.5);
    let sx = (0.015 * v as f32) as usize;
    let sy = (0.025 * v as f32) as usize;
    let mut shifted = vec![0.0f32; v * v];
    for y in sy..v {
        for x in sx..v {
            shifted[y * v + x] = t[(y - sy) * v + x - sx];
        }
    }
    let shadow = blur(&shifted, v, 4.0);
    let bgc = [ch(bg, 16), ch(bg, 8), ch(bg, 0)];
    let fgc = [ch(fg, 16), ch(fg, 8), ch(fg, 0)];
    let vf = v as f32;
    let mut out = vec![0u32; v * v];
    for y in 0..v {
        let top = 1.0 - y as f32 / vf;
        for x in 0..v {
            let i = y * v + x;
            let tt = t[i];
            let diag = (x + y) as f32 / (2.0 * vf);
            let sh = 1.0 - 0.32 * shadow[i] * (1.0 - tt);
            let gx = (blurred[y * v + (x + 1).min(v - 1)] - blurred[y * v + x.saturating_sub(1)]) / 2.0;
            let gy = (blurred[(y + 1).min(v - 1) * v + x] - blurred[y.saturating_sub(1) * v + x]) / 2.0;
            let emb = (0.42 * gx + 0.62 * gy) * 5.0;
            let lit = emb.clamp(0.0, 1.0) * 0.55 * 255.0;
            let dim = (-emb).clamp(0.0, 1.0) * 0.45;
            let mut rgb = 0u32;
            for c in 0..3 {
                let b = bgc[c] + (255.0 - bgc[c]) * 0.14 * (1.0 - diag) - bgc[c] * 0.22 * diag;
                let f = fgc[c] + (255.0 - fgc[c]) * 0.18 * top;
                let mut p = b * sh * (1.0 - tt) + f * tt;
                p += lit - p * dim;
                rgb = (rgb << 8) | (p as i32).clamp(0, 255) as u32;
            }
            out[i] = 0xFF00_0000 | rgb;
        }
    }
    out
}

/// Fills pixels outside the shape (alpha <= 0.5) with the colors of the nearest inside pixels,
/// so refraction and anti-aliased edges never pick up transparent black.
fn bleed_colors(content: &[u32], alpha: &[f32], v: usize) -> Vec<u32> {
    let mut px = content.to_vec();
    let mut known: Vec<bool> = alpha.iter().map(|&a| a > 0.5).collect();
    if known.iter().all(|&k| k) {
        return px;
    }
    for _ in 0..(v / 6).clamp(6, 40) {
        let mut next = known.clone();
        let mut changed = false;
        for y in 0..v {
            for x in 0..v {
                let i = y * v + x;
                if known[i] {
                    continue;
                }
                let (mut r, mut g, mut b, mut c) = (0u32, 0u32, 0u32, 0u32);
                for dy in -1i32..=1 {
                    for dx in -1i32..=1 {
                        let (nx, ny) = (x as i32 + dx, y as i32 + dy);
                        if nx < 0 || ny < 0 || nx >= v as i32 || ny >= v as i32 {
                            continue;
                        }
                        let j = ny as usize * v + nx as usize;
                        if known[j] {
                            r += (px[j] >> 16) & 0xFF;
                            g += (px[j] >> 8) & 0xFF;
                            b += px[j] & 0xFF;
                            c += 1;
                        }
                    }
                }
                if c > 0 {
                    px[i] = 0xFF00_0000 | ((r / c) << 16) | ((g / c) << 8) | (b / c);
                    next[i] = true;
                    changed = true;
                }
            }
        }
        known = next;
        if !changed {
            break;
        }
    }
    px
}

/// Glass optics over [content] cut by the shape coverage [alpha].
pub fn render(content: &[u32], alpha: &[f32], v: usize, strength: f32) -> Vec<u32> {
    let n = v * v;
    let d = distance_inside(alpha, v);
    let content = &bleed_colors(content, alpha, v)[..];
    // Small shapes (a logo on a plate) get a thinner bevel and rim, so the glass does not swallow them.
    let thick = d.iter().cloned().fold(0.0f32, f32::max);
    let sc = (thick / (0.30 * v as f32)).clamp(0.3, 1.0);
    let dn = blur(&d, v, (v as f32 * 0.012).max(1.5));
    let k = strength.clamp(0.0, 1.5);
    let vf = v as f32;
    let bevel = (0.11 * vf * sc).max(2.0);
    let refr = bevel * (0.45 + 0.75 * k);
    let g_b = 5f32.powf(-FALLOFF);
    let band_w = 0.085 * vf * sc;
    let band_in = band_w * 0.35;
    let hi = vf - 1.0;
    let mut out = vec![0u32; n];

    for y in 0..v {
        let ym = y.saturating_sub(1);
        let yp = (y + 1).min(v - 1);
        for x in 0..v {
            let i = y * v + x;
            let cov = alpha[i];
            if cov <= 0.004 {
                continue;
            }
            let d_in = d[i];

            let mut nx = -(dn[y * v + (x + 1).min(v - 1)] - dn[y * v + x.saturating_sub(1)]);
            let mut ny = -(dn[yp * v + x] - dn[ym * v + x]);
            let nl = (nx * nx + ny * ny).sqrt();
            if nl > 1e-4 {
                nx /= nl;
                ny /= nl;
            } else {
                nx = 0.0;
                ny = -1.0;
            }

            let mut px = [0.0f32; 3];
            if d_in < bevel {
                let tt = d_in.max(0.0) / bevel;
                let slope = ((1.0 + 4.0 * tt).powf(-FALLOFF) - g_b) / (1.0 - g_b);
                let ox = -nx * slope * refr;
                let oy = -ny * slope * refr;
                let fr = 1.0 - DISPERSION * slope;
                let fb = 1.0 + DISPERSION * slope;
                let (xf, yf) = (x as f32, y as f32);
                px[0] = sample(content, v, 16, (xf + ox * fr).clamp(0.0, hi), (yf + oy * fr).clamp(0.0, hi));
                px[1] = sample(content, v, 8, (xf + ox).clamp(0.0, hi), (yf + oy).clamp(0.0, hi));
                px[2] = sample(content, v, 0, (xf + ox * fb).clamp(0.0, hi), (yf + oy * fb).clamp(0.0, hi));
            } else {
                let p = content[i];
                px = [ch(p, 16), ch(p, 8), ch(p, 0)];
            }

            let facing = nx * DIR_X + ny * DIR_Y;
            let lit = facing.max(0.0);
            let away = (-facing).max(0.0);
            let s = (((x + y) as f32 / (2.0 * vf) - 0.25) / 0.75).clamp(0.0, 1.0);
            let sheen = 0.30 * 0.5 * (s + s * s) * k;
            // Soft gloss on the upper part of the tile, fades out by the middle.
            let gloss = 0.11 * k * (1.0 - (y as f32 / (0.55 * vf)).min(1.0)).powi(2);
            let mut band = (d_in / band_in).clamp(0.0, 1.0) * (1.0 - (d_in - band_in) / (band_w * 0.65)).clamp(0.0, 1.0);
            if band > 0.0 {
                band = band.powf(0.8);
            }
            // Light from the top-left: bright rim on the lit side, a softer reflected rim opposite.
            let band_a = band * (0.14 + 0.20 * away + 0.30 * lit) * k;
            let hair = (1.0 - (d_in - 1.2).abs() / 1.6).clamp(0.0, 1.0) * 0.28 * k;
            let inner_edge = (1.0 - (d_in - band_w).abs() / (0.02 * vf)).clamp(0.0, 1.0);
            let caustic = (1.0 - (d_in - band_w * 1.6).abs() / (0.03 * vf)).clamp(0.0, 1.0) * away * away * 0.22 * k;

            let mut rgb = 0u32;
            for c in 0..3 {
                let mut p = px[c];
                p += (255.0 - p) * sheen;
                p += (255.0 - p) * gloss;
                p += (255.0 - p) * band_a;
                p += (255.0 - p) * hair;
                p *= 1.0 - 0.03 * k * inner_edge;
                p += (255.0 - p) * caustic;
                rgb = (rgb << 8) | ((p + 0.5) as i32).clamp(0, 255) as u32;
            }
            out[i] = (((cov * 255.0 + 0.5) as u32) << 24) | rgb;
        }
    }
    out
}


#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn distance_counts_canvas_border_as_outside() {
        // A shape that fills the whole canvas must still have a rim along every side.
        let v = 40;
        let a = vec![1.0f32; v * v];
        let d = distance_inside(&a, v);
        assert!((d[20 * v + 0] - 0.5).abs() < 1e-4);
        assert!((d[20 * v + 5] - 5.5).abs() < 1e-3, "{}", d[20 * v + 5]);
        assert!((d[0 * v + 20] - 0.5).abs() < 1e-4);
    }

    #[test]
    fn distance_is_euclidean_on_diagonals() {
        let v = 41;
        let mut a = vec![0.0f32; v * v];
        for y in 5..36 {
            for x in 5..36 {
                a[y * v + x] = 1.0;
            }
        }
        let d = distance_inside(&a, v);
        // Center is 15 px from every side.
        assert!((d[20 * v + 20] - 15.5).abs() < 0.6, "{}", d[20 * v + 20]);
    }

    #[test]
    fn logo_on_plate_keeps_its_colors() {
        // A small opaque red disc on transparent black: the rim must not pull in black.
        let v = 96;
        let mut alpha = vec![0.0f32; v * v];
        let mut content = vec![0u32; v * v];
        for y in 0..v {
            for x in 0..v {
                let (dx, dy) = (x as f32 - 47.5, y as f32 - 47.5);
                if dx * dx + dy * dy < 30.0 * 30.0 {
                    alpha[y * v + x] = 1.0;
                    content[y * v + x] = 0xFFD03020;
                }
            }
        }
        let out = render(&content, &alpha, v, 0.85);
        let px = out[48 * v + 20 + 3]; // a few px inside the left edge
        let (r, g, b) = ((px >> 16) & 0xFF, (px >> 8) & 0xFF, px & 0xFF);
        assert!(r > 150 && g < 140 && b < 130, "dark or washed rim: {r} {g} {b}");
    }

    #[test]
    fn render_keeps_alpha_and_is_deterministic() {
        let v = 64;
        let alpha = vec![1.0f32; v * v];
        let content = vec![0xFF336699u32; v * v];
        let a = render(&content, &alpha, v, 0.85);
        let b = render(&content, &alpha, v, 0.85);
        assert_eq!(a, b);
        assert!(a.iter().all(|p| p >> 24 == 0xFF));
    }
}
