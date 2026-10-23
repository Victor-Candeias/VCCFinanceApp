import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val appVersionCode = 5
val appVersionName = "0.5.0"

// Release signing credentials live outside git: root keystore.properties (gitignored) or
// ~/.keystores/vcc-finance-release.properties. Without them the release APK is left unsigned.
val releaseSigningProperties: Properties? = listOf(
    rootProject.file("keystore.properties"),
    File(System.getProperty("user.home"), ".keystores/vcc-finance-release.properties"),
).firstOrNull { it.isFile }?.let { file ->
    Properties().apply { file.inputStream().use(::load) }
}

base {
    archivesName.set("VCCFinanceApp-$appVersionName-$appVersionCode")
}

android {
    namespace = "pt.vcc.financeapp"
    compileSdk = 37

    defaultConfig {
        applicationId = "pt.vcc.financeapp"
        minSdk = 26
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSigningProperties != null) {
            create("release") {
                storeFile = file(releaseSigningProperties.getProperty("ANDROID_KEYSTORE_PATH"))
                storePassword = releaseSigningProperties.getProperty("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = releaseSigningProperties.getProperty("ANDROID_KEY_ALIAS")
                keyPassword = releaseSigningProperties.getProperty("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:database"))
    implementation(project(":core:security"))
    implementation(project(":core:designsystem"))
    implementation(project(":feature:finance"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    debugImplementation(libs.compose.tooling)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
