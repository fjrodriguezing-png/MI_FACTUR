plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.francisco.mifactur"
    compileSdk = 35
    defaultConfig { applicationId = "com.francisco.mifactur"; minSdk = 26; targetSdk = 35; versionCode = 10; versionName = "5.1" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies { implementation("com.google.zxing:core:3.5.3") }
