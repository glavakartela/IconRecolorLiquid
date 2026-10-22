//! Motion engine of the liquid tab thumb (the "slider" of the main menu and the segmented controls).
//!
//! The thumb is a pill with two independent edges. Each edge is its own damped spring:
//! the edge that leads the movement is stiffer, the trailing one is softer and overshoots a little.
//! This gives the stretch while travelling and the small squash on arrival that the reference video
//! shows (parameters were fitted to a frame-by-frame measurement of that video).
//!
//! Everything is in "item units": item `i` occupies `[i, i + 1]`. The UI multiplies by the item width.
//! The engine has no Android dependencies, the JNI glue lives in lib.rs.

/// Output layout of [`Thumb::state`] (`STATE_LEN` floats).
pub const STATE_LEN: usize = 8;
pub const S_LEFT: usize = 0;
pub const S_RIGHT: usize = 1;
pub const S_SCALE_Y: usize = 2;
pub const S_GLOW: usize = 3;
pub const S_PRESS: usize = 4;
pub const S_SPEED: usize = 5;
pub const S_SETTLED: usize = 6;
pub const S_SELECTED: usize = 7;

// Fitted to the reference video (30 fps capture, 3 item travel).
const LEAD_K: f32 = 250.0;
const LEAD_Z: f32 = 0.80;
const TRAIL_K: f32 = 180.0;
const TRAIL_Z: f32 = 0.70;
// While a finger holds the thumb: tight tracking, slightly softer trailing edge.
const DRAG_LEAD_K: f32 = 1400.0;
const DRAG_LEAD_Z: f32 = 0.95;
const DRAG_TRAIL_K: f32 = 700.0;
const DRAG_TRAIL_Z: f32 = 0.85;
const PRESS_K: f32 = 520.0;
const PRESS_Z: f32 = 0.70;

const MIN_WIDTH: f32 = 0.55;
const SUBSTEP: f32 = 1.0 / 480.0;
const MAX_FRAME: f32 = 1.0 / 20.0;

#[derive(Clone, Copy, Default)]
struct Spring {
    x: f32,
    v: f32,
}

impl Spring {
    fn at(x: f32) -> Self {
        Spring { x, v: 0.0 }
    }

    /// Semi-implicit Euler, stable for the stiffness range used here at 480 Hz.
    fn step(&mut self, target: f32, k: f32, zeta: f32, dt: f32) {
        let c = 2.0 * zeta * k.sqrt();
        self.v += (k * (target - self.x) - c * self.v) * dt;
        self.x += self.v * dt;
    }
}

pub struct Thumb {
    count: usize,
    selected: usize,
    left: Spring,
    right: Spring,
    press: Spring,
    glow: f32,
    pressed: bool,
    dragging: bool,
    /// Finger position as the center of the thumb, in items (0.5 = center of item 0).
    finger: f32,
    lead_right: bool,
}

fn rubber(over: f32) -> f32 {
    0.35 * (over.abs() / 0.35).tanh() * over.signum()
}

impl Thumb {
    pub fn new(count: usize, selected: usize) -> Self {
        let count = count.max(1);
        let selected = selected.min(count - 1);
        Thumb {
            count,
            selected,
            left: Spring::at(selected as f32),
            right: Spring::at(selected as f32 + 1.0),
            press: Spring::default(),
            glow: 0.0,
            pressed: false,
            dragging: false,
            finger: selected as f32 + 0.5,
            lead_right: true,
        }
    }

    pub fn set_count(&mut self, count: usize) {
        self.count = count.max(1);
        self.selected = self.selected.min(self.count - 1);
    }

    fn center(&self) -> f32 {
        0.5 * (self.left.x + self.right.x)
    }

    /// Settles on item [idx] (tap, or selection changed from outside).
    pub fn select(&mut self, idx: usize) {
        let idx = idx.min(self.count - 1);
        if idx != self.selected {
            self.lead_right = idx as f32 + 0.5 > self.center();
        }
        self.selected = idx;
    }

