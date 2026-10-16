package net.tjalp.nexus.api.leaderboard

import net.kyori.adventure.text.Component.*
import net.kyori.adventure.text.format.NamedTextColor.*
import net.kyori.adventure.text.format.TextDecoration.BOLD
import net.kyori.adventure.text.format.TextDecoration.ITALIC
import net.tjalp.nexus.Constants.MONOCHROME_COLOR
import net.tjalp.nexus.Constants.PRIMARY_COLOR
import net.tjalp.nexus.api.hologram.Hologram
import net.tjalp.nexus.api.hologram.line.ComponentLine
import net.tjalp.nexus.api.hologram.line.SpacerLine
import org.spongepowered.configurate.reactive.Disposable

class LeaderboardHologram(
    val leaderboard: Leaderboard
) : Disposable {

    private val hologram: Hologram

    private val scoreLines = mutableListOf<ComponentLine>()

    init {
        hologram = Hologram(leaderboard.location).apply {
            addLine(ComponentLine(text().color(PRIMARY_COLOR).decorate(BOLD).append(leaderboard.title)))
            leaderboard.description?.let {
                addLine(ComponentLine(text().color(GRAY).decorate(ITALIC).append(it)))
            }
            addLine(SpacerLine())

            repeat(leaderboard.size) {
                val line = ComponentLine(NO_DATA)

                scoreLines += line
                addLine(line)
            }
        }

        render()
    }

    fun render() {
        repeat(leaderboard.size) { idx ->
            val score = leaderboard.getScore(idx)

            scoreLines[idx].component = score?.let { score ->
                val num = text("${idx + 1}. ").color(YELLOW)
                textOfChildren(
                    num,
                    score.display.asComponent().colorIfAbsent(PRIMARY_COLOR),
                    text(" - ", DARK_GRAY),
                    score.value.asComponent().colorIfAbsent(MONOCHROME_COLOR)
                )
            } ?: NO_DATA
        }
    }

    fun spawn() {
        hologram.spawn()
    }

    fun despawn() {
        hologram.despawn()
    }

    override fun dispose() {
        hologram.dispose()
    }

    companion object {
        private val NO_DATA = translatable("leaderboard.no_data", DARK_GRAY)
    }
}