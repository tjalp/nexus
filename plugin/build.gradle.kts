plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.shadowPlugin)
    alias(libs.plugins.runPaperPlugin)
}

dependencies {
    implementation(project(":common"))
    implementation(project(":chat"))
}

tasks {
    runServer {
        minecraftVersion("1.21.10")
    }

    shadowJar {
        archiveFileName.set("nexus.jar")

        manifest {
            attributes["paperweight-mappings-namespace"] = "mojang"
        }
    }
}
