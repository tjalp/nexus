plugins {
    application
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
    alias(libs.plugins.ktor)
}

application {
    mainClass = "net.tjalp.nexus.backend.ApplicationKt"
}

dependencies {
    implementation(project(":common"))
    implementation(libs.bundles.ktor)
    implementation(libs.logback.classic)
    implementation(libs.kgraphql.ktor)
}

ktor {
    development = true

    openApi {
        enabled = true
    }
}