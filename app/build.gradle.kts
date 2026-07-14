import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Load signing credentials from local.properties (never committed to git)
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

android {
    namespace = "com.hackerapps.c2k"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.hackerapps.c2k"
        minSdk = 26
        targetSdk = 36
        versionCode = 11
        versionName = "1.2.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        // Only configured when keystore properties exist in local.properties.
        // F-Droid builds unsigned and applies their own signature.
        create("release") {
            val storeFile = localProps["storeFile"] as String?
            if (storeFile != null) {
                this.storeFile     = file(storeFile)
                this.storePassword = localProps["storePassword"] as String
                this.keyAlias      = localProps["keyAlias"] as String
                this.keyPassword   = localProps["keyPassword"] as String
            }
        }
    }

    flavorDimensions += "store"

    productFlavors {
        create("foss") {
            dimension = "store"
        }
        create("play") {
            dimension = "store"
            if (localProps["storeFile"] != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions { jvmTarget = "21" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }

}

// ART baseline profile generation (assets/dexopt/baseline.prof(m)) is not reproducible:
// AGP's ArtProfile.kt iterates a HashMap<DexFile, DexFileData> without sorting when
// serializing some profile formats, so byte order can differ build-to-build even with
// otherwise identical output. Disabling it entirely is the documented workaround —
// see https://gist.github.com/obfusk/61046e09cee352ae6dd109911534b12e
tasks.configureEach {
    if (name.contains("ArtProfile")) {
        enabled = false
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.navigation.compose)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.datastore.preferences)

    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
