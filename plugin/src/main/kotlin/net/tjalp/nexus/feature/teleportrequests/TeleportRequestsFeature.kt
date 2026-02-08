package net.tjalp.nexus.feature.teleportrequests

import net.tjalp.nexus.Feature
import net.tjalp.nexus.feature.FeatureKeys.TELEPORT_REQUESTS

class TeleportRequestsFeature : Feature(TELEPORT_REQUESTS) {

    override fun onDisposed() {
        PlayerTeleportRequest.requests().forEach { it.dispose() }
    }
}