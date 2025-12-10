package net.tjalp.nexus.feature.games.snowballfight

import net.tjalp.nexus.feature.games.Game
import net.tjalp.nexus.feature.games.GamePhase
import net.tjalp.nexus.feature.games.GameType

class SnowballFightGame : Game(type = GameType.SNOWBALL_FIGHT) {

    override val nextPhase: GamePhase
        get() = if (currentPhase == null || currentPhase is SnowballFightFightPhase) {
            SnowballFightWaitingPhase(this)
        } else {
            SnowballFightFightPhase(this)
        }
}