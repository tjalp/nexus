package net.tjalp.nexus.util

import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.NexusServices
import net.tjalp.nexus.profile.ProfilesService
import net.tjalp.nexus.profile.model.ProfileSnapshot
import org.bukkit.entity.Player
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener

/**
 * Registers this listener to the server.
 */
fun Listener.register() {
    val plugin = NexusServices.get<NexusPlugin>()
    plugin.server.pluginManager.registerEvents(this, plugin)
}

/**
 * Unregisters this listener from all handlers.
 */
fun Listener.unregister() {
    HandlerList.unregisterAll(this)
}

/**
 * Gets the profile snapshot for this player from the cached profiles.
 *
 * @throws IllegalStateException if the profile is not cached
 * @return The cached [ProfileSnapshot] for this player
 */
fun Player.profile(): ProfileSnapshot {
    return NexusServices.get<ProfilesService>().getCached(this.uniqueId)
        ?: error("Cached profile for player ${this.uniqueId} not found")
}