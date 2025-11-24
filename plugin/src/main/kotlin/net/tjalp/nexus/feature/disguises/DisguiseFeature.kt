package net.tjalp.nexus.feature.disguises

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import net.tjalp.nexus.Feature
import net.tjalp.nexus.NexusServices
import net.tjalp.nexus.feature.disguises.provider.LibsDisguisesDisguiseProvider
import net.tjalp.nexus.feature.disguises.provider.NexusDisguiseProvider
import org.bukkit.Bukkit

object DisguiseFeature : Feature {

    override val name: String = "disguises"
    override lateinit var scheduler: CoroutineScope; private set

    var provider: DisguiseProvider? = null; private set

    override fun enable() {
        scheduler = CoroutineScope(NexusServices.get<CoroutineScope>().coroutineContext + SupervisorJob())
        provider = if (Bukkit.getPluginManager()
                .isPluginEnabled("LibsDisguises")
        ) LibsDisguisesDisguiseProvider() else NexusDisguiseProvider()
    }

    override fun disable() {
        provider?.dispose()
        scheduler.cancel()
    }
}