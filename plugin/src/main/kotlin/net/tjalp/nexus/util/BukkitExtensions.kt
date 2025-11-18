package net.tjalp.nexus.util

import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.NexusServices
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