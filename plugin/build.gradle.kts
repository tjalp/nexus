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

repositories {
    mavenCentral()
    maven {
        name = "md-5"
        url = uri("https://repo.md-5.net/content/groups/public/")
    }
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
}

paperweight.reobfArtifactConfiguration = io.papermc.paperweight.userdev.ReobfArtifactConfiguration.MOJANG_PRODUCTION

dependencies {
    implementation(libs.bundles.kotlinxEcosystem)
    implementation(libs.bundles.database)
    implementation(libs.configurateKotlinExtras)
    implementation(libs.commonsCollections)

    compileOnly(libs.libsDisguises)

    paperweight.paperDevBundle(libs.versions.paperApi)
}

tasks {
    runServer {
        minecraftVersion("1.21.11")
        downloadPlugins {
//            github("libraryaddict", "LibsDisguises", "v11.0.13", "LibsDisguises-11.0.13-Github.jar")
        }
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
        @Suppress("UnstableApiUsage")
        vendor = JvmVendorSpec.JETBRAINS
        languageVersion = JavaLanguageVersion.of(21)
    }
    jvmArgs("-XX:+AllowEnhancedClassRedefinition")
}
