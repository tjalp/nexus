package net.tjalp.nexus.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands.literal
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.feature.seasons.Season

object SeasonCommand {

    val seasons
        get() = NexusPlugin.seasons ?: error("Seasons feature is not enabled")

    fun create(): LiteralCommandNode<CommandSourceStack> {
        val base = literal("season")
            .requires { NexusPlugin.seasons != null && it.sender.hasPermission("nexus.command.season") }

        for (season in Season.entries) {
            base.then(literal(season.name.lowercase())
                .executes { context ->
                    seasons.currentSeason = season
                    context.source.sender.sendMessage("Season set to ${season.name.lowercase()}")
                    return@executes Command.SINGLE_SUCCESS
                })
        }

        return base.build()
    }
}