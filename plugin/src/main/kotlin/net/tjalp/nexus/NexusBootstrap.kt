package net.tjalp.nexus

import io.papermc.paper.plugin.bootstrap.BootstrapContext
import io.papermc.paper.plugin.bootstrap.PluginBootstrap
import io.papermc.paper.plugin.bootstrap.PluginProviderContext
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import net.tjalp.nexus.config.NexusConfig
import org.bukkit.plugin.java.JavaPlugin

@Suppress("UnstableApiUsage", "unused")
class NexusBootstrap : PluginBootstrap {

    override fun bootstrap(context: BootstrapContext) {
        val config = NexusConfig.reload(context.dataDirectory)

        context.lifecycleManager.registerEventHandler(LifecycleEvents.DATAPACK_DISCOVERY) { event ->
            val uri = javaClass.getResource("/nexus_datapack")?.toURI()
                ?: throw IllegalStateException("Could not find nexus_datapack resource")

            event.registrar().discoverPack(uri, "provided")
        }

        // TODO whatever this is. We can't do this because we need to format the locale, but we don't have that at this point
//        context.lifecycleManager.registerEventHandler(RegistryEvents.DIALOG.compose()
//            .newHandler { event ->
//                event.registry().register(RecommendationsDialog.KEY) { builder ->
//                    builder
//                        .base(RecommendationsDialog.base(
//                            config = config.features.notices.recommendations, locale = null
//                        ))
//                        .type(RecommendationsDialog.type())
//                }
//            })
    }

    override fun createPlugin(context: PluginProviderContext): JavaPlugin = NexusPlugin
}