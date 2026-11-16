plugins {
    alias(libs.plugins.android.application)
}

import java.util.Properties

// 读取本地 local.properties（已被 Git 忽略），把密钥注入 BuildConfig，不写进公开代码
val localProps = Properties()
val localPropsFile = rootProject.file("local.properties")
if (localPropsFile.exists()) {
    localPropsFile.inputStream().use { localProps.load(it) }
}

android {
    namespace = "com.hexbei.appwidget"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.hexbei.appwidget"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "EXTENDSCLASS_API_KEY", "\"${localProps.getProperty("EXTENDSCLASS_API_KEY", "")}\"")
        buildConfigField("String", "ZHIPU_API_KEY", "\"${localProps.getProperty("ZHIPU_API_KEY", "")}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // 课表 PDF 解析（导入课表用）
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}