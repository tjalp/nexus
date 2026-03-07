package net.tjalp.nexus.redis

import net.tjalp.nexus.player.PlayerChangeServerEvent
import net.tjalp.nexus.player.PlayerOfflineEvent
import net.tjalp.nexus.player.PlayerOnlineEvent
import net.tjalp.nexus.serializer.UUIDAsStringSerializer
import net.tjalp.nexus.server.*
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

    /**
     * Server online signal key, used to indicate that a server has come online.
     */
    val SERVER_ONLINE = SignalKey(
        namespace = SignalNamespace("server:online"),
        type = ServerOnlineEvent::class,
        serializer = ServerOnlineEvent.serializer()
    )

    /**
     * Server offline signal key, used to indicate that a server has gone offline.
     */
    val SERVER_OFFLINE = SignalKey(
        namespace = SignalNamespace("server:offline"),
        type = ServerOfflineEvent::class,
        serializer = ServerOfflineEvent.serializer()
    )

    /**
     * Server heartbeat signal key, used by servers to indicate they're still alive.
     */
    val SERVER_HEARTBEAT = SignalKey(
        namespace = SignalNamespace("server:heartbeat"),
        type = ServerHeartbeat::class,
        serializer = ServerHeartbeat.serializer()
    )

    /**
     * Player join server signal key, used to indicate a player joined a server.
     */
    val PLAYER_JOIN_SERVER = SignalKey(
        namespace = SignalNamespace("server:player:join"),
        type = PlayerJoinServerEvent::class,
        serializer = PlayerJoinServerEvent.serializer()
    )

    /**
     * Player leave server signal key, used to indicate a player left a server.
     */
    val PLAYER_LEAVE_SERVER = SignalKey(
        namespace = SignalNamespace("server:player:leave"),
        type = PlayerLeaveServerEvent::class,
        serializer = PlayerLeaveServerEvent.serializer()
    )

    /**
     * Player online signal key, used to indicate a player came online.
     */
    val PLAYER_ONLINE = SignalKey(
        namespace = SignalNamespace("player:online"),
        type = PlayerOnlineEvent::class,
        serializer = PlayerOnlineEvent.serializer()
    )

    /**
     * Player offline signal key, used to indicate a player went offline.
     */
    val PLAYER_OFFLINE = SignalKey(
        namespace = SignalNamespace("player:offline"),
        type = PlayerOfflineEvent::class,
        serializer = PlayerOfflineEvent.serializer()
    )

    /**
     * Player change server signal key, used to indicate a player changed servers.
     */
    val PLAYER_CHANGE_SERVER = SignalKey(
        namespace = SignalNamespace("player:change-server"),
        type = PlayerChangeServerEvent::class,
        serializer = PlayerChangeServerEvent.serializer()
    )
}