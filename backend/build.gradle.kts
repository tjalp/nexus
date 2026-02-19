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
    implementation(libs.auth0.jwt)
}

ktor {
    development = true

    openApi {
        enabled = true
    }
}

tasks.register<JavaExec>("createAdminUser") {
    group = "application"
    description = "Create an admin user in the database. This should only be used for initial setup and should be removed or disabled afterwards for security reasons."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "net.tjalp.nexus.backend.util.CreateAdminUserKt"
}