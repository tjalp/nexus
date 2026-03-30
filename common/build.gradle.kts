plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    api(libs.bundles.kotlinxEcosystem)
    api(libs.bundles.database)
    api(libs.jbcrypt)
    api(libs.lettuce)
    api(libs.ktor.client.core)
    api(libs.ktor.client.cio)
    api(libs.ktor.client.content.negotiation)
    api(libs.ktor.serialization.kotlinx.json)
    api(libs.ktor.server.core)
    api(libs.ktor.server.cio)
    api(libs.ktor.content.negotiation)
}