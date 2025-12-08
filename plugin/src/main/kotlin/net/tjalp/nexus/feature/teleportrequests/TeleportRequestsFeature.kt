package net.tjalp.nexus.feature.teleportrequests

import net.tjalp.nexus.Feature

object TeleportRequestsFeature : Feature("teleport_requests") {

    override fun disable() {
        PlayerTeleportRequest.clearAllRequests()

        super.disable()
    }
}