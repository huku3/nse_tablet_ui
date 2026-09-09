import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// ===== バージョン自動採番 =====
// version.properties に versionCode / versionName を保持する。
// リリースビルド（タスク名に "Release" を含む）を実行すると versionCode を +1 して
// その値で署名APKを生成する。デバッグビルドでは採番しない。
val versionPropsFile = rootProject.file("version.properties")
val versionProps = Properties().apply {
    if (versionPropsFile.exists()) FileInputStream(versionPropsFile).use { load(it) }
}
val isReleaseBuild = gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }
val computedVersionCode = run {
    var code = (versionProps.getProperty("versionCode") ?: "1").toInt()
    if (isReleaseBuild) {
        code += 1
        versionProps.setProperty("versionCode", code.toString())
        versionPropsFile.outputStream().use { versionProps.store(it, "auto-bumped on release build") }
        println("▶ release versionCode = $code")
    }
    code
}
val computedVersionName = versionProps.getProperty("versionName") ?: "1.0"

// ===== リリース署名 =====
// keystore.properties が存在すればそれを使って release を署名する（無ければ未署名）。
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) FileInputStream(keystorePropsFile).use { load(it) }
}

android {
    namespace = "jp.co.nse.worker"
    compileSdk = 34

    defaultConfig {
        applicationId = "jp.co.nse.worker"
        minSdk = 26
        targetSdk = 34
        versionCode = computedVersionCode
        versionName = computedVersionName

        // 接続先APIのベースURL（末尾スラッシュ必須）。アプリ内の設定画面でも変更可能。
        buildConfigField("String", "DEFAULT_API_BASE_URL", "\"https://nagashima0701.sakura.ne.jp/nse/api/\"")
    }

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.ui.text.google.fonts)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization.converter)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    implementation(libs.play.services.code.scanner)
    implementation(libs.play.services.base)

    debugImplementation(libs.androidx.ui.tooling)
}
