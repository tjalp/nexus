package net.tjalp.nexus.controlplane

interface ControlPlaneProvider {
    val providerId: String

    suspend fun listStacks(): List<StackSummary>

    suspend fun listServers(stackId: String): List<ManagedServer>

    suspend fun getServer(serverId: String): ManagedServer?

    suspend fun listPlayers(serverId: String): List<OnlinePlayer>

    suspend fun performAction(serverId: String, request: ControlActionRequest): ControlActionResult
}

