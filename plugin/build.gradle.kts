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

dependencies {
    implementation(project(":common"))
    implementation(libs.configurateKotlinExtras)
    implementation(libs.commonsCollections)
    implementation(libs.icu4j)

    compileOnly(libs.libsDisguises)

    paperweight.paperDevBundle(libs.versions.paperApi)
}

tasks {
    runServer {
        minecraftVersion("26.1.2")
        downloadPlugins {
//            github("libraryaddict", "LibsDisguises", "v11.0.13", "LibsDisguises-11.0.13-Github.jar")
            hangar("ViaVersion", "5.8.1")
            hangar("ViaBackwards", "5.8.1")
        }
    }

    shadowJar {
        archiveFileName.set("nexus-${version}.jar")

        manifest {
            attributes["paperweight-mappings-namespace"] = "mojang"
        }
    }
}

tasks.withType(xyz.jpenilla.runtask.task.AbstractRun::class) {
    javaLauncher = javaToolchains.launcherFor {
        @Suppress("UnstableApiUsage")
        vendor = JvmVendorSpec.JETBRAINS
        languageVersion = JavaLanguageVersion.of(25)
    }
    jvmArgs("-XX:+AllowEnhancedClassRedefinition")
}

tasks.register<JavaExec>("generateMigrationScript") {
    group = "application"
    description = "Generate a migration script in the path plugin/src/main/resources/db/migration"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "net.tjalp.nexus.GenerateMigrationScriptKt"
}

tasks.register<Copy>("copyJarToDevPlugins") {
    group = "application"
    description = "Copy the built jar to the /dev/plugins/ directory for testing"

    // copy and rename file to "nexus.jar"
    dependsOn(tasks.shadowJar)
    from(tasks.shadowJar.get().archiveFile)
    rename { "nexus.jar" }
    into(file("../dev/plugins/"))
}

tasks.shadowJar {
    mergeServiceFiles {
        include("META-INF/services/**")
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
}