    pub fn set_pressed(&mut self, pressed: bool) {
        // While a finger drags the thumb it stays pressed, whatever the item underneath reports.
        if self.dragging && !pressed {
            return;
        }
        self.pressed = pressed;
    }

    /// Finger is at [center] (in items) while dragging; may go beyond the ends (rubber band).
    pub fn drag_to(&mut self, center: f32) {
        if !self.dragging {
            self.dragging = true;
            self.pressed = true;
        }
        if (center - self.finger).abs() > 1e-4 {
            self.lead_right = center > self.finger;
        }
        self.finger = center;
    }

    /// Finger lifted: returns the item to settle on (nearest to the thumb, nudged by its velocity).
    pub fn release(&mut self) -> usize {
        let c = self.center() - 0.5 + 0.12 * (self.left.v + self.right.v) * 0.5;
        let idx = (c.round().max(0.0) as usize).min(self.count - 1);
        self.dragging = false;
        self.pressed = false;
        self.lead_right = idx as f32 + 0.5 > self.center();
        self.selected = idx;
        idx
    }

    pub fn selected(&self) -> usize {
        self.selected
    }

    pub fn is_dragging(&self) -> bool {
        self.dragging
    }

    /// Advances the simulation by [dt] seconds.
    pub fn step(&mut self, dt: f32) {
        let mut left_over = dt.clamp(0.0, MAX_FRAME);
        while left_over > 1e-6 {
            let h = left_over.min(SUBSTEP);
            left_over -= h;
            self.substep(h);
        }
        let speed = 0.5 * (self.left.v + self.right.v).abs();
        let hot = if self.press.x > 0.5 || self.pressed { 1.0 } else { (speed / 2.5).min(1.0) };
        let tau = if hot > self.glow { 0.035 } else { 0.16 };
        self.glow += (hot - self.glow) * (1.0 - (-dt / tau).exp());
    }

    fn substep(&mut self, h: f32) {
        let (tl, tr, lead_k, lead_z, trail_k, trail_z);
        if self.dragging {
            let max_c = self.count as f32 - 0.5;
            let c = self.finger.clamp(0.5, max_c);
            let over = self.finger - c;
            let eff = c + rubber(over);
            // Past the ends the pill gets narrower, as if pushed against a wall.
            let half = 0.5 * (1.0 - 0.22 * (over.abs() / 0.5).tanh());
            tl = eff - half;
            tr = eff + half;
            lead_k = DRAG_LEAD_K;
            lead_z = DRAG_LEAD_Z;
            trail_k = DRAG_TRAIL_K;
            trail_z = DRAG_TRAIL_Z;
        } else {
            tl = self.selected as f32;
            tr = self.selected as f32 + 1.0;
            lead_k = LEAD_K;
            lead_z = LEAD_Z;
            trail_k = TRAIL_K;
            trail_z = TRAIL_Z;
        }
        let (lk, lz, rk, rz) = if self.lead_right {
            (trail_k, trail_z, lead_k, lead_z)
        } else {
            (lead_k, lead_z, trail_k, trail_z)
        };
        self.left.step(tl, lk, lz, h);
        self.right.step(tr, rk, rz, h);
        // Edges never cross.
        if self.right.x < self.left.x + 0.25 {
            let m = 0.5 * (self.left.x + self.right.x);
            self.left.x = m - 0.125;
            self.right.x = m + 0.125;
        }
        let pt = if self.pressed { 1.0 } else { 0.0 };
        self.press.step(pt, PRESS_K, PRESS_Z, h);
    }

