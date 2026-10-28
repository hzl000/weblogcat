plugins { alias(libs.plugins.android.application); alias(libs.plugins.compose.compiler) }
android {
 buildFeatures { compose = true }
 namespace = "dev.weblogcat.fixture"
 compileSdk = 36
 defaultConfig { applicationId = "dev.weblogcat.fixture"; minSdk = 26; targetSdk = 36; versionCode = 1; versionName = "1" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies {
 implementation(platform(libs.androidx.compose.bom))
 implementation(libs.androidx.activity.compose)
 implementation(libs.androidx.compose.material3)
}
