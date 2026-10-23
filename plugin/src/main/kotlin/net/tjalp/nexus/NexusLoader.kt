package net.tjalp.nexus

import io.papermc.paper.plugin.loader.PluginClasspathBuilder
import io.papermc.paper.plugin.loader.PluginLoader
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.graph.Dependency
import org.eclipse.aether.repository.RemoteRepository
import java.util.*

@Suppress("UnstableApiUsage")
object NexusLoader : PluginLoader {

    override fun classloader(classpathBuilder: PluginClasspathBuilder) {
        val properties = Properties()

        NexusLoader::class.java.getResourceAsStream("/nexus-loader.properties")
            ?.use { properties.load(it) }
            ?: error("Missing nexus-loader.properties resource")

        val icu4jVersion = properties.getProperty("icu4j.version")
            ?: error("Missing icu4j.version property in nexus-loader.properties")

        val resolver = MavenLibraryResolver()

        resolver.addRepository(
            RemoteRepository.Builder(
                "central",
                "default",
                MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR
            ).build()
        )

        resolver.addDependency(
            Dependency(DefaultArtifact("com.ibm.icu:icu4j:$icu4jVersion"), null)
        )

        classpathBuilder.addLibrary(resolver)
    }
}