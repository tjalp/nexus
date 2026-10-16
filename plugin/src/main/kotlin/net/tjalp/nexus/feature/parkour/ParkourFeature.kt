package net.tjalp.nexus.feature.parkour

import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.Component.translatable
import net.kyori.adventure.text.minimessage.translation.Argument
import net.tjalp.nexus.Feature
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.api.leaderboard.Leaderboard
import net.tjalp.nexus.feature.FeatureKeys.PARKOUR
import net.tjalp.nexus.util.register
import net.tjalp.nexus.util.unregister

/**
 * The Parkour feature manages a single global node/segment graph and runtime
 * player sessions (split timing, action bars, and segment result persistence).
 */
class ParkourFeature : Feature(PARKOUR) {

    lateinit var definitions: ParkourDefinitionsRepository; private set
    lateinit var runtime: ParkourRuntimeService; private set
    private lateinit var listener: ParkourListener

    val leaderboardsSet = mutableSetOf<Leaderboard>()

    override fun onEnable() {
        definitions = ParkourDefinitionsRepository(NexusPlugin.dataPath)
        runtime = ParkourRuntimeService(this)
//        leaderboards = ParkourLeaderboardService(this)
        for (leaderboard in definitions.definition.leaderboards) {
            val title = translatable(
                "parkour.leaderboard.title",
                Argument.component(
                    "segment_title",
                    definitions.definition.segmentByKey(leaderboard.segmentKey)?.displayName
                        ?: text(leaderboard.segmentKey)
                    )
            )
            leaderboardsSet += Leaderboard(
                location = leaderboard.location ?: continue,
                description = translatable("parkour.leaderboard.description"),
                title = title,
                source = ParkourLeaderboardSource(leaderboard.segmentKey),
                scheduler = scheduler
            )
        }

        runtime.rebuildIndex()

        listener = ParkourListener(runtime)
        listener.register()

        // Tick live action bar 20 times per second for active sessions
        scheduler.repeat(0, 1) {
            runtime.tickActionBars()
        }
    }

    override fun onDisposed() {
//        leaderboards.dispose()
        leaderboardsSet.forEach(Leaderboard::dispose)
        listener.unregister()
    }
}
