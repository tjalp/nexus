package net.tjalp.nexus.feature.disguises

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import net.tjalp.nexus.Feature
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.feature.disguises.provider.LibsDisguisesDisguiseProvider
import net.tjalp.nexus.feature.disguises.provider.NexusDisguiseProvider
import org.bukkit.Bukkit

object DisguiseFeature : Feature {

    override val name: String = "disguises"

    private var _isEnabled: Boolean = false
    override val isEnabled: Boolean
        get() = _isEnabled

    override lateinit var scheduler: CoroutineScope; private set

    var provider: DisguiseProvider? = null; private set

    override fun enable() {
        this._isEnabled = true

        scheduler = CoroutineScope(NexusPlugin.scheduler.coroutineContext + SupervisorJob())
        provider = if (Bukkit.getPluginManager()
                .isPluginEnabled("LibsDisguises")
        ) LibsDisguisesDisguiseProvider() else NexusDisguiseProvider()
    }

    override fun disable() {
        provider?.dispose()
        scheduler.cancel()

        this._isEnabled = false
    }
}