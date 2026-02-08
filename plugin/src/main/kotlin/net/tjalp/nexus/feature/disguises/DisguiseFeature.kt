package net.tjalp.nexus.feature.disguises

import net.tjalp.nexus.Feature
import net.tjalp.nexus.feature.FeatureKeys.DISGUISES
import net.tjalp.nexus.feature.disguises.provider.LibsDisguisesDisguiseProvider
import net.tjalp.nexus.feature.disguises.provider.NexusDisguiseProvider
import org.bukkit.Bukkit

class DisguiseFeature : Feature(DISGUISES) {

    var provider: DisguiseProvider? = null; private set

    override fun onEnable() {
        provider = if (Bukkit.getPluginManager()
                .isPluginEnabled("LibsDisguises")
        ) LibsDisguisesDisguiseProvider() else NexusDisguiseProvider(this)
    }

    override fun onDisposed() {
        provider?.dispose()
    }
}