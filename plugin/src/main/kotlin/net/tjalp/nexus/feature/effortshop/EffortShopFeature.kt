package net.tjalp.nexus.feature.effortshop

import kotlinx.coroutines.*
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.Component.newline
import net.kyori.adventure.text.Component.text
import net.tjalp.nexus.Feature
import net.tjalp.nexus.NexusServices
import net.tjalp.nexus.profile.ProfilesService
import net.tjalp.nexus.profile.attachment.AttachmentKeys.EFFORT_SHOP
import net.tjalp.nexus.profile.attachment.AttachmentRegistry
import net.tjalp.nexus.profile.attachment.EffortShopAttachmentProvider
import net.tjalp.nexus.util.profile
import net.tjalp.nexus.util.register
import net.tjalp.nexus.util.unregister
import org.bukkit.Bukkit
import org.bukkit.entity.Player

object EffortShopFeature : Feature {

    override val name: String = "effort_shop"
    override lateinit var scheduler: CoroutineScope; private set

    val profiles: ProfilesService; get() = NexusServices.get<ProfilesService>()

    private lateinit var listener: EffortShopListener

    override fun enable() {
        AttachmentRegistry.register(EffortShopAttachmentProvider.also { runBlocking { it.init() } })
        scheduler = CoroutineScope(NexusServices.get<CoroutineScope>().coroutineContext + SupervisorJob())
        listener = EffortShopListener(this).also { it.register() }

        scheduler.launch {
            profiles.updates.collect { event ->
                event.player?.let { player -> sendFooter(player) }
            }
        }
    }

    override fun disable() {
        listener.unregister()
        scheduler.cancel()
        AttachmentRegistry.unregister(EffortShopAttachmentProvider)
    }

    fun sendFooter(player: Player) {
        val profile = player.profile()
        val att = profile.getAttachment(EFFORT_SHOP)

        Bukkit.broadcastMessage("Sending footer")

        player.sendPlayerListFooter(Component.textOfChildren(
            newline(),
            text("Effort Shop Balance: ${att?.effortPoints ?: "n/a"}")
        ))
    }
}