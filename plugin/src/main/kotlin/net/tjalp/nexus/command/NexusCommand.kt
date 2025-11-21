package net.tjalp.nexus.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import net.kyori.adventure.text.Component.text
import net.tjalp.nexus.Feature
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.command.argument.FeatureArgument

object NexusCommand {

    fun create(plugin: NexusPlugin): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal("nexus")
            .requires(Commands.restricted { source -> source.sender.hasPermission("nexus.command.nexus") })
            .then(Commands.literal("reload")
                .executes { context ->
                    plugin.reloadConfig()
                    plugin.features.forEach {
                        it.disable()
                        it.enable()
                    }
                    context.source.sender.sendMessage("Reloaded config and features")
                    return@executes Command.SINGLE_SUCCESS
                })
            .then(Commands.literal("feature")
                .then(Commands.argument("feature", FeatureArgument)
                    .then(Commands.literal("enable")
                        .executes { context ->
                            val feature = context.getArgument("feature", Feature::class.java).also { it.enable() }
                            context.source.sender.sendMessage(text("Enabled feature '${feature.name}'"))
                            return@executes Command.SINGLE_SUCCESS
                        })
                    .then(Commands.literal("disable")
                        .executes { context ->
                            val feature = context.getArgument("feature", Feature::class.java).also { it.disable() }
                            context.source.sender.sendMessage(text("Disabled feature '${feature.name}'"))
                            return@executes Command.SINGLE_SUCCESS
                        })
                    .then(Commands.literal("reload")
                        .executes { context ->
                            val feature = context.getArgument("feature", Feature::class.java).also {
                                it.disable()
                                it.enable()
                            }
                            context.source.sender.sendMessage(text("Reloaded feature '${feature.name}'"))
                            return@executes Command.SINGLE_SUCCESS
                        })))
            .build()
    }
}