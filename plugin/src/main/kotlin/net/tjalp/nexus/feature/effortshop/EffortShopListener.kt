package net.tjalp.nexus.feature.effortshop

import kotlinx.coroutines.launch
import net.tjalp.nexus.profile.attachment.EffortShopTable
import net.tjalp.nexus.util.profile
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.jdbc.update

class EffortShopListener(private val feature: EffortShopFeature) : Listener {

    @EventHandler
    fun on(event: PlayerJoinEvent) {
        feature.sendFooter(event.player)
    }

    @EventHandler
    fun on(event: BlockBreakEvent) {
        feature.scheduler.launch {
            event.player.profile().update {
                EffortShopTable.update({ EffortShopTable.profileId eq event.player.uniqueId }) {
                    it[EffortShopTable.effortBalance] = EffortShopTable.effortBalance + 1
                }
            }
        }
    }
}