package net.tjalp.nexus.backend.controlplane.provider

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.tjalp.nexus.backend.controlplane.ControlPlaneEventHub
import net.tjalp.nexus.backend.controlplane.adapter.TransportAdapterChain
import net.tjalp.nexus.controlplane.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class BareMetalControlPlaneProvider(
    private val config: ControlPlaneRuntimeConfig,
    private val adapterChain: TransportAdapterChain,
    private val eventHub: ControlPlaneEventHub,
    private val scope: CoroutineScope
) : ControlPlaneProvider {
    override val providerId: String = "bare-metal"

    private val processes = ConcurrentHashMap<String, Process>()

    override suspend fun listStacks(): List<StackSummary> {
        val stackIds = config.bareMetalServers.map { it.stackId }.toSet()
        return stackIds.map { stackId ->
            StackSummary(
                id = stackId,
                name = "Bare-metal $stackId",
                runtimeType = RuntimeType.BARE_METAL,
                hostId = config.hostId
            )
        }
    }

    override suspend fun listServers(stackId: String): List<ManagedServer> {
        return config.bareMetalServers.filter { it.stackId == stackId }.map { toServer(it) }
    }

    override suspend fun getServer(serverId: String): ManagedServer? {
        val cfg = config.bareMetalServers.firstOrNull { it.id == serverId } ?: return null
        return toServer(cfg)
    }

    override suspend fun listPlayers(serverId: String): List<OnlinePlayer> {
        val server = getServer(serverId) ?: return emptyList()
        return adapterChain.listPlayers(server)
    }

    override suspend fun performAction(serverId: String, request: ControlActionRequest): ControlActionResult {
        val cfg = config.bareMetalServers.firstOrNull { it.id == serverId }
            ?: return ControlActionResult(false, "Unknown bare-metal server '$serverId'")

        if (request.action == ServerAction.CUSTOM) {
            val server = toServer(cfg)
            return adapterChain.performCustomAction(server, request)
                ?: ControlActionResult(false, "No adapter accepted this custom action")
        }

        return when (request.action) {
            ServerAction.START -> start(cfg)
            ServerAction.STOP -> stop(cfg)
            ServerAction.RESTART -> restart(cfg)
            ServerAction.CUSTOM -> ControlActionResult(false, "Unsupported action")
        }
    }

    private suspend fun start(cfg: BareMetalServerConfig): ControlActionResult {
        val current = processes[cfg.id]
        if (current != null && current.isAlive) {
            return ControlActionResult(false, "Server '${cfg.id}' is already running")
        }

        val process = CommandExecution.start(
            command = CommandExecution.shellCommand(cfg.startCommand),
            workingDirectory = cfg.workingDirectory
        )

        processes[cfg.id] = process
        bindConsoleStream(cfg.id, process)

        eventHub.publish(
            ControlPlaneEvent(
                serverId = cfg.id,
                type = ControlPlaneEventType.STATE_CHANGED,
                message = "Server process started",
                state = ManagedServerState.RUNNING
            )
        )

        return ControlActionResult(true, "Start command launched")
    }

    private suspend fun stop(cfg: BareMetalServerConfig): ControlActionResult {
        val process = processes[cfg.id]

        if (cfg.stopCommand != null) {
            val result = CommandExecution.runAndWait(
                command = CommandExecution.shellCommand(cfg.stopCommand),
                workingDirectory = cfg.workingDirectory
            )

            if (process != null && process.isAlive) {
                process.waitFor(5, TimeUnit.SECONDS)
                if (process.isAlive) {
                    process.destroy()
                }
            }
            processes.remove(cfg.id)

            val accepted = result.exitCode == 0
            eventHub.publish(
                ControlPlaneEvent(
                    serverId = cfg.id,
                    type = if (accepted) ControlPlaneEventType.STATE_CHANGED else ControlPlaneEventType.ERROR,
                    message = if (accepted) "Stop command completed" else result.stderr.ifBlank { "Stop command failed" },
                    state = if (accepted) ManagedServerState.STOPPED else ManagedServerState.ERROR
                )
            )
            return ControlActionResult(accepted, if (accepted) "Stopped" else result.stderr.ifBlank { "Stop failed" })
        }

        if (process == null) {
            return ControlActionResult(false, "Server '${cfg.id}' is not running")
        }

        process.destroy()
        process.waitFor(5, TimeUnit.SECONDS)
        if (process.isAlive) {
            process.destroyForcibly()
        }
        processes.remove(cfg.id)

        eventHub.publish(
            ControlPlaneEvent(
                serverId = cfg.id,
                type = ControlPlaneEventType.STATE_CHANGED,
                message = "Process stopped",
                state = ManagedServerState.STOPPED
            )
        )

        return ControlActionResult(true, "Stopped process")
    }

    private suspend fun restart(cfg: BareMetalServerConfig): ControlActionResult {
        if (cfg.restartCommand != null) {
            val result = CommandExecution.runAndWait(
                command = CommandExecution.shellCommand(cfg.restartCommand),
                workingDirectory = cfg.workingDirectory
            )
            val accepted = result.exitCode == 0

            eventHub.publish(
                ControlPlaneEvent(
                    serverId = cfg.id,
                    type = if (accepted) ControlPlaneEventType.STATE_CHANGED else ControlPlaneEventType.ERROR,
                    message = if (accepted) "Restart command completed" else result.stderr.ifBlank { "Restart failed" },
                    state = if (accepted) ManagedServerState.RUNNING else ManagedServerState.ERROR
                )
            )
            return ControlActionResult(accepted, if (accepted) "Restarted" else result.stderr.ifBlank { "Restart failed" })
        }

        val stopResult = stop(cfg)
        if (!stopResult.accepted) return stopResult
        return start(cfg)
    }

    private fun bindConsoleStream(serverId: String, process: Process) {
        scope.launch(Dispatchers.IO) {
            process.inputStream.bufferedReader().use { reader ->
                reader.lineSequence().forEach { line ->
                    eventHub.publish(
                        ControlPlaneEvent(
                            serverId = serverId,
                            type = ControlPlaneEventType.CONSOLE_LINE,
                            message = line,
                            state = ManagedServerState.RUNNING
                        )
                    )
                }
            }
        }

        scope.launch(Dispatchers.IO) {
            process.errorStream.bufferedReader().use { reader ->
                reader.lineSequence().forEach { line ->
                    eventHub.publish(
                        ControlPlaneEvent(
                            serverId = serverId,
                            type = ControlPlaneEventType.ERROR,
                            message = line,
                            state = ManagedServerState.ERROR
                        )
                    )
                }
            }
        }
    }

    private suspend fun toServer(cfg: BareMetalServerConfig): ManagedServer {
        val process = processes[cfg.id]
        val state = when {
            process == null -> ManagedServerState.STOPPED
            process.isAlive -> ManagedServerState.RUNNING
            else -> ManagedServerState.STOPPED
        }

        val baseCapabilities = linkedSetOf(
            ServerCapability.START,
            ServerCapability.STOP,
            ServerCapability.RESTART,
            ServerCapability.CONSOLE_READ
        )

        val base = ManagedServer(
            id = cfg.id,
            stackId = cfg.stackId,
            name = cfg.name,
            state = state,
            transports = emptySet(),
            capabilities = baseCapabilities,
            metadata = mapOf(
                "runtime" to "bare-metal",
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


