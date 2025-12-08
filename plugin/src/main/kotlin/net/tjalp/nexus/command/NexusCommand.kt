package net.tjalp.nexus.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import kotlinx.coroutines.isActive
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.NamedTextColor.*
import net.tjalp.nexus.Constants.PRIMARY_COLOR
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.command.argument.FeatureArgument
import net.tjalp.nexus.Feature
import net.tjalp.nexus.scheduler.Scheduler

object NexusCommand {

    fun create(plugin: NexusPlugin): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal("nexus")
            .requires(Commands.restricted { source -> source.sender.hasPermission("nexus.command.nexus") })
            .then(Commands.literal("reload")
                .executes { context ->
                    plugin.reloadConfig()
                    plugin.features.forEach {
                        if (it.isEnabled) it.disable()
                        it.enable()
                    }
                    context.source.sender.server.onlinePlayers.forEach { it.updateCommands() }
                    context.source.sender.sendMessage("Reloaded config and features")
                    return@executes Command.SINGLE_SUCCESS
                })
            .then(Commands.literal("schedulers")
                .executes(::listSchedulers))
            .then(Commands.literal("feature")
                .then(Commands.argument("feature", FeatureArgument)
                    .then(Commands.literal("enable")
                        .executes { context ->
                            val feature = context.getArgument("feature", Feature::class.java).also { it.enable() }
                            context.source.sender.server.onlinePlayers.forEach { it.updateCommands() }
                            context.source.sender.sendMessage(text("Enabled feature '${feature.name}'"))
                            return@executes Command.SINGLE_SUCCESS
                        })
                    .then(Commands.literal("disable")
                        .executes { context ->
                            val feature = context.getArgument("feature", Feature::class.java).also { if (it.isEnabled) it.disable() }
                            context.source.sender.server.onlinePlayers.forEach { it.updateCommands() }
                            context.source.sender.sendMessage(text("Disabled feature '${feature.name}'"))
                            return@executes Command.SINGLE_SUCCESS
                        })
                    .then(Commands.literal("reload")
                        .executes { context ->
                            val feature = context.getArgument("feature", Feature::class.java).also {
                                if (it.isEnabled) it.disable()
                                it.enable()
                            }
                            context.source.sender.server.onlinePlayers.forEach { it.updateCommands() }
                            context.source.sender.sendMessage(text("Reloaded feature '${feature.name}'"))
                            return@executes Command.SINGLE_SUCCESS
                        })))
            .build()
    }

    private fun listSchedulers(context: CommandContext<CommandSourceStack>): Int {
        fun constructComponent(
            builder: TextComponent.Builder,
            scheduler: Scheduler,
            indent: Int,
            isLast: Boolean,
            activeLines: Set<Int> = emptySet()
        ) {
            val prefix = if (indent == 0) "" else {
                val parts = mutableListOf<String>()
                var currentIndent = 0
                while (currentIndent <= indent - 3) {
                    if (currentIndent / 3 in activeLines) parts.add( "│ ")
                    currentIndent += 3
                }
                val connector = if (isLast) "└ " else "├ "
                parts.add(connector)
                parts.joinToString("")
            }

            val color = if (scheduler.isActive) PRIMARY_COLOR else RED
            val line = text().append(text(prefix, DARK_GRAY), text(scheduler.id, color))

            if (!scheduler.isActive) {
                line.append(text(" (inactive)", DARK_RED))
            }

            if (indent != 0) builder.appendNewline()
            builder.append(line)

            val children = scheduler.children
            val nextActiveLines = activeLines.toMutableSet()
            if (!isLast) nextActiveLines.add(indent / 3)

            children.forEachIndexed { index, child ->
                val isLastChild = index == children.size - 1
                val nextIndent = indent + 3
                constructComponent(builder, child, nextIndent, isLastChild, nextActiveLines)
            }
        }

        val builder = text()
        constructComponent(builder, NexusPlugin.scheduler, 0, true)
        context.source.sender.sendMessage(builder)

        return Command.SINGLE_SUCCESS
    }


}