package net.tjalp.nexus.feature.effortshop

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component.*
import net.kyori.adventure.text.format.NamedTextColor.WHITE
import net.tjalp.nexus.Constants.PRIMARY_COLOR
import net.tjalp.nexus.Feature
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.profile.attachment.AttachmentKeys.EFFORT_SHOP
import net.tjalp.nexus.profile.attachment.AttachmentRegistry
import net.tjalp.nexus.profile.attachment.EffortShopAttachmentProvider
import net.tjalp.nexus.util.profile
import net.tjalp.nexus.util.register
import net.tjalp.nexus.util.unregister
import org.bukkit.entity.Player

object EffortShopFeature : Feature("effort_shop") {

    private lateinit var listener: EffortShopListener

    override fun enable() {
        super.enable()

        AttachmentRegistry.register(EffortShopAttachmentProvider.also { runBlocking { it.init() } })
        listener = EffortShopListener(this).also { it.register() }

//        val uncraftableBlocks = Registry.ITEM.filter {
//            val item = it.createItemStack()
//            it.hasBlockType()
//                    && it.blockType.isSolid
//                    && !Tag.LOGS.isTagged(item.type)
//                    && !Tag.LEAVES.isTagged(item.type)
//                    && !Tag.SAPLINGS.isTagged(item.type)
//                    && !Tag.WOOL.isTagged(item.type)
//                    && !Tag.CROPS.isTagged(item.type)
//                    && !Tag.COPPER.isTagged(item.type)
//                    && Bukkit.getRecipesFor(item).isEmpty()
//        }
//        Bukkit.getOnlinePlayers().forEach { player ->
//            uncraftableBlocks.forEach { item ->
//                // drop the items where they're standing
//                player.world.dropItemNaturally(player.location, item.createItemStack())
//            }
//        }

        scheduler.launch {
            NexusPlugin.profiles.updates.collect { event ->
                val oldBalance = event.old?.getAttachment(EFFORT_SHOP)?.effortBalance
                val newBalance = event.new.getAttachment(EFFORT_SHOP)?.effortBalance
                if (newBalance != oldBalance) event.player?.let { player -> sendFooter(player) }
            }
        }
    }

    override fun disable() {
        listener.unregister()
        AttachmentRegistry.unregister(EffortShopAttachmentProvider)

        super.disable()
    }

    fun sendFooter(player: Player) {
        val profile = player.profile()
        val att = profile.getAttachment(EFFORT_SHOP)

//        player.sendActionBar(
//            text("Updated effort shop balance to ", PRIMARY_COLOR).append(
//                text(
//                    att?.effortBalance?.toString() ?: "n/a", WHITE
//                )
//            )
//        )

        player.sendPlayerListHeaderAndFooter(
            textOfChildren(
                newline(),
                text("Welcome to Nexus", PRIMARY_COLOR),
                newline()
            ),
            textOfChildren(
                newline(),
                text("Effort shop balance: ", PRIMARY_COLOR).append(text(att?.effortBalance?.toString() ?: "n/a", WHITE)),
                newline()
            )
        )
    }
}