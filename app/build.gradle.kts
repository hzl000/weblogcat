plugins { alias(libs.plugins.android.application); alias(libs.plugins.compose.compiler) }
android {
 namespace = "dev.weblogcat.probe"
 compileSdk = 36
 buildFeatures { compose = true }
 defaultConfig {
  applicationId = "dev.weblogcat.probe"
  minSdk = 26
  targetSdk = 36
  versionCode = 4
  versionName = "0.3.1"
 }
 compileOptions {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
 }
}

dependencies {
 testImplementation(libs.junit)
 implementation(platform(libs.androidx.compose.bom))
 implementation(libs.androidx.activity.compose)
 implementation(libs.androidx.compose.material3)
 implementation(libs.androidx.compose.ui)
 implementation(libs.androidx.compose.ui.tooling.preview)
 implementation("com.google.zxing:core:3.5.3")
 implementation("com.github.MuntashirAkon:libadb-android:3.1.1")
 implementation("org.conscrypt:conscrypt-android:2.5.3")
 implementation("org.bouncycastle:bcpkix-jdk15to18:1.81")
}
