//! JNI glue of the pixel engine (engine.rs) and the thumb motion engine (motion.rs).
//! The algorithms themselves have no Android dependencies, so they are testable on a desktop.

pub mod engine;
pub mod motion;
use engine::{duo_content, render};

// ---------- JNI ----------
use jni::objects::{JFloatArray, JIntArray, JObject};
use jni::sys::{jfloat, jint};
use jni::JNIEnv;

fn read_f32(env: &mut JNIEnv, a: &JFloatArray, n: usize) -> Option<Vec<f32>> {
    let mut v = vec![0.0f32; n];
    env.get_float_array_region(a, 0, &mut v).ok()?;
    Some(v)
}

fn read_u32(env: &mut JNIEnv, a: &JIntArray, n: usize) -> Option<Vec<u32>> {
    let mut v = vec![0i32; n];
    env.get_int_array_region(a, 0, &mut v).ok()?;
    Some(v.into_iter().map(|c| c as u32).collect())
}

fn write_u32(env: &mut JNIEnv, out: &JIntArray, data: Vec<u32>) {
    let as_i32: Vec<i32> = data.into_iter().map(|c| c as i32).collect();
    let _ = env.set_int_array_region(out, 0, &as_i32);
}

#[no_mangle]
pub extern "system" fn Java_com_glavakartela_iconrecolorliquid_NativeCore_nativeDuoContent(
    mut env: JNIEnv,
    _this: JObject,
    mask: JFloatArray,
    v: jint,
    bg: jint,
    fg: jint,
    out: JIntArray,
) {
    let v = v as usize;
    if let Some(t) = read_f32(&mut env, &mask, v * v) {
        write_u32(&mut env, &out, duo_content(&t, v, bg as u32, fg as u32));
    }
}

#[no_mangle]
pub extern "system" fn Java_com_glavakartela_iconrecolorliquid_NativeCore_nativeRender(
    mut env: JNIEnv,
    _this: JObject,
    content: JIntArray,
    alpha: JFloatArray,
    v: jint,
    strength: jfloat,
    out: JIntArray,
) {
    let v = v as usize;
    let (Some(c), Some(a)) = (read_u32(&mut env, &content, v * v), read_f32(&mut env, &alpha, v * v)) else {
        return;
    };
    write_u32(&mut env, &out, render(&c, &a, v, strength));
}

// ---------- JNI: liquid thumb motion ----------
use jni::sys::jboolean;
use std::sync::Mutex;

static THUMBS: Mutex<Vec<Option<motion::Thumb>>> = Mutex::new(Vec::new());

fn with_thumb<R>(id: jint, f: impl FnOnce(&mut motion::Thumb) -> R) -> Option<R> {
    let mut g = THUMBS.lock().ok()?;
    g.get_mut(usize::try_from(id).ok()?)?.as_mut().map(f)
}

#[no_mangle]
pub extern "system" fn Java_com_glavakartela_iconrecolorliquid_NativeCore_nativeThumbNew(
    _env: JNIEnv,
    _this: JObject,
    count: jint,
    selected: jint,
) -> jint {
    let Ok(mut g) = THUMBS.lock() else { return -1 };
    let t = motion::Thumb::new(count.max(1) as usize, selected.max(0) as usize);
    if let Some(i) = g.iter().position(|s| s.is_none()) {
        g[i] = Some(t);
        i as jint
    } else {
        g.push(Some(t));
        (g.len() - 1) as jint
    }
}

#[no_mangle]
pub extern "system" fn Java_com_glavakartela_iconrecolorliquid_NativeCore_nativeThumbFree(
    _env: JNIEnv,
    _this: JObject,
    id: jint,
) {
    if let Ok(mut g) = THUMBS.lock() {
        if let Some(slot) = usize::try_from(id).ok().and_then(|i| g.get_mut(i)) {
            *slot = None;
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_glavakartela_iconrecolorliquid_NativeCore_nativeThumbSelect(
    _env: JNIEnv,
    _this: JObject,
    id: jint,
    idx: jint,
) {
    with_thumb(id, |t| t.select(idx.max(0) as usize));
}

#[no_mangle]
pub extern "system" fn Java_com_glavakartela_iconrecolorliquid_NativeCore_nativeThumbPress(
    _env: JNIEnv,
    _this: JObject,
    id: jint,
    pressed: jboolean,
) {
    with_thumb(id, |t| t.set_pressed(pressed != 0));
}

#[no_mangle]
pub extern "system" fn Java_com_glavakartela_iconrecolorliquid_NativeCore_nativeThumbDrag(
    _env: JNIEnv,
    _this: JObject,
    id: jint,
    center: jfloat,
) {
    with_thumb(id, |t| t.drag_to(center));
}

#[no_mangle]
pub extern "system" fn Java_com_glavakartela_iconrecolorliquid_NativeCore_nativeThumbRelease(
    _env: JNIEnv,
    _this: JObject,
    id: jint,
) -> jint {
    with_thumb(id, |t| t.release() as jint).unwrap_or(0)
}

#[no_mangle]
pub extern "system" fn Java_com_glavakartela_iconrecolorliquid_NativeCore_nativeThumbStep(
    #[allow(unused_mut)] mut env: JNIEnv,
    _this: JObject,
    id: jint,
    dt: jfloat,
    out: JFloatArray,
) {
    if let Some(state) = with_thumb(id, |t| {
        t.step(dt);
        t.state()
    }) {
        let _ = env.set_float_array_region(&out, 0, &state);
    }
}
