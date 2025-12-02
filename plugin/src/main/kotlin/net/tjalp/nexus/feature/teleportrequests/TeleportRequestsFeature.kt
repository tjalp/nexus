package net.tjalp.nexus.feature.teleportrequests

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import net.tjalp.nexus.Feature
import net.tjalp.nexus.NexusServices

object TeleportRequestsFeature : Feature {

    override val name: String = "teleport_requests"

    private var _isEnabled: Boolean = false
    override val isEnabled: Boolean
        get() = _isEnabled

    override lateinit var scheduler: CoroutineScope; private set

    override fun enable() {
        this._isEnabled = true

        scheduler = CoroutineScope(NexusServices.get<CoroutineScope>().coroutineContext + SupervisorJob())
    }

    override fun disable() {
        scheduler.cancel()

        this._isEnabled = false
    }
}