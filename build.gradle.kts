// Top-level build file where you can add configuration options common to all sub-projects/modules.
// AGP 9 内建 Kotlin（默认 KGP 2.2.10），此处用 classpath 覆盖为 2.3.10，
// 与 KSP 2.3.10 / Compose 编译器 2.3.10 保持同一版本线。
buildscript {
    dependencies {
        classpath(libs.kotlin.gradle.plugin)
    }
}
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
