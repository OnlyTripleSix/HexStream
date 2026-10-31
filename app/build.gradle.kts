plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.navigation.safeargs)
    alias(libs.plugins.apollo)
}

kotlin {
    jvmToolchain(21)
}

android {
    // Local development uses Android's standard debug signing key.
    // Release signing is supplied by the builder; no keystore is distributed.
    val releaseKeyFile = providers.environmentVariable("HEXSTREAM_KEYSTORE_FILE").orNull
    signingConfigs {
        if (!releaseKeyFile.isNullOrBlank()) {
            create("hexstreamRelease") {
                storeFile = file(releaseKeyFile)
                storePassword = providers.environmentVariable("HEXSTREAM_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("HEXSTREAM_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("HEXSTREAM_KEY_PASSWORD").get()
            }
        }
    }
    namespace = "com.github.andreyasadchy.xtra"
    compileSdk = 37

    bundle { language { enableSplit = false } }

    defaultConfig {
        applicationId = "com.github.andreyasadchy.xtra"
        minSdk = 24
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = 144
        versionName = "1.0.0-beta.8.2"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".prototype"
            versionNameSuffix = "-debug"
            signingConfig = signingConfigs.getByName("debug")
        }
        create("qa") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".qa"
            versionNameSuffix = "-HEXSTREAM-QA"
            matchingFallbacks += "debug"
        }
        release {
            applicationIdSuffix = ".prototype"
            isShrinkResources = true
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("hexstreamRelease")
        }
    }
    testBuildType = providers.gradleProperty("hexstreamTestBuildType").getOrElse("debug")
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
    androidResources {
        generateLocaleConfig = true
    }
    lint {
        disable += "ContentDescription"
    }
    packaging.jniLibs.excludes.addAll(listOf(
        "lib/x86/libtranslate_jni.so",
        "lib/x86/liblanguage_id_l2c_jni.so",
        "lib/x86_64/libtranslate_jni.so",
        "lib/x86_64/liblanguage_id_l2c_jni.so",
        "lib/armeabi-v7a/libtranslate_jni.so",
        "lib/armeabi-v7a/liblanguage_id_l2c_jni.so",
    ))
}

dependencies {
    testImplementation(libs.junit)
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.test.junit)
    compileOnly(libs.j2objc) // OkHttpDataSource SettableFuture
    implementation(libs.mlkit.language)
    implementation(libs.mlkit.translate)

    implementation(libs.material)
    implementation(libs.markwon.core)
    implementation(libs.markwon.linkify)

    implementation(libs.activity)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.coordinatorlayout)
    implementation(libs.core)
    implementation(libs.fragment)
    implementation(libs.lifecycle.service)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    implementation(libs.paging.runtime)
    implementation(libs.preference.ktx)
    implementation(libs.recyclerview)
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    implementation(libs.room.paging)
    implementation(libs.swiperefreshlayout)
    implementation(libs.viewpager2)
    implementation(libs.webkit)
    implementation(libs.work.runtime)

    implementation(libs.cronet.api)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.conscrypt)
    implementation(libs.serialization.json)
    implementation(libs.apollo.api)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)

    implementation(libs.coil)
    implementation(libs.coil.gif)
    implementation(libs.coil.okhttp)

    implementation(libs.glide)
    ksp(libs.glide.ksp)
    implementation(libs.glide.okhttp)
    implementation(libs.glide.webpdecoder)

    implementation(libs.coroutines)
}

apollo {
    @Suppress("ApolloEndpointNotConfigured")
    service("service") {
        packageName.set("com.github.andreyasadchy.xtra.graphql")
    }
}

// Delete large build log files from ~/.gradle/daemon/X.X/daemon-XXX.out.log
// Source: https://discuss.gradle.org/t/gradle-daemon-produces-a-lot-of-logs/9905
File("${project.gradle.gradleUserHomeDir.absolutePath}/daemon/${project.gradle.gradleVersion}").listFiles()?.forEach {
    if (it.name.endsWith(".out.log")) {
        // println("Deleting gradle log file: $it") // Optional debug output
        it.delete()
    }
}
