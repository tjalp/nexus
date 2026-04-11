package net.tjalp.nexus.feature.parkour

import net.tjalp.nexus.Feature
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.feature.FeatureKeys.PARKOUR
import net.tjalp.nexus.profile.attachment.AttachmentRegistry
import net.tjalp.nexus.profile.attachment.ParkourAttachmentProvider
import net.tjalp.nexus.util.register
import net.tjalp.nexus.util.unregister

/**
 * The Parkour feature manages parkour definitions (graph of nodes/segments stored
 * via Configurate) and runtime player sessions (start/stop tracking, action-bar
 * splits, and pinned-route auto-finish with DB result persistence).
 */
class ParkourFeature : Feature(PARKOUR) {

    lateinit var definitions: ParkourDefinitionsRepository; private set
    lateinit var runtime: ParkourRuntimeService; private set
    private lateinit var listener: ParkourListener

    override fun onEnable() {
        definitions = ParkourDefinitionsRepository(NexusPlugin.dataPath)
        runtime = ParkourRuntimeService(this)
        runtime.rebuildIndex()

        listener = ParkourListener(runtime)
        listener.register()

        AttachmentRegistry.register(ParkourAttachmentProvider)

        // Tick live action bar 20 times per second for active sessions
        scheduler.repeat(0, 1) {
            runtime.tickActionBars()
        }
    }

    override fun onDisposed() {
        listener.unregister()
        AttachmentRegistry.unregister(ParkourAttachmentProvider)
    }
}
