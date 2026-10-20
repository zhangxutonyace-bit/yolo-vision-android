plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "cn.local.yolovision"
    compileSdk = 35
    defaultConfig {
        applicationId = "cn.local.yolovision"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        // LiteRT has no 32-bit x86 runtime; do not advertise that unsupported ABI.
        ndk { abiFilters += setOf("arm64-v8a", "armeabi-v7a", "x86_64") }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            // The distributed first build is explicitly a test APK, using the standard debug signer.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    androidResources { noCompress += "tflite" }
    packaging { resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE.md", "META-INF/NOTICE.md") }
    testOptions { unitTests.isReturnDefaultValues = true }
}
dependencies {
    implementation(project(":yolo-engine"))
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.startup:startup-runtime:1.1.1")
    implementation("com.google.android.material:material:1.12.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
