package net.tjalp.nexus.feature.teleportrequests

import org.spongepowered.configurate.reactive.Disposable

/**
 * Represents a teleport request.
 */
interface TeleportRequest : Disposable {

    /**
     * Send the teleport request.
     */
    fun request()

    /**
     * Accept the teleport request.
     */
    fun accept()

    /**
     * Deny the teleport request.
     */
    fun deny()

    /**
     * Cancel the teleport request.
     */
    fun cancel()
}