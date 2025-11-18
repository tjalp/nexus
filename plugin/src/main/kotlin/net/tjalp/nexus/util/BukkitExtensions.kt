package net.tjalp.nexus.util

import net.tjalp.nexus.NexusServices
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin

/**
 * Registers this listener to the server.
 */
fun Listener.register() {
    val plugin = NexusServices.get<JavaPlugin>()
    plugin.server.pluginManager.registerEvents(this, plugin)
}

/**
 * Unregisters this listener from all handlers.
 */
fun Listener.unregister() {
    HandlerList.unregisterAll(this)
}