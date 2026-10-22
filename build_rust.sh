#!/usr/bin/env bash
# Builds the Rust engine into app/src/main/jniLibs/arm64-v8a. Gradle runs this before every build.
# With cargo-ndk (rustup target add aarch64-linux-android; cargo install cargo-ndk; ANDROID_NDK_HOME set)
# it cross-compiles; otherwise (for example Termux on the phone) it uses plain cargo.
set -e
root="$(dirname "$(readlink -f "$0")")"
cd "$root/rust/iconcore"
dest="$root/app/src/main/jniLibs/arm64-v8a"
mkdir -p "$dest"
if command -v cargo-ndk >/dev/null 2>&1 && [ -n "$ANDROID_NDK_HOME" ]; then
    cargo ndk -t arm64-v8a -o "$root/app/src/main/jniLibs" build --release
else
    cargo build --release
    cp target/release/libiconcore.so "$dest/"
fi
echo "Rust engine: $dest/libiconcore.so"
