plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    // AGP 9 включает Kotlin сам (built-in Kotlin). kotlin-jvm здесь не применяется — он только
    // поднимает версию Kotlin Gradle Plugin до версии из каталога, чтобы компилятор совпадал
    // с плагинами Compose, Serialization и Metro.
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.metro) apply false
}
