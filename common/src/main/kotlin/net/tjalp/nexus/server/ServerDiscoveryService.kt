package net.tjalp.nexus.server

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable

/**
 * Service for discovering servers from a centralized discovery endpoint.
 *
 * This allows maintaining a single source of truth for the server list,
 * rather than configuring staticServers on every server instance.
 *
 * The discovery URL should return a JSON array of server URLs:
 * ```json
 * ["http://server1:8080", "http://server2:8080", "http://server3:8080"]
 * ```
 *
 * @param httpClient HTTP client for making requests
 * @param discoveryUrl URL to fetch the server list from
 */
class ServerDiscoveryService(
    private val httpClient: HttpClient,
    private val discoveryUrl: String
) {

    @Serializable
    private data class ServerList(
        val servers: List<String>
    )

    /**
     * Fetch the list of servers from the discovery endpoint.
     *
     * Supports two JSON formats:
     * 1. Simple array: `["http://server1:8080", "http://server2:8080"]`
     * 2. Object with servers field: `{"servers": ["http://server1:8080", ...]}`
     *
     * @return List of server URLs, or empty list if discovery fails
     */
    suspend fun fetchServerList(): List<String> {
        if (discoveryUrl.isBlank()) {
            return emptyList()
        }

        return try {
            val response = httpClient.get(discoveryUrl) {
                timeout {
                    requestTimeoutMillis = 5000
                }
            }

            if (response.status != HttpStatusCode.OK) {
                println("Discovery service returned ${response.status}: $discoveryUrl")
                return emptyList()
            }

            // Try parsing as simple array first
            try {
                response.body<List<String>>()
            } catch (e: Exception) {
                // Fall back to object format with "servers" field
                try {
                    val serverList: ServerList = response.body()
                    serverList.servers
                } catch (e2: Exception) {
                    println("Failed to parse discovery response from $discoveryUrl: ${e2.message}")
                    emptyList()
                }
            }
        } catch (e: Exception) {
            // Discovery service not reachable - this is expected during normal operation
            // Will fall back to staticServers configuration
            emptyList()
        }
    }

    /**
     * Check if discovery is enabled (URL is configured)
     */
    fun isEnabled(): Boolean = discoveryUrl.isNotBlank()
}
