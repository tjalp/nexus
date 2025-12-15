package net.tjalp.nexus.feature.games.snowballfight

import kotlinx.coroutines.launch
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.bossbar.BossBar.bossBar
import net.kyori.adventure.key.Key.key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.sound.Sound.sound
import net.kyori.adventure.text.Component.empty
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor.*
import net.kyori.adventure.title.Title.Times.times
import net.kyori.adventure.title.Title.title
import net.tjalp.nexus.Constants.PRIMARY_COLOR
import net.tjalp.nexus.feature.games.Game
import net.tjalp.nexus.feature.games.GamePhase
import net.tjalp.nexus.feature.games.phase.TimerPhase
import net.tjalp.nexus.feature.games.phase.WaitingPhase
import net.tjalp.nexus.feature.games.prefix
import net.tjalp.nexus.util.SecondCountdownTimer
import org.bukkit.entity.Entity
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

class SnowballFightWaitingPhase(private val game: Game) : WaitingPhase(game), TimerPhase {

    private val scheduler = game.scheduler.fork("phase/waiting")

    override var timer: SecondCountdownTimer = SecondCountdownTimer(scheduler, 30, onTick = { remaining ->
        updateBossBar(remaining)

        if (remaining <= 5L || remaining == 10L || remaining == 20L) {
            val titleText = text(remaining.toString(), PRIMARY_COLOR)
            val title = title(titleText, empty(), times(0.seconds.toJavaDuration(), 1.5.seconds.toJavaDuration(), 1.seconds.toJavaDuration()))

            game.showTitle(title)
            game.playSound(
                sound(key("block.note_block.bell"), Sound.Source.MASTER, .5f, 1f),
                Sound.Emitter.self()
            )
        }
    }) {
        game.scheduler.launch {
            game.enterNextPhase()
        }
    }

    private val bossBar =
        bossBar(empty(), 1f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS)

    override suspend fun start(previous: GamePhase?) {
        updateBossBar()

        super.start(previous)
    }

    override suspend fun onJoin(entity: Entity) {
        super.onJoin(entity)

        if (game.participants.size >= game.settings.minPlayers && !timer.isRunning) {
            game.sendMessage(game.prefix.append(text("Minimum player count reached, starting the wait timer!", PRIMARY_COLOR)))
            timer.start()
        }

        bossBar.addViewer(entity)
        updateBossBar()
    }

    override fun onLeave(entity: Entity) {
        bossBar.removeViewer(entity)

        if (game.participants.size < game.settings.minPlayers && timer.isRunning) {
            game.sendMessage(game.prefix.append(text("Not enough players, resetting the wait timer...", RED)))
            timer.pause()
            timer.reset()
        }

        updateBossBar()

        super.onLeave(entity)
    }

    private fun updateBossBar(remaining: Long = timer.remaining) {
        val progress = remaining.toDouble() / timer.initialTime.toDouble()
        val nameColor = if (timer.isRunning) WHITE else YELLOW
        val bossBarColor = if (timer.isRunning) BossBar.Color.WHITE else BossBar.Color.YELLOW
        val name = text().color(nameColor).content("⌚ ${remaining.seconds}")

        if (!timer.isRunning) {
            name.append(text(" - Waiting for players... (${game.participants.size}/${game.settings.minPlayers})", nameColor))
        }

        bossBar.progress(progress.toFloat().coerceIn(0f, 1f))
        bossBar.name(name)
        bossBar.color(bossBarColor)
    }

    override fun dispose() {
        scheduler.dispose()
        super.dispose()
    }
}