    pub fn state(&self) -> [f32; STATE_LEN] {
        let press = self.press.x.clamp(-0.2, 1.4);
        let wall = self.count as f32;
        // The thumb never leaves the bar: edges that would pass an end are held at the wall,
        // so the pill gets narrower (squeezes) instead of sticking out.
        let mut r = self.right.x.min(wall);
        let mut l = self.left.x.max(0.0);
        if r - l < MIN_WIDTH {
            if self.right.x > wall - 0.5 {
                l = r - MIN_WIDTH;
            } else {
                r = l + MIN_WIDTH;
            }
        }
        let w = r - l;
        // Squeezed: slightly taller is not allowed (the bar has no room), so keep height <= 1.
        let scale_y = (1.0 - 0.10 * (1.0 - w).max(0.0) - 0.03 * press).clamp(0.82, 1.0);
        let speed = 0.5 * (self.left.v + self.right.v).abs();
        let settled = !self.dragging
            && !self.pressed
            && (self.left.x - self.selected as f32).abs() < 0.002
            && (self.right.x - self.selected as f32 - 1.0).abs() < 0.002
            && self.left.v.abs() < 0.01
            && self.right.v.abs() < 0.01
            && self.press.x.abs() < 0.002
            && self.glow < 0.01;
        [
            l,
            r,
            scale_y,
            self.glow,
            press,
            speed,
            if settled { 1.0 } else { 0.0 },
            self.selected as f32,
        ]
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn run(t: &mut Thumb, secs: f32) -> Vec<[f32; STATE_LEN]> {
        let mut out = Vec::new();
        let mut time = 0.0;
        while time < secs {
            t.step(1.0 / 120.0);
            out.push(t.state());
            time += 1.0 / 120.0;
        }
        out
    }

    #[test]
    fn settles_on_target() {
        let mut t = Thumb::new(4, 0);
        t.select(3);
        let s = run(&mut t, 2.0);
        let last = s.last().unwrap();
        assert!((last[S_LEFT] - 3.0).abs() < 1e-3, "{:?}", last);
        assert!((last[S_RIGHT] - 4.0).abs() < 1e-3);
        assert_eq!(last[S_SETTLED], 1.0);
    }

    #[test]
    fn stretches_while_travelling_and_matches_video() {
        let mut t = Thumb::new(4, 0);
        t.select(3);
        let s = run(&mut t, 1.0);
        let max_w = s.iter().map(|x| x[S_RIGHT] - x[S_LEFT]).fold(0.0, f32::max);
        assert!(s.iter().all(|x| x[S_RIGHT] <= 4.0 + 1e-4 && x[S_LEFT] >= -1e-4), "thumb left the bar");
        // Video: peak width about 1.25 x the resting one.
        assert!(max_w > 1.12 && max_w < 1.5, "max width {max_w}");
        // Trailing edge overshoots the target a little before coming back.
        let max_l = s.iter().map(|x| x[S_LEFT]).fold(0.0, f32::max);
        assert!(max_l > 3.02 && max_l < 3.3, "overshoot {max_l}");
    }

    #[test]
    fn drag_follows_and_releases_to_nearest() {
        let mut t = Thumb::new(4, 0);
        t.drag_to(0.5);
        for k in 0..60 {
            t.drag_to(0.5 + k as f32 * 0.03);
            t.step(1.0 / 120.0);
        }
        let idx = t.release();
        assert_eq!(idx, 2, "released on {idx}");
        run(&mut t, 2.0);
        assert!((t.state()[S_LEFT] - 2.0).abs() < 1e-3);
    }

    #[test]
    fn rubber_band_is_bounded() {
        let mut t = Thumb::new(4, 3);
        t.drag_to(20.0);
        let s = run(&mut t, 1.0);
        let last = s.last().unwrap();
        assert!(last[S_RIGHT] <= 4.0 + 1e-4, "{:?}", last);
        assert!(last[S_RIGHT] - last[S_LEFT] < 1.0, "pill must squeeze at the wall {:?}", last);
    }

    #[test]
    fn frame_time_independent() {
        let mut a = Thumb::new(4, 0);
        let mut b = Thumb::new(4, 0);
        a.select(2);
        b.select(2);
        for _ in 0..60 {
            a.step(1.0 / 60.0);
        }
        for _ in 0..120 {
            b.step(1.0 / 120.0);
        }
        assert!((a.state()[S_LEFT] - b.state()[S_LEFT]).abs() < 0.01);
    }
}
