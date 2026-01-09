package net.tjalp.nexus.feature.teleportrequests

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component.*
import net.kyori.adventure.text.event.ClickEvent.callback
import net.kyori.adventure.text.format.NamedTextColor.*
import net.kyori.adventure.text.format.TextDecoration.BOLD
import net.kyori.adventure.text.minimessage.translation.Argument
import net.tjalp.nexus.Constants.PRIMARY_COLOR
import net.tjalp.nexus.NexusPlugin
import org.bukkit.entity.Player
import kotlin.time.Duration.Companion.seconds

class PlayerTeleportRequest(
    val source: Player,
    val target: Player
) : TeleportRequest {

    private val scheduler; get() = TeleportRequestsFeature.scheduler
    private var expireJob: Job? = null

    override fun request() {
        requests += this

        expireJob = this.scheduler.launch {
            delay(NexusPlugin.configuration.features.teleportRequests.requestTimeoutSeconds.seconds)

            requests -= this@PlayerTeleportRequest

            val expiredSender = translatable(
                "teleportrequest.expired.source",
                GRAY,
                Argument.component("target", target.name().colorIfAbsent(PRIMARY_COLOR))
            )
            val expiredReceiver = translatable(
                "teleportrequest.expired.target",
                GRAY,
                Argument.component("source", source.name().colorIfAbsent(PRIMARY_COLOR))
            )

            source.sendMessage(expiredSender)
            target.sendMessage(expiredReceiver)
        }

        source.sendMessage(
            translatable(
                "teleportrequest.sent",
                GRAY,
                Argument.component("target", target.name().colorIfAbsent(PRIMARY_COLOR)),
                Argument.component("cancel", translatable("teleportrequest.cancel", RED, BOLD)
                    .clickEvent(callback { cancel() }))
            )
        )
        target.sendMessage(
            textOfChildren(
                newline(),
                translatable(
                    "teleportrequest.received",
                    GRAY,
                    Argument.component("source", source.name().colorIfAbsent(PRIMARY_COLOR)),
                    Argument.component("accept", translatable("teleportrequest.accept", GREEN, BOLD)
                        .clickEvent(callback { accept() })),
                    Argument.component("deny", translatable("teleportrequest.deny", RED, BOLD)
                        .clickEvent(callback { deny() }))
                ),
                newline()
            )
        )

        target.playSound(
            Sound.sound(Key.key("entity.item.pickup"), Sound.Source.PLAYER, 1f, 1f),
            Sound.Emitter.self()
        )
    }

    override fun accept() {
        if (requests.none { it.target == target && it.source == source }) return

        requests -= this
        expireJob?.cancel()

        target.sendMessage(
            translatable(
                "teleportrequest.accepted.target",
                GREEN,
                Argument.component("source", source.name().colorIfAbsent(PRIMARY_COLOR))
            )
        )
        source.sendMessage(
            translatable(
                "teleportrequest.accepted.source",
                GREEN,
                Argument.component("target", target.name().colorIfAbsent(PRIMARY_COLOR))
            )
        )

        val successful = source.teleport(this.target)

        if (successful) {
            val location = this.target.location
            target.world.playSound(
                Sound.sound(Key.key("item.chorus_fruit.teleport"), Sound.Source.PLAYER, 1f, 1f),
                location.x, location.y, location.z
            )
            return
        }

        source.sendMessage(translatable("teleportrequest.teleport.failed", RED))
    }

    override fun deny() {
        if (requests.none { it.target == target && it.source == source }) return

        requests -= this
        expireJob?.cancel()

        target.sendMessage(
            translatable(
                "teleportrequest.denied.target",
                RED,
                Argument.component("source", source.name().colorIfAbsent(PRIMARY_COLOR))
            )
        )
        source.sendMessage(
            translatable(
                "teleportrequest.denied.source",
                RED,
                Argument.component("target", target.name().colorIfAbsent(PRIMARY_COLOR))
            )
        )
    }

    override fun cancel() {
        if (requests.none { it.target == target && it.source == source }) return

        requests -= this
        expireJob?.cancel()

        target.sendMessage(
            translatable(
                "teleportrequest.cancelled.target",
                GRAY,
                Argument.component("source", source.name().colorIfAbsent(PRIMARY_COLOR))
            )
        )
        source.sendMessage(
            translatable(
                "teleportrequest.cancelled.source",
                GRAY,
                Argument.component("target", target.name().colorIfAbsent(PRIMARY_COLOR))
            )
        )
    }

    override fun dispose() {
        requests -= this
        expireJob?.cancel()
    }

    companion object {
        private val requests = mutableListOf<PlayerTeleportRequest>()

        fun requests(): List<PlayerTeleportRequest> = this.requests.toList()
    }
}