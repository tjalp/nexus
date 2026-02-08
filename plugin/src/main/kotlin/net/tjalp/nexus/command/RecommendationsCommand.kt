package net.tjalp.nexus.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands.literal
import net.kyori.adventure.identity.Identity
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.feature.notices.RecommendationsDialog
import java.util.*
import kotlin.jvm.optionals.getOrNull

object RecommendationsCommand {

    fun create(): LiteralCommandNode<CommandSourceStack> {
        return literal("recommendations")
            .requires { NexusPlugin.notices != null && it.sender.hasPermission("nexus.command.recommendations") && it.executor != null }
            .executes { context ->
                val executor = context.source.executor!!
                val locale = executor.get(Identity.LOCALE).getOrNull() ?: Locale.US

                executor.showDialog(RecommendationsDialog.create(locale))

                return@executes Command.SINGLE_SUCCESS
            }
            .build()
    }
}