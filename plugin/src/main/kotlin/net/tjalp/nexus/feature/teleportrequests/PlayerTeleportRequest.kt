package net.tjalp.nexus.feature.teleportrequests

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.event.ClickEvent.callback
import net.kyori.adventure.text.format.NamedTextColor.*
import net.kyori.adventure.text.format.TextDecoration.BOLD
import net.tjalp.nexus.Constants.PRIMARY_COLOR
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.NexusServices
import org.bukkit.entity.Player
import kotlin.time.Duration.Companion.minutes

class PlayerTeleportRequest(
    val source: Player,
    val target: Player
) : TeleportRequest {

    private val nexus: NexusPlugin; get() = NexusServices.get<NexusPlugin>()
    private val scheduler; get() = TeleportRequestsFeature.scheduler
    private var expireJob: Job? = null

    override fun request() {
//        if (source == target) {
//            this.source.sendMessage(text("You cannot send a request to yourself, silly!", RED))
//            return
//        }
        if (requests.any { it.target == target && it.source == source }) {
            this.source.sendMessage(
                text("You already have an active teleport request to", RED)
                    .appendSpace().append(this.target.name().colorIfAbsent(PRIMARY_COLOR))
            )
            return
        }

        requests += this

        this.expireJob = this.scheduler.launch {
            delay(1.5.minutes)

            requests -= this@PlayerTeleportRequest

            source.sendMessage(
                text("Your teleport request to", GRAY)
                    .appendSpace().append(target.name().colorIfAbsent(PRIMARY_COLOR))
                    .appendSpace().append(text("has expired"))
            )
            target.sendMessage(
                text().color(GRAY)
                    .append(source.name())
                    .append(text("'s teleport request has expired"))
            )
        }

        this.source.sendMessage(
            text("You sent a teleport request to ", GRAY)
                .append(this.target.name().colorIfAbsent(PRIMARY_COLOR))
                .appendSpace().append(
                    text("CANCEL", RED, BOLD)
                        .clickEvent(callback { cancel() })
                )
        )
        this.target.sendMessage(
            text().color(GRAY)
                .appendNewline()
                .append(text("You've received a teleport request from"))
                .appendSpace().append(this.source.name().colorIfAbsent(PRIMARY_COLOR))
                .appendNewline().append(text("Would you like to accept this request?"))
                .appendNewline()
                .appendNewline().append(text("YES", GREEN, BOLD).clickEvent(callback { accept() }))
                .appendSpace().append(text("NO", RED, BOLD).clickEvent(callback { deny() }))
                .appendNewline()
        )
        this.target.playSound(
            Sound.sound(Key.key("entity.item.pickup"), Sound.Source.PLAYER, 1f, 1f),
            Sound.Emitter.self()
        )
    }

    override fun accept() {
        if (requests.none { it.target == target && it.source == source }) return

        requests -= this
        this.expireJob?.cancel()

        this.target.sendMessage(
            text("You've accepted", GREEN)
                .appendSpace().append(this.source.name().colorIfAbsent(PRIMARY_COLOR))
                .append(text("'s teleport request"))
        )
        this.source.sendMessage(
            text("Your teleport request to", GREEN)
                .appendSpace().append(this.target.name().colorIfAbsent(PRIMARY_COLOR))
                .appendSpace().append(text("has been accepted"))
        )

        val successful = this.source.teleport(this.target)

        if (successful) {
            val location = this.target.location
            this.target.world.playSound(
                Sound.sound(Key.key("item.chorus_fruit.teleport"), Sound.Source.PLAYER, 1f, 1f),
                location.x, location.y, location.z
            )
            return
        }

        this.source.sendMessage(text("Teleportation failed! Try again later", RED))
    }

    override fun deny() {
        if (requests.none { it.target == target && it.source == source }) return

        requests -= this
        this.expireJob?.cancel()

        this.target.sendMessage(
            text("You've denied", RED)
                .appendSpace().append(this.source.name().colorIfAbsent(PRIMARY_COLOR))
                .append(text("'s teleport request"))
        )
        this.source.sendMessage(
            text("Your teleport request to", RED)
                .appendSpace().append(this.target.name().colorIfAbsent(PRIMARY_COLOR))
                .appendSpace().append(text("has been denied"))
        )
    }

    override fun cancel() {
        if (requests.none { it.target == target && it.source == source }) return

        requests -= this
        this.expireJob?.cancel()

        this.target.sendMessage(
            text().color(GRAY)
                .append(this.source.name().colorIfAbsent(PRIMARY_COLOR))
                .appendSpace().append(text("has cancelled their teleport request"))
        )
        this.source.sendMessage(
            text("You've cancelled your teleport request to", GRAY)
                .appendSpace().append(this.target.name().colorIfAbsent(PRIMARY_COLOR))
        )
    }

    companion object {
        private val requests = mutableListOf<PlayerTeleportRequest>()

        fun requests(): List<PlayerTeleportRequest> = this.requests.toList()
    }
}