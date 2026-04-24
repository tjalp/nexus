package net.tjalp.nexus.backend.controlplane.provider

import net.tjalp.nexus.backend.controlplane.ControlPlaneEventHub
import net.tjalp.nexus.backend.controlplane.adapter.TransportAdapterChain
import net.tjalp.nexus.controlplane.*
import java.util.concurrent.ConcurrentHashMap

class DockerComposeControlPlaneProvider(
    private val config: ControlPlaneRuntimeConfig,
    private val adapterChain: TransportAdapterChain,
    private val eventHub: ControlPlaneEventHub
) : ControlPlaneProvider {
    override val providerId: String = "docker-compose"

    private val states = ConcurrentHashMap<String, ManagedServerState>()

    override suspend fun listStacks(): List<StackSummary> {
        val stackIds = config.composeServers.map { it.stackId }.toSet()
        return stackIds.map { stackId ->
            StackSummary(
                id = stackId,
                name = "Compose $stackId",
                runtimeType = RuntimeType.DOCKER_COMPOSE,
                hostId = config.hostId
            )
        }
    }

    override suspend fun listServers(stackId: String): List<ManagedServer> {
        return config.composeServers
            .filter { it.stackId == stackId }
            .map { toServer(it) }
    }

    override suspend fun getServer(serverId: String): ManagedServer? {
        val server = config.composeServers.firstOrNull { it.id == serverId } ?: return null
        return toServer(server)
    }

    override suspend fun listPlayers(serverId: String): List<OnlinePlayer> {
        val server = getServer(serverId) ?: return emptyList()
        return adapterChain.listPlayers(server)
    }

    override suspend fun performAction(serverId: String, request: ControlActionRequest): ControlActionResult {
        val cfg = config.composeServers.firstOrNull { it.id == serverId }
            ?: return ControlActionResult(false, "Unknown compose server '$serverId'")

        if (request.action == ServerAction.CUSTOM) {
            val server = toServer(cfg)
            val custom = adapterChain.performCustomAction(server, request)
            return custom ?: ControlActionResult(false, "No adapter accepted this custom action")
        }

        val command = when (request.action) {
            ServerAction.START -> listOf("docker", "compose", "-f", config.composeFilePath, "up", "-d", cfg.serviceName)
            ServerAction.STOP -> listOf("docker", "compose", "-f", config.composeFilePath, "stop", cfg.serviceName)
            ServerAction.RESTART -> listOf("docker", "compose", "-f", config.composeFilePath, "restart", cfg.serviceName)
            ServerAction.CUSTOM -> error("Custom action should be handled before command dispatch")
        }

        val result = CommandExecution.runAndWait(command)
        val accepted = result.exitCode == 0
        val newState = when (request.action) {
            ServerAction.START, ServerAction.RESTART -> if (accepted) ManagedServerState.RUNNING else ManagedServerState.ERROR
            ServerAction.STOP -> if (accepted) ManagedServerState.STOPPED else ManagedServerState.ERROR
            ServerAction.CUSTOM -> error("Custom action should be handled before state mapping")
        }
        states[cfg.id] = newState

        eventHub.publish(
            ControlPlaneEvent(
                serverId = cfg.id,
                type = if (accepted) ControlPlaneEventType.STATE_CHANGED else ControlPlaneEventType.ERROR,
                message = if (accepted) {
                    "${request.action.name.lowercase()} completed for compose service '${cfg.serviceName}'"
                } else {
                    result.stderr.ifBlank { "Command failed with exit code ${result.exitCode}" }
                },
                state = newState
            )
        )

        return ControlActionResult(
            accepted = accepted,
            message = if (accepted) result.stdout.ifBlank { "OK" } else result.stderr.ifBlank { "Command failed" }
        )
    }

    private suspend fun toServer(cfg: ComposeServerConfig): ManagedServer {
        val baseCapabilities = linkedSetOf(ServerCapability.START, ServerCapability.STOP, ServerCapability.RESTART)
        val state = states[cfg.id] ?: ManagedServerState.UNKNOWN

        val base = ManagedServer(
            id = cfg.id,
            stackId = cfg.stackId,
            name = cfg.name,
            state = state,
            transports = emptySet(),
            capabilities = baseCapabilities,
            metadata = mapOf(
                "service" to cfg.serviceName,
                "runtime" to "compose",
                "msmp" to (cfg.id in config.msmpEnabledServers).toString()
            )
        )

        val transports = adapterChain.availableTransports(base)
        val capabilities = baseCapabilities + adapterChain.capabilities(base)

        return base.copy(
            transports = transports,
            capabilities = capabilities
        )
    }
}


