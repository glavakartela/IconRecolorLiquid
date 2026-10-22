# IconRecolor Liquid

Recolor your app icons with a Liquid Glass effect using any two colors, or a palette derived from your wallpaper

The app redraws icons with a glass effect and pins them to the home screen as shortcuts. Original app icons and system files are left untouched.

## Features

- **Liquid Glass effect** — apply a glass-style appearance to your app icons.
- **Two-color mode** — choose two colors to customize the look of the glass effect.
- **Wallpaper palette** — use colors derived from your wallpaper in a Material You style.
- **Original colors** — preserve the original icon colors and apply the glass effect on top.
- **Adjustable strength** — control the intensity of the glass effect.
- **Icon shapes** — use the launcher's default shape, built-in shapes, or draw your own.
- **Shareable shapes** — export and import custom shapes using `icrec-...` codes.
- **Update in place** — refresh already pinned shortcuts after changing their appearance.
- **Local rendering** — icons are processed on the device.

## Requirements

- Android device. The app is primarily designed for Xiaomi, Redmi and POCO devices running HyperOS.
- Android 13 or newer is recommended; compatibility may vary by device and launcher.
- [Shizuku](https://github.com/RikkaApps/Shizuku) running — required for wallpaper access and shortcut-related operations. Root access is not required.
- For building on **Windows, Linux, or macOS**: a compatible JDK, Android SDK, Android SDK Build Tools, Android NDK and Rust toolchain.
- **Termux:** building directly on Android may work, but the `cargo-ndk` project does not officially support this environment.

## Usage

1. Start Shizuku and grant IconRecolor Liquid the required permission.
2. Choose two custom colors or use a palette derived from your wallpaper.
3. Select an icon shape.
4. Adjust the Liquid Glass effect strength to your preference.
5. Select an app and pin its recolored icon to the home screen.
6. Use **Update pinned** to refresh existing shortcuts after changing colors or shapes.

If an icon does not look as expected, try changing the available recoloring options in the app.

## Building

The project can be built on **Windows, Linux, or macOS**. You need a compatible JDK, the Android SDK with `platforms;android-37`, Android SDK Build Tools, the Android NDK and Rust.

The project uses a native Rust engine compiled for Android. A Kotlin fallback is not provided.

Install the Rust Android target and `cargo-ndk` if they are not already available:

```sh
rustup target add aarch64-linux-android
cargo install cargo-ndk
```

Make sure the Android NDK is installed and available to the Rust build script. The project includes `build_rust.sh` to build the native engine.

Run the Gradle build from the project root:

**Linux / macOS / Termux**
```sh
./gradlew assembleDebug
```

**Windows (Command Prompt / PowerShell)**
```bat
gradlew.bat assembleDebug
```

The APK is written to:

`app/build/outputs/apk/debug/app-debug.apk`

**Termux:** direct on-device builds may require extra environment configuration. `cargo-ndk` does not officially support Termux, so the build may need platform-specific adjustments.

The Rust target `aarch64-linux-android` produces the native library for 64-bit ARM Android devices (`arm64-v8a`). The build host can be x86_64 or ARM64; it does not determine the Android ABI.

## Notes

- Root access is not required, but Shizuku is mandatory.
- Icons are added as shortcuts; the original app icons remain unchanged.
- Shortcut behavior may vary between launchers and HyperOS versions.
- Custom icon shapes can be shared using their `icrec-...` codes.
- The native Rust engine is required for building the application.

## Built With

- Kotlin
- Jetpack Compose
- Material 3
- Rust
- Shizuku API

## License

Copyright (C) 2026 Kulesha Evgeniy

This project is licensed under the GNU General Public License v3.0.
See [LICENSE](LICENSE) for the full license text.
