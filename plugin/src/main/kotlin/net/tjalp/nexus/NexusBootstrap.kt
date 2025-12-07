package net.tjalp.nexus

import io.papermc.paper.plugin.bootstrap.BootstrapContext
import io.papermc.paper.plugin.bootstrap.PluginBootstrap
import io.papermc.paper.plugin.bootstrap.PluginProviderContext
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import org.bukkit.plugin.java.JavaPlugin

@Suppress("UnstableApiUsage", "unused")
class NexusBootstrap : PluginBootstrap {

    override fun bootstrap(context: BootstrapContext) {
        context.lifecycleManager.registerEventHandler(LifecycleEvents.DATAPACK_DISCOVERY) { event ->
            val uri = javaClass.getResource("/nexus_datapack")?.toURI()
                ?: throw IllegalStateException("Could not find nexus_datapack resource")

            event.registrar().discoverPack(uri, "provided")
        }
    }

    override fun createPlugin(context: PluginProviderContext): JavaPlugin = NexusPlugin
}