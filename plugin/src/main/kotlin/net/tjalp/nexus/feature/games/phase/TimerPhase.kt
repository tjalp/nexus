package net.tjalp.nexus.feature.games.phase

import net.tjalp.nexus.util.SecondCountdownTimer

/**
 * Represents a phase with a timer
 */
interface TimerPhase {

    /**
     * The timer which counts down once started
     */
    val timer: SecondCountdownTimer
}