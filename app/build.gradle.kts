// AGP 9 has Kotlin built in, the org.jetbrains.kotlin.android plugin is not needed.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.glavakartela.iconrecolorliquid"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.glavakartela.iconrecolorliquid"
        minSdk = 33
        targetSdk = 37
        versionCode = 3
        versionName = "0.5"
        ndk { abiFilters += "arm64-v8a" }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    // Bytecode stays at Java 17, any JDK 17-26 (including 25) can build it.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        aidl = true
    }
}

// The Rust engine is part of every build: build_rust.sh produces libiconcore.so before packaging.
val buildRust = tasks.register<Exec>("buildRust") {
    workingDir = rootDir
    commandLine("bash", "build_rust.sh")
    inputs.dir("$rootDir/rust/iconcore/src")
    inputs.file("$rootDir/rust/iconcore/Cargo.toml")
    outputs.file("$rootDir/app/src/main/jniLibs/arm64-v8a/libiconcore.so")
}
tasks.named("preBuild") { dependsOn(buildRust) }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.08.00"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    implementation("com.github.QWEA0:liquidglass:v2.0.11")

    // Shizuku: run shell commands (like adb) from the app
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}
