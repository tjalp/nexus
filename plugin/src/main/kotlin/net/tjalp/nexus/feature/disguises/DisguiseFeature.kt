package net.tjalp.nexus.feature.disguises

import net.tjalp.nexus.Feature
import net.tjalp.nexus.feature.disguises.provider.LibsDisguisesDisguiseProvider
import net.tjalp.nexus.feature.disguises.provider.NexusDisguiseProvider
import org.bukkit.Bukkit

object DisguiseFeature : Feature("disguises") {

    var provider: DisguiseProvider? = null; private set

    override fun enable() {
        super.enable()

        provider = if (Bukkit.getPluginManager()
                .isPluginEnabled("LibsDisguises")
        ) LibsDisguisesDisguiseProvider() else NexusDisguiseProvider()
    }

    override fun disable() {
        provider?.dispose()

        super.disable()
    }
}