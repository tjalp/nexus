plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.
    id("buildsrc.convention.kotlin-jvm")
    id(libs.plugins.paperUserDevPlugin.get().pluginId)
    alias(libs.plugins.shadowPlugin)
    alias(libs.plugins.runPaperPlugin)
    alias(libs.plugins.kotlinPluginSerialization)
//    alias(libs.plugins.foojayResolverConventionPlugin)
}

paperweight.reobfArtifactConfiguration = io.papermc.paperweight.userdev.ReobfArtifactConfiguration.MOJANG_PRODUCTION

dependencies {
    implementation(libs.bundles.kotlinxEcosystem)
    implementation(libs.bundles.database)
    implementation(libs.commonsCollections)

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

tasks.withType(xyz.jpenilla.runtask.task.AbstractRun::class) {
    javaLauncher = javaToolchains.launcherFor {
        vendor = JvmVendorSpec.JETBRAINS
        languageVersion = JavaLanguageVersion.of(21)
    }
    jvmArgs("-XX:+AllowEnhancedClassRedefinition")
}
