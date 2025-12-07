package net.tjalp.nexus.feature.teleportrequests

import net.tjalp.nexus.feature.Feature

object TeleportRequestsFeature : Feature("teleport_requests") {

    override fun disable() {
        PlayerTeleportRequest.clearAllRequests()

        super.disable()
    }
}