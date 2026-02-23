package net.tjalp.nexus.redis

import net.tjalp.nexus.serializer.UUIDAsStringSerializer
import java.util.*

/**
 * Object containing predefined signal keys for various events in the application.
 */
object Signals {

    /**
     * Profile update signal key, used to indicate that a profile has been updated.
     */
    val PROFILE_UPDATE = SignalKey(
        namespace = SignalNamespace("profile:update"),
        type = UUID::class,
        serializer = UUIDAsStringSerializer
    )
}