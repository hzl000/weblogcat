# 第三方依赖

- Kotlin 与 Jetpack Compose：Apache License 2.0。https://github.com/JetBrains/kotlin 和 https://android.googlesource.com/platform/frameworks/support/
- ZXing Core 3.5.3：Apache License 2.0。https://github.com/zxing/zxing
- LibADB Android 3.1.1：GPL-3.0-or-later 或 Apache-2.0 双许可；项目及依赖可能另有许可条款。https://github.com/MuntashirAkon/libadb-android
- Conscrypt Android 2.5.3：Apache License 2.0。https://github.com/google/conscrypt
- Bouncy Castle Java 1.81：Bouncy Castle 许可。https://www.bouncycastle.org/licence.html
- spake2-java 2.2.1：LibADB 的传递依赖，分发前需连同其原生依赖的许可文件核对。https://github.com/MuntashirAkon/spake2-java

依赖通过 Gradle 获取，详细传递依赖可用 `./gradlew :app:dependencies --configuration debugRuntimeClasspath` 查看。
