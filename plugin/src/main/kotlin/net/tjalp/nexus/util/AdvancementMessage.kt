package net.tjalp.nexus.util

import io.papermc.paper.advancement.AdvancementDisplay
import io.papermc.paper.adventure.PaperAdventure
import net.kyori.adventure.text.Component.empty
import net.kyori.adventure.text.ComponentLike
import net.minecraft.advancements.*
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStackTemplate
import net.tjalp.nexus.NexusPlugin
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.ItemType
import java.util.*

/**
 * An advancement message to show. May be in toast form.
 */
@Suppress("UnstableApiUsage")
class AdvancementMessage(
    val title: ComponentLike,
    val description: ComponentLike = empty(),
    val icon: ItemStack = ItemType.FIREWORK_ROCKET.createItemStack(),
    val frame: AdvancementDisplay.Frame = AdvancementDisplay.Frame.TASK
) {

    private val uniqueId = UUID.randomUUID()
    private val id = Identifier.parse("story/${uniqueId}")
    private val requirement = AdvancementRequirements(listOf(listOf("1")))
    private val advancement: Advancement = Advancement(
        Optional.empty(),
        Optional.of(DisplayInfo(
            ItemStackTemplate.fromNonEmptyStack(icon.asNmsItemStack()),
            PaperAdventure.asVanilla(title.asComponent()),
            PaperAdventure.asVanilla(description.asComponent()),
            Optional.empty(),
            frame.asNmsType(),
            true,
            false,
            true
        )),
        AdvancementRewards.EMPTY,
        emptyMap(),
        requirement,
        false
    )

    /**
     * Send the message to a player.
     *
     * @param player The player to send the message to
     */
    private fun send(player: Player) {
        val holder = AdvancementHolder(id, advancement)
        val progress = AdvancementProgress().apply {
            update(requirement)
            grantProgress("1")
        }
        val packet = ClientboundUpdateAdvancementsPacket(
            false,
            setOf(holder),
            emptySet(),
            mapOf(id to progress),
            true
        )

        player.sendPacket(packet)
    }

    /**
     * Remove the enchantment from the player.
     *
     * @param player The player to remove the advancement from
     */
    private fun revoke(player: Player) {
        val packet = ClientboundUpdateAdvancementsPacket(
            false,
            emptySet(),
            setOf(id),
            emptyMap(),
            true
        )

        player.sendPacket(packet)
    }

    /**
     * Send the advancement toast to the specified player.
     *
     * @param player The player to send to
     */
    fun toast(player: Player) {
        send(player)

        NexusPlugin.scheduler.delay(ticks = 8 * 20) {
            revoke(player.uniqueId.asPlayer() ?: return@delay)
        }
    }
}