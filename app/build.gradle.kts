import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// 读取签名配置（release.keystore 与密码，已 gitignore 不入库）
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "io.github.rusheng920.wattscope"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.rusheng920.wattscope"
        minSdk = 33
        targetSdk = 36
        // 正式版：versionCode 需大于所有测试版（beta.3 = 11），保证 beta 用户可直接覆盖安装
        versionCode = 20
        versionName = "1.4.0-beta.3"
    }

    signingConfigs {
        create("release") {
            if (keystoreProps.isNotEmpty()) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // 没有 keystore.properties（CI 首次运行、未配置 secrets）时不指定签名配置，
            // 让 Gradle 产出 *-unsigned.apk，而不是在 signingConfig 上直接失败。
            if (keystoreProps.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }
}

dependencies {
    val roomVersion = "2.8.5"
    val lifecycleVersion = "2.9.2"
    val miuixVersion = "0.8.8"

    implementation(platform("androidx.compose:compose-bom:2026.02.00"))
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-service:$lifecycleVersion")
    implementation("androidx.navigation:navigation-compose:2.9.2")
    implementation("androidx.room:room-runtime:$roomVersion")

    // MIUIX（HyperOS 设计语言）：真实组件库，而非自绘仿制
    implementation("top.yukonga.miuix.kmp:miuix-android:$miuixVersion")
    implementation("top.yukonga.miuix.kmp:miuix-icons-android:$miuixVersion")

    // 液态玻璃：Kyant0 Backdrop（https://github.com/Kyant0/AndroidLiquidGlass）
    // 1.0.6 是最后一个面向 Compose 1.10.3 / Kotlin 2.3.x 的版本，
    // 与本项目 BOM 2026.02.00 精确对齐；2.x 起要求 Kotlin 2.4 / CMP 1.12，不要升级。
    implementation("io.github.kyant0:backdrop:1.0.6")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
    implementation("androidx.work:work-runtime-ktx:2.10.2")
    implementation("com.github.topjohnwu.libsu:core:6.0.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
