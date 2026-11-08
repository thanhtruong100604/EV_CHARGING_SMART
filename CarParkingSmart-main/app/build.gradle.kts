plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
}


android {
    namespace = "com.example.carparkingsmart"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.carparkingsmart"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs += listOf("-language-version", "1.9")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    val room_version = "2.7.0"
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    implementation("org.osmdroid:osmdroid-android:6.1.18")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    kapt("androidx.room:room-compiler:$room_version") //

    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.google.code.gson:gson:2.10.1")

    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.fragment:fragment-ktx:1.6.2")

    implementation("com.github.bumptech.glide:glide:4.16.0")
    kapt("com.github.bumptech.glide:compiler:4.16.0")

    // Text-to-Speech
    implementation("com.google.android.gms:play-services-location:21.0.1")
// OSRM Routing
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
// JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")
// Permissions
    implementation("pub.devrel:easypermissions:3.0.0")
}

