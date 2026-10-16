package net.tjalp.nexus.api.leaderboard

interface LeaderboardScoreSource {

    suspend fun getScores(limit: Int): List<LeaderboardScore>
}