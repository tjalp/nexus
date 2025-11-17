package net.tjalp.nexus.plugin.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import net.kyori.adventure.text.Component.text
import net.tjalp.nexus.plugin.NexusPlugin

object NexusCommand {

    fun create(plugin: NexusPlugin): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal("nexus")
            .requires(Commands.restricted { source -> source.sender.hasPermission("nexus.command.nexus") })
            .then(Commands.literal("reload")
                .executes { context ->
                    context.source.sender.sendMessage("Reloading config")

                    plugin.reloadConfig()

                    return@executes Command.SINGLE_SUCCESS
                }
                .then(Commands.argument("module", StringArgumentType.string())
                    .executes { context ->
                        val moduleName = context.getArgument("module", String::class.java)
                        context.source.sender.sendMessage(text("Reloading module '$moduleName'"))

                        val feature = plugin.features.filter { it.name == moduleName }

                        feature.forEach { it.disable(); it.enable() }

                        return@executes Command.SINGLE_SUCCESS
                    }))
            .build()
    }
}