plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.
    id("buildsrc.convention.kotlin-jvm")
    id(libs.plugins.paperUserDevPlugin.get().pluginId)
    alias(libs.plugins.shadowPlugin)
    alias(libs.plugins.runPaperPlugin)
}

paperweight.reobfArtifactConfiguration = io.papermc.paperweight.userdev.ReobfArtifactConfiguration.MOJANG_PRODUCTION

dependencies {
    implementation(project(":common"))
    implementation(project(":chat"))
    implementation(project(":gamerules"))

    paperweight.paperDevBundle(libs.versions.paperApi)
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
