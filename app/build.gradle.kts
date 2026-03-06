import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val keystoreProperties = Properties().apply {
    val propsFile = rootProject.file("keystore.properties")
    if (propsFile.exists()) {
        propsFile.inputStream().use(::load)
    }
}

fun secretOrNull(name: String): String? =
    (findProperty(name) as? String)?.takeIf { it.isNotBlank() }
        ?: keystoreProperties.getProperty(name)?.takeIf { it.isNotBlank() }
        ?: System.getenv(name)?.takeIf { it.isNotBlank() }

val releaseStoreFile = secretOrNull("RELEASE_STORE_FILE")
val releaseStorePassword = secretOrNull("RELEASE_STORE_PASSWORD")
val releaseKeyAlias = secretOrNull("RELEASE_KEY_ALIAS")
val releaseKeyPassword = secretOrNull("RELEASE_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

android {
    namespace = "com.psycode.spotiflac"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.psycode.spotiflac"
        minSdk = 24
        targetSdk = 34
        versionCode = 110
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "SPOTIFY_API_BASE_URL",
            "\"${project.findProperty("SPOTIFY_API_BASE_URL")}\""
        )
        buildConfigField(
            "String",
            "BACKEND_BASE_URL",
            "\"${project.findProperty("BACKEND_BASE_URL")}\""
        )
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
            isShrinkResources = false
        }

        getByName("release") {
            isDebuggable = false
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                logger.warn(
                    "Release signing is not configured. " +
                            "Set RELEASE_STORE_FILE, RELEASE_STORE_PASSWORD, " +
                            "RELEASE_KEY_ALIAS, RELEASE_KEY_PASSWORD " +
                            "(Gradle properties, keystore.properties, or env vars)."
                )
            }
        }
    }

    configurations.all {
        resolutionStrategy.eachDependency {
            if (requested.group == "androidx.lifecycle") {
                useVersion("2.8.3")
                because("Align all Lifecycle artifacts to 2.8.3")
            }
        }
    }
}

ktlint {
    android.set(true)
    ignoreFailures.set(true)
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    ignoreFailures = true
    parallel = true
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "17"
}

dependencies {
    
    coreLibraryDesugaring(libs.android.desugar.jdk.libs)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.paging.common.android)

    
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.ui.preview)
    implementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    
    implementation(libs.androidx.lifecycle.common.java8)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    
    implementation(libs.google.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.google.hilt.android.compiler)
    ksp(libs.androidx.hilt.compiler)

    
    implementation(libs.squareup.okhttp)
    implementation(libs.squareup.okhttp.logging.interceptor)
    implementation(libs.squareup.retrofit)
    implementation(libs.squareup.retrofit.converter.gson)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.io.coil.compose)

    
    implementation(libs.google.material)
    implementation(libs.androidx.material.icons.extended)

    
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.room.compiler)

    
    implementation(libs.androidx.datastore.preferences)

    
    implementation(libs.turn.ttorrent.core)
    implementation(libs.frostwire.jlibtorrent)
    implementation(libs.frostwire.jlibtorrent.android.arm)
    implementation(libs.frostwire.jlibtorrent.android.arm64)
    implementation(libs.frostwire.jlibtorrent.android.x86)
    implementation(libs.frostwire.jlibtorrent.android.x64)

    
    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.ui.test.junit4)
}
