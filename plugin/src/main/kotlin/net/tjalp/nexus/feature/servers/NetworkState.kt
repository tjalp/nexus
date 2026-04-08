package net.tjalp.nexus.feature.servers

/**
 * Represents the current operational state of the network integration layer.
 *
 * - [ONLINE]  – Redis is reachable; full network features are available (transfers, session checks, etc.)
 * - [DEGRADED] – Redis is unreachable; network features are disabled until connectivity is restored.
 */
enum class NetworkState {
    ONLINE,
    DEGRADED
}
