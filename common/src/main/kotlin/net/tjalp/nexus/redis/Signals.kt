package net.tjalp.nexus.redis

import net.tjalp.nexus.chat.ChatMessageSignal
import net.tjalp.nexus.player.PlayerChangeServerEvent
import net.tjalp.nexus.player.PlayerOfflineEvent
import net.tjalp.nexus.player.PlayerOnlineEvent
import net.tjalp.nexus.serializer.UUIDAsStringSerializer
import net.tjalp.nexus.server.ServerHeartbeat
import net.tjalp.nexus.server.ServerOfflineEvent
import net.tjalp.nexus.server.ServerOnlineEvent
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

    val CHAT_MESSAGE = SignalKey(
        namespace = SignalNamespace("chat:message"),
        type = ChatMessageSignal::class,
        serializer = ChatMessageSignal.serializer()
    )
}