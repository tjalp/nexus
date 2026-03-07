package net.tjalp.nexus.redis

import io.lettuce.core.ExperimentalLettuceCoroutinesApi

/**
 * Configuration utilities for Redis
 */
@OptIn(ExperimentalLettuceCoroutinesApi::class)
object RedisConfig {

    /**
     * Configure Redis to enable keyspace notifications for expired events.
     * This is required for automatic server crash detection.
     *
     * Enables "Ex" notification (expired events in keyevent format).
     *
     * @param redis The Redis controller to configure
     */
    suspend fun enableKeyspaceNotifications(redis: RedisController) {
        try {
            // Get current config - returns a Map<String, String>
            val currentConfig = redis.query.configGet("notify-keyspace-events")
            val currentValue = currentConfig?.get("notify-keyspace-events") ?: ""

            // Check if Ex is already enabled
            if (!currentValue.contains("E") || !currentValue.contains("x")) {
                // Enable keyevent notifications for expired events
                // E = keyevent events, x = expired events
                val newConfig = buildString {
                    append(currentValue)
                    if (!currentValue.contains("E")) append("E")
                    if (!currentValue.contains("x")) append("x")
                }

                redis.query.configSet("notify-keyspace-events", newConfig)
                println("✓ Enabled Redis keyspace notifications: '$newConfig'")
            } else {
                println("✓ Redis keyspace notifications already enabled: '$currentValue'")
            }
        } catch (e: Exception) {
            // If config commands are disabled or fail, log warning
            // The system will still work, but crash detection will rely on TTL expiration only
            println("⚠ Warning: Could not enable Redis keyspace notifications: ${e.message}")
            println("  Server crash detection will rely on TTL expiration only.")
        }
    }

    /**
     * Validate that Redis is properly configured for the Nexus network.
     * Checks for keyspace notifications and prints diagnostic information.
     *
     * @param redis The Redis controller to validate
     */
    suspend fun validateConfiguration(redis: RedisController) {
        try {
            // Check Redis version
            val info = redis.query.info("server")
            val versionLine = info?.lines()?.find { it.startsWith("redis_version:") }
            if (versionLine != null) {
                println("Redis Server: $versionLine")
            }

            // Check keyspace notifications
            val config = redis.query.configGet("notify-keyspace-events")
            val value = config?.get("notify-keyspace-events") ?: ""

            val hasKeyevent = value.contains("E")
            val hasExpired = value.contains("x")

            println("Redis Configuration:")
            println("  notify-keyspace-events: '$value'")
            println("  Keyevent notifications (E): ${if (hasKeyevent) "✓ Enabled" else "✗ Disabled"}")
            println("  Expired events (x): ${if (hasExpired) "✓ Enabled" else "✗ Disabled"}")

            if (!hasKeyevent || !hasExpired) {
                println("  ⚠ Warning: Keyspace notifications not fully enabled")
                println("  Automatic crash detection may not work properly")
                println("  Run: CONFIG SET notify-keyspace-events Ex")
            } else {
                println("  ✓ Nexus network ready for automatic crash detection")
            }
        } catch (e: Exception) {
            println("⚠ Warning: Could not validate Redis configuration: ${e.message}")
        }
    }
}




