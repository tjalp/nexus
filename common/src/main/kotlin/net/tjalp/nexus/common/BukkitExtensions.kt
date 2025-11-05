package net.tjalp.nexus.common

import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin

/**
 * Registers this listener to the given plugin's server.
 *
 * @param plugin The plugin to register the listener to.
 */
fun Listener.register(plugin: JavaPlugin) {
    plugin.server.pluginManager.registerEvents(this, plugin)
}

/**
 * Unregisters this listener from all handlers.
 */
fun Listener.unregister() {
    HandlerList.unregisterAll(this)
}