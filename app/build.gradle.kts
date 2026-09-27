import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.musictag.artistcover"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.musictag.artistcover"
        minSdk = 26
        targetSdk = 35
        // 版本号按构建时间自增，便于区分每次产出的 APK。
        versionCode = (System.currentTimeMillis() / 1000L).toInt()
        versionName = "1.0.0+" + SimpleDateFormat("MMddHHmm", Locale.US).format(Date())
    }

    buildTypes {
        release {
            // 首版不做混淆，避免反射 / JSON 解析相关的问题；同时不引入体积巨大的 icons-extended。
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // 供「关于」里读取版本号
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.documentfile:documentfile:1.1.0")

    implementation("androidx.compose.ui:ui:1.11.1")
    implementation("androidx.compose.ui:ui-graphics:1.11.1")
    implementation("androidx.compose.ui:ui-tooling-preview:1.11.1")
    implementation("androidx.compose.foundation:foundation:1.11.1")
    implementation("androidx.compose.animation:animation:1.11.1")
    implementation("androidx.compose.material3:material3:1.5.0-alpha19")
    implementation("androidx.compose.material:material-icons-core:1.7.8")

    debugImplementation("androidx.compose.ui:ui-tooling:1.11.1")
}
