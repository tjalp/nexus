package net.tjalp.nexus.feature.effortshop

import kotlinx.coroutines.*
import net.kyori.adventure.text.Component.*
import net.kyori.adventure.text.format.NamedTextColor.WHITE
import net.tjalp.nexus.Constants.PRIMARY_COLOR
import net.tjalp.nexus.Feature
import net.tjalp.nexus.NexusServices
import net.tjalp.nexus.profile.ProfilesService
import net.tjalp.nexus.profile.attachment.AttachmentKeys.EFFORT_SHOP
import net.tjalp.nexus.profile.attachment.AttachmentRegistry
import net.tjalp.nexus.profile.attachment.EffortShopAttachmentProvider
import net.tjalp.nexus.util.profile
import net.tjalp.nexus.util.register
import net.tjalp.nexus.util.unregister
import org.bukkit.entity.Player

object EffortShopFeature : Feature {

    override val name: String = "effort_shop"

    private var _isEnabled: Boolean = false
    override val isEnabled: Boolean
        get() = _isEnabled

    override lateinit var scheduler: CoroutineScope; private set

    val profiles: ProfilesService; get() = NexusServices.get<ProfilesService>()

    private lateinit var listener: EffortShopListener

    override fun enable() {
        this._isEnabled = true

        AttachmentRegistry.register(EffortShopAttachmentProvider.also { runBlocking { it.init() } })
        scheduler = CoroutineScope(NexusServices.get<CoroutineScope>().coroutineContext + SupervisorJob())
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
            profiles.updates.collect { event ->
                val oldBalance = event.old?.getAttachment(EFFORT_SHOP)?.effortBalance
                val newBalance = event.new.getAttachment(EFFORT_SHOP)?.effortBalance
                if (newBalance != oldBalance) event.player?.let { player -> sendFooter(player) }
            }
        }
    }

    override fun disable() {
        listener.unregister()
        scheduler.cancel()
        AttachmentRegistry.unregister(EffortShopAttachmentProvider)

        this._isEnabled = false
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