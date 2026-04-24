package net.tjalp.nexus.backend.controlplane.provider

import java.io.File

data class ComposeServerConfig(
    val id: String,
    val stackId: String,
    val name: String,
    val serviceName: String
)

data class BareMetalServerConfig(
    val id: String,
    val stackId: String,
    val name: String,
    val workingDirectory: String?,
    val startCommand: String,
    val stopCommand: String?,
    val restartCommand: String?
)

data class ControlPlaneRuntimeConfig(
    val hostId: String,
    val composeFilePath: String,
    val composeProjectName: String,
    val composeServers: List<ComposeServerConfig>,
    val bareMetalServers: List<BareMetalServerConfig>,
    val msmpEnabledServers: Set<String>,
    val rconEnabledServers: Set<String>
) {
    companion object {
        fun fromEnv(): ControlPlaneRuntimeConfig {
            val hostId = System.getenv("CONTROL_HOST_ID") ?: "default"
            val composeFilePath = System.getenv("CONTROL_DOCKER_COMPOSE_FILE") ?: "dev/docker-compose.yml"
            val composeProjectName = System.getenv("CONTROL_DOCKER_PROJECT") ?: "nexus"

            val composeServers = parseComposeServers(
                System.getenv("CONTROL_DOCKER_SERVERS") ?: "creative|compose|Creative|creative"
            )

            val bareMetalServers = parseBareMetalServers(System.getenv("CONTROL_BARE_METAL_SERVERS") ?: "")

            val msmpServers = parseCsvSet(System.getenv("CONTROL_MSMP_SERVERS") ?: "")
            val rconServers = parseCsvSet(System.getenv("CONTROL_RCON_SERVERS") ?: "")

            return ControlPlaneRuntimeConfig(
                hostId = hostId,
                composeFilePath = File(composeFilePath).path,
                composeProjectName = composeProjectName,
                composeServers = composeServers,
                bareMetalServers = bareMetalServers,
                msmpEnabledServers = msmpServers,
                rconEnabledServers = rconServers
            )
        }

        private fun parseCsvSet(raw: String): Set<String> {
            return raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        }

        private fun parseComposeServers(raw: String): List<ComposeServerConfig> {
            if (raw.isBlank()) return emptyList()

            return raw.split(';').mapNotNull { entry ->
                val parts = entry.split('|').map { it.trim() }
                if (parts.size < 4) return@mapNotNull null
                ComposeServerConfig(
                    id = parts[0],
                    stackId = parts[1],
                    name = parts[2],
                    serviceName = parts[3]
                )
            }
        }

        private fun parseBareMetalServers(raw: String): List<BareMetalServerConfig> {
            if (raw.isBlank()) return emptyList()

            return raw.split(';').mapNotNull { entry ->
                val parts = entry.split('|').map { it.trim() }
                if (parts.size < 5) return@mapNotNull null

                BareMetalServerConfig(
                    id = parts[0],
                    stackId = parts[1],
                    name = parts[2],
                    workingDirectory = parts[3].ifBlank { null },
                    startCommand = parts[4],
                    stopCommand = parts.getOrNull(5)?.ifBlank { null },
                    restartCommand = parts.getOrNull(6)?.ifBlank { null }
                )
            }
        }
    }
}

