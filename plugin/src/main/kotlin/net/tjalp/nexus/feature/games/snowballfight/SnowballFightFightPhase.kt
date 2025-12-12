package net.tjalp.nexus.feature.games.snowballfight

import io.papermc.paper.scoreboard.numbers.NumberFormat.styled
import kotlinx.coroutines.launch
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.key.Key.key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.sound.Sound.sound
import net.kyori.adventure.text.Component.empty
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor.RED
import net.kyori.adventure.text.format.NamedTextColor.WHITE
import net.kyori.adventure.text.format.Style.style
import net.kyori.adventure.text.format.TextColor.color
import net.kyori.adventure.text.minimessage.MiniMessage.miniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.title.Title.Times.times
import net.kyori.adventure.title.Title.title
import net.tjalp.nexus.Constants.PRIMARY_COLOR
import net.tjalp.nexus.feature.games.GamePhase
import net.tjalp.nexus.feature.games.JoinResult
import net.tjalp.nexus.feature.games.addition.TimerPhase
import net.tjalp.nexus.util.CountdownTimer
import net.tjalp.nexus.util.register
import net.tjalp.nexus.util.unregister
import org.bukkit.Material
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.entity.ProjectileLaunchEvent
import org.bukkit.scoreboard.Criteria
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.scoreboard.Objective
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

class SnowballFightFightPhase(private val game: SnowballFightGame) : GamePhase, TimerPhase, Listener {

    val scheduler = game.scheduler.fork("phase/fight")

    lateinit var snowballHitsObjective: Objective; private set

    override lateinit var timer: CountdownTimer
    private lateinit var bossBar: BossBar

    override suspend fun load(previous: GamePhase?) {

    }

    override suspend fun start(previous: GamePhase?) {
        register()
        snowballHitsObjective = game.scoreboard.registerNewObjective(
            "snowball_fight_hits",
            Criteria.DUMMY,
            null
        ).apply {
            displaySlot = DisplaySlot.SIDEBAR
            numberFormat(styled(style(PRIMARY_COLOR)))
        }

        timer = CountdownTimer(scheduler, 10.minutes) {
            finish()
        }.also { it.start() }
        bossBar = BossBar.bossBar(empty(), 1f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS)

        var offset = 0f

        scheduler.repeat(interval = 1) {
            val title = miniMessage().deserialize("<bold><gradient:#D4F1F8:#71A6D1:$offset>SNOWBALL FIGHT")

            snowballHitsObjective.displayName(title)

            offset = ((offset + 1f + 0.02f) % 2f) - 1f
        }

        scheduler.repeat(interval = 20) {
            // 1.6s becomes 2.1s -> 2s, 1.4s becomes 1.9s -> 1s
            val secondsRemaining = (timer.remaining + 500.milliseconds).inWholeSeconds
            var progress =
                (secondsRemaining.toFloat() / (timer.initialDuration + 500.milliseconds).inWholeSeconds.toFloat())
                    .coerceIn(0f, 1f)
            val text = text().content("⌚ ${secondsRemaining.seconds}").color(WHITE)
            val warningThreshold = 10

            if (secondsRemaining <= warningThreshold) {
                val isEven = secondsRemaining % 2 == 0L
                val bossBarColor = if (isEven) BossBar.Color.RED else BossBar.Color.WHITE
                val textColor = if (isEven) RED else WHITE

                progress = (secondsRemaining.toFloat() / warningThreshold.toFloat()).coerceIn(0f, 1f)
                bossBar.color(bossBarColor)
                bossBar.addFlag(BossBar.Flag.DARKEN_SCREEN)
                text.color(textColor)

                game.participants.forEach { player ->
                    player.playSound(
                        sound(
                            key("block.note_block.cow_bell"),
                            Sound.Source.MASTER,
                            .5f,
                            if (isEven) 1.1f else .9f
                        ),
                        Sound.Emitter.self()
                    )
                }
            } else {
                bossBar.color(BossBar.Color.WHITE)
                bossBar.removeFlag(BossBar.Flag.DARKEN_SCREEN)
            }

            bossBar.name(text)
            bossBar.progress(progress)
        }
    }

    override suspend fun onJoin(player: Player): JoinResult {
        bossBar.addViewer(player)
        val score = snowballHitsObjective.getScore(player)

        if (!score.isScoreSet) score.score = 0

        return JoinResult.Success
    }

    override fun onLeave(player: Player) {
        bossBar.removeViewer(player)
    }

