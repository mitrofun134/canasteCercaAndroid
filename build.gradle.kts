plugins {
    // AGP 9 компилирует Kotlin сам (built-in Kotlin): плагин kotlin-android не нужен
    alias(libs.plugins.android.application) apply false
}
