package net.tjalp.nexus.controlplane

import kotlinx.serialization.Serializable

@Serializable
enum class RuntimeType {
    DOCKER_COMPOSE,
    BARE_METAL
}

@Serializable
enum class ControlTransport {
    MSMP,
    PLUGIN,
    RCON,
    QUERY
}

@Serializable
enum class ManagedServerState {
    STARTING,
    RUNNING,
    STOPPING,
    STOPPED,
    ERROR,
    UNKNOWN
}

@Serializable
enum class ServerCapability {
    START,
    STOP,
    RESTART,
    PLAYERS_READ,
    CONSOLE_READ,
    CONSOLE_WRITE,
    FILES_READ,
    FILES_WRITE,
    CUSTOM_ACTION
}

@Serializable
enum class ServerAction {
    START,
    STOP,
    RESTART,
    CUSTOM
}

@Serializable
data class StackSummary(
    val id: String,
    val name: String,
    val runtimeType: RuntimeType,
    val hostId: String
)

@Serializable
data class ManagedServer(
    val id: String,
    val stackId: String,
    val name: String,
    val state: ManagedServerState,
    val transports: Set<ControlTransport>,
    val capabilities: Set<ServerCapability>,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
data class OnlinePlayer(
    val id: String,
    val username: String
)

@Serializable
data class ControlActionRequest(
    val action: ServerAction,
    val customAction: String? = null,
    val payload: String? = null
)

@Serializable
data class ControlActionResult(
    val accepted: Boolean,
    val message: String
)

@Serializable
enum class ControlPlaneEventType {
    STATE_CHANGED,
    CONSOLE_LINE,
    ACTION_RESULT,
    INFO,
    ERROR
}

@Serializable
data class ControlPlaneEvent(
    val serverId: String,
    val type: ControlPlaneEventType,
    val message: String,
    val state: ManagedServerState? = null,
    val timestampMillis: Long = System.currentTimeMillis()
)

