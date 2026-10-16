package net.tjalp.nexus.api.leaderboard

import net.kyori.adventure.text.ComponentLike

data class LeaderboardScore(
    val display: ComponentLike,
    val value: ComponentLike
)
