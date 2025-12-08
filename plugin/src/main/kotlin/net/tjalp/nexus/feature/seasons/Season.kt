package net.tjalp.nexus.feature.seasons

import net.tjalp.nexus.feature.seasons.ticker.DefaultSeasonTicker
import net.tjalp.nexus.feature.seasons.ticker.WinterSeasonTicker

enum class Season(val ticker: SeasonTicker?) {
    DEFAULT(DefaultSeasonTicker),
    WINTER(WinterSeasonTicker)
}