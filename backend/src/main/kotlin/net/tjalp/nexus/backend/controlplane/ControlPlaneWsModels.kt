package net.tjalp.nexus.backend.controlplane

import kotlinx.serialization.Serializable

@Serializable
data class ControlPlaneWsRequest(
    val type: String,
    val serverId: String? = null
)

