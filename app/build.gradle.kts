plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("dagger.hilt.android.plugin")
}

android {
    namespace = "foundation.algorand.demo"
    compileSdk = 34

    defaultConfig {
        applicationId = "foundation.algorand.demo"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        buildConfigField("String", "API_BASE_URL", "\"https://nest-fido2.vercel.app\"")
        resValue("string", "host", "https://nest-fido2.vercel.app")
        resValue(
            "string", "asset_statements", """
           [{
             "include": "https://nest-fido2.vercel.app/.well-known/assetlinks.json"
           }]
        """)
    }
    signingConfigs {
        getByName("debug") {
            storeFile = file("../debug.jks")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        dataBinding = true
        buildConfig = true
    }
}
var kotlinVersion: String by rootProject.extra
dependencies {
    // AlgoSDK
    implementation("org.bouncycastle:bcprov-jdk15on:1.67")
    implementation("com.github.Jesulonimi21:java-algorand-sdk:1.5.0")

    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")

    // FIDO2 Client
    implementation("com.google.android.gms:play-services-fido:20.1.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.fragment:fragment-ktx:1.6.0")

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Barcode Reader
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")

    var coroutineVersion by extra { "1.7.1" }
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutineVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutineVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:$coroutineVersion")

    var hiltVersion by extra { "2.48" }
    implementation("com.google.dagger:hilt-android:$hiltVersion")
    implementation("com.google.mlkit:camera:16.0.0-beta3")
    implementation("androidx.legacy:legacy-support-v4:1.0.0")
    kapt("com.google.dagger:hilt-compiler:$hiltVersion")
    kapt("androidx.hilt:hilt-compiler:1.0.0")

    var lifecycleVersion by extra { "2.6.2" }
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycleVersion")
    testImplementation("androidx.arch.core:core-testing:2.2.0")

    implementation("com.google.android.material:material:1.9.0")

    var okhttpVersion by extra { "4.11.0" }
    implementation("com.squareup.okhttp3:okhttp:$okhttpVersion")
    implementation("com.squareup.okhttp3:logging-interceptor:$okhttpVersion")
    implementation("ru.gildor.coroutines:kotlin-coroutines-okhttp:1.0")

//    testImplementation 'junit:junit:4.13.2'
//    androidTestImplementation 'androidx.test:runner:1.5.2'
//    androidTestImplementation 'androidx.test:rules:1.5.0'
//
//    androidTestImplementation 'androidx.test.ext:junit:1.1.5'
//    androidTestImplementation 'androidx.test.ext:truth:1.5.0'
//    androidTestImplementation 'com.google.truth:truth:1.1.3'
//
//    androidTestImplementation 'androidx.test.espresso:espresso-core:3.5.1'

//    val hiltVersion: String by extra {"2.48"}
//    implementation("com.google.dagger:hilt-android:$hiltVersion")
//    kapt("com.google.dagger:hilt-compiler:$hiltVersion")

//    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
//    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-cbor:2.15.2")
//    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
//    implementation("com.google.guava:guava:32.1.2-android")
//    implementation("androidx.core:core-ktx:1.9.0")
//    implementation("androidx.appcompat:appcompat:1.6.1")
//    implementation("com.google.android.material:material:1.9.0")
//    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
//    implementation("androidx.navigation:navigation-fragment-ktx:2.5.3")
//    implementation("androidx.navigation:navigation-ui-ktx:2.5.3")
//    implementation("com.squareup.okhttp3:okhttp:4.11.0")
//    testImplementation("junit:junit:4.13.2")
//    androidTestImplementation("androidx.test.ext:junit:1.1.5")
//    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}