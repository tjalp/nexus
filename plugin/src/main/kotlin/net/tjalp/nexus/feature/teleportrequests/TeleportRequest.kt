package net.tjalp.nexus.feature.teleportrequests

/**
 * Represents a teleport request.
 */
interface TeleportRequest {

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