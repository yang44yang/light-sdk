plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "io.github.yang44yang.habits"
    compileSdk = rootProject.ext["compileSdk"] as Int
    defaultConfig {
        minSdk = 34
        testApplicationId = "io.github.yang44yang.routine.verification.test"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    // Test the production files themselves; do not maintain copies of the logic.
    sourceSets["main"].java.srcDir("../tool/src/main/kotlin")
    sourceSets["main"].res.srcDir("../tool/src/main/res")
    sourceSets["androidTest"].assets.srcDir("../tool/src/main/assets")
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

ksp { arg("room.schemaLocation", "$projectDir/../tool/src/main/assets/room-schemas") }

dependencies {
    implementation(project(":sdk:client"))
    ksp(libs.androidx.room.compiler)
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation(libs.kotlin.test)
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
