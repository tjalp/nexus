plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.
    id("buildsrc.convention.kotlin-jvm")
    id(libs.plugins.paperUserDevPlugin.get().pluginId)
    // Apply Kotlin Serialization plugin from `gradle/libs.versions.toml`.
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    // Apply the kotlinx bundle of dependencies from the version catalog (`gradle/libs.versions.toml`).
    api(libs.bundles.kotlinxEcosystem)
    api(libs.commonsCollections)

    compileOnlyApi(libs.paperApi)

    paperweight.paperDevBundle(libs.versions.paperApi)
}