plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    api(libs.bundles.kotlinxEcosystem)
    api(libs.bundles.database)
    api(libs.jbcrypt)
}