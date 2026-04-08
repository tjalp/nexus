package net.tjalp.nexus.feature.servers

import io.papermc.paper.connection.PlayerLoginConnection
import io.papermc.paper.event.connection.PlayerConnectionValidateLoginEvent
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.Component.translatable
import net.kyori.adventure.text.format.NamedTextColor.RED
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.NexusPlugin.scheduler
import net.tjalp.nexus.player.PlayerStatus
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

@Suppress("UnstableApiUsage")
class ServersListener(
    private val feature: ServersFeature
) : Listener {

    @EventHandler(priority = EventPriority.LOW)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        event.joinMessage(null)

        val reg = feature.playerRegistry ?: return
        scheduler.launch {
            try {
                reg.registerPlayer(
                    playerId = event.player.uniqueId,
                    username = event.player.name,
                    serverId = feature.serverInfo.id,
                    ttl = feature.heartbeatTtl
                )
            } catch (e: Exception) {
                NexusPlugin.logger.warning("[Servers] Failed to track player join: ${e.message}")
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    fun onConnectionValidate(event: PlayerConnectionValidateLoginEvent) {
        val conn = event.connection

        // DEGRADED – skip all network enforcement; local-only mode
        if (feature.networkState == NetworkState.DEGRADED) return

        val reg = feature.playerRegistry ?: return

        if (conn is PlayerLoginConnection) {
            // First stage: profile lookup. Check for duplicate sessions.
            runBlocking {
                val id = conn.authenticatedProfile?.id ?: run {
                    event.kickMessage(text("Failed to verify your profile, please try again later", RED))
                    return@runBlocking
                }

                val existingPlayer = try {
                    reg.getPlayer(id)
                } catch (_: Exception) {
                    return@runBlocking // Redis error – allow rather than blocking
                }

                if (existingPlayer != null
                    && existingPlayer.status != PlayerStatus.TRANSFERRING
                    && existingPlayer.serverId != null
                    && existingPlayer.serverId != feature.serverInfo.id
                ) {
                    event.kickMessage(translatable("multiserver.kick.already_online", RED))
                    return@runBlocking
                }
                else if (existingPlayer == null
                    || existingPlayer.serverId == feature.serverInfo.id
                    || existingPlayer.status != PlayerStatus.TRANSFERRING
                ) {
                    return@runBlocking
                }

                // Retrieve the transfer cookie asynchronously then validate synchronously.
                val cookieBytes: ByteArray? = try {
                    conn.retrieveCookie(feature.transferCookieKey).await()
                } catch (_: Exception) {
                    null
                }

                val token = feature.tokenSigner.decode(
                    cookieBytes = cookieBytes,
                    expectedPlayerId = id.toString(),
                    expectedToServerId = feature.serverInfo.id
                )

                if (token == null) {
                    event.kickMessage(
                        text("Transfer validation failed: missing or invalid transfer token. Please try again.", RED)
                    )
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        event.quitMessage(null)

        val reg = feature.playerRegistry ?: return
        scheduler.launch {
            try {
                // Don't remove transferring players – the target or TTL will handle them.
                val player = reg.getPlayer(event.player.uniqueId)
                if (player != null && player.status == PlayerStatus.TRANSFERRING) return@launch
                reg.removePlayer(event.player.uniqueId)
            } catch (e: Exception) {
                NexusPlugin.logger.warning("[Servers] Failed to track player quit: ${e.message}")
            }
        }
    }
}