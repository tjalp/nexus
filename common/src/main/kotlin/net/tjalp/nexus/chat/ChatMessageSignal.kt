package net.tjalp.nexus.chat

import kotlinx.serialization.Serializable
import net.tjalp.nexus.serializer.UUIDAsStringSerializer
import net.tjalp.nexus.server.ServerInfo
import java.util.*

@Serializable
data class ChatMessageSignal(
    val origin: ServerInfo,
    @Serializable(with = UUIDAsStringSerializer::class)
    val playerId: UUID,
    val playerName: String,
    val message: String
)