    fun finish() {
        // in case of a tie, no one wins!
        val winner = game.participants.groupBy { snowballHitsObjective.getScore(it).score }
            .maxByOrNull { it.key }?.value
            ?.let { if (it.size == 1) it.first() else null }
        val title = miniMessage().deserialize("<bold><gradient:#D4F1F8:#71A6D1>FIGHT OVER!")
        val subtitle = text().color(color(0x71A6D1)).append(winner?.name()?.colorIfAbsent(WHITE) ?: text("No one", WHITE))
            .append(text(" wins the snowball fight!")).build()
        val times = times(0.seconds.toJavaDuration(), 4.seconds.toJavaDuration(), 1.seconds.toJavaDuration())
        val finishedTitle = title(title, subtitle, times)

        game.participants.forEach {
            it.showTitle(finishedTitle)
            it.playSound(sound(key("block.note_block.bit"), Sound.Source.MASTER, 1f, 1f), Sound.Emitter.self())
        }

        game.scheduler.launch {
            game.enterNextPhase()
        }
    }

    override fun dispose() {
        if (this::snowballHitsObjective.isInitialized) snowballHitsObjective.unregister()
        unregister()
        scheduler.dispose()
    }

    @EventHandler
    fun on(event: ProjectileLaunchEvent) {
        val projectile = event.entity
        val shooter = projectile.shooter as? Player ?: return

        if (projectile.type != EntityType.SNOWBALL
            || !game.participants.contains(shooter)
        ) return

        shooter.setCooldown(Material.SNOWBALL, 7)
    }

    @EventHandler
    fun on(event: ProjectileHitEvent) {
        val projectile = event.entity
        val shooter = projectile.shooter as? Player ?: return
        val hitEntity = event.hitEntity as? Player ?: return

        if (projectile.type != EntityType.SNOWBALL
            || shooter == hitEntity
            || !game.participants.containsAll(listOf(shooter, hitEntity))
        ) return

        // simulate knockback for players using velocity
        hitEntity.velocity = hitEntity.velocity.add(projectile.velocity.clone().normalize().multiply(1.4)).setY(.45)
        val hurtAnimationYaw = ((projectile.location.yaw - hitEntity.location.yaw + 360) % 360)
        hitEntity.playHurtAnimation(hurtAnimationYaw)
        hitEntity.freezeTicks = 60
        hitEntity.world.playSound(sound(key("entity.player.hurt_freeze"), Sound.Source.PLAYER, 1.5f, 1f), hitEntity)

        val hitByMessage = miniMessage().deserialize(
            HIT_BY_MESSAGES.random(),
            Placeholder.component("shooter", shooter.name().colorIfAbsent(WHITE))
        ).colorIfAbsent(color(0x71A6D1))
        val hitByTitle = title(
            empty(),
            hitByMessage,
            times(0.seconds.toJavaDuration(), 750.milliseconds.toJavaDuration(), 500.milliseconds.toJavaDuration())
        )
//        hitEntity.sendActionBar(hitByMessage)
        hitEntity.showTitle(hitByTitle)

        snowballHitsObjective.getScore(shooter.name).score += 1

        val targetHitMessage = miniMessage().deserialize(
            TARGET_HIT_MESSAGES.random(),
            Placeholder.component("target", hitEntity.name().colorIfAbsent(WHITE))
        ).colorIfAbsent(PRIMARY_COLOR)
        val title = title(
            empty(),
            targetHitMessage,
            times(0.seconds.toJavaDuration(), 350.milliseconds.toJavaDuration(), 400.milliseconds.toJavaDuration())
        )
        shooter.showTitle(title)
        shooter.playSound(sound(key("entity.arrow.hit_player"), Sound.Source.PLAYER, 1f, 1f), Sound.Emitter.self())
    }

    companion object {
        val TARGET_HIT_MESSAGES = listOf(
            "Hit <target>!",
            "Froze <target> solid!",
            "Snowball smack on <target>!",
            "Direct hit on <target>!",
            "Got <target>!",
            "<target> took a snowball!",
            "Bullseye on <target>!",
            "<target> was snowballed!",
            "Nailed <target>!",
            "<target> is frozen!",
            "<target> got hit good!",
            "Tagged <target>!",
            "<target> couldn't dodge!",
            "Splattered <target>!",
            "<target> got iced!",
            "Snowballed <target>!",
        )
        val HIT_BY_MESSAGES = listOf(
            "You were hit by <shooter>!",
            "Ouch! <shooter> got you!",
            "<shooter> froze you solid!",
            "<shooter> hit you with a snowball!",
            "Snowball from <shooter> just hit you!",
            "<shooter> nailed you!",
            "You got snowballed by <shooter>!",
            "<shooter> tagged you!",
            "<shooter> splattered you!",
            "You were iced by <shooter>!",
            "You got hit good by <shooter>!",
            "You couldn't dodge <shooter>'s snowball!",
            "Direct hit from <shooter>!",
            "You were frosted by <shooter>!",
            "You got pelted by <shooter>!",
            "You got blasted by <shooter>!",
            "You were struck by <shooter>!"
        )
    }
}