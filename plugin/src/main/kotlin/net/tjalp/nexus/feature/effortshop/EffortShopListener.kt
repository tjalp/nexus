package net.tjalp.nexus.feature.effortshop

import kotlinx.coroutines.launch
import net.tjalp.nexus.profile.attachment.AttachmentKeys.EFFORT_SHOP
import net.tjalp.nexus.util.profile
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.player.PlayerJoinEvent

class EffortShopListener(private val feature: EffortShopFeature) : Listener {

    @EventHandler
    fun on(event: PlayerJoinEvent) {
        feature.sendFooter(event.player)
    }

    @EventHandler
    fun on(event: BlockBreakEvent) {
        feature.scheduler.launch {
            event.player.profile().update(EFFORT_SHOP) {
                it.addEffort(1)
            }
        }
    }
}