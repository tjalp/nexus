package net.tjalp.nexus.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands.argument
import io.papermc.paper.command.brigadier.Commands.literal
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor.RED
import net.tjalp.nexus.feature.teleportrequests.PlayerTeleportRequest
import net.tjalp.nexus.feature.teleportrequests.TeleportRequestsFeature
import org.bukkit.entity.Player

object TeleportRequestCommand {

    val aliases = setOf("tprequest", "requestteleport", "requesttp", "tpr", "tpa", "tpask", "teleportask")

    fun create(): LiteralCommandNode<CommandSourceStack> {
        return literal("teleportrequest")
            .requires { TeleportRequestsFeature.isEnabled && it.executor is Player && it.sender.hasPermission("nexus.command.teleportrequest") }
            .then(literal("send")
                .then(argument("target", ArgumentTypes.player())
                    .executes(::executeSend)))
            .then(literal("accept")
                .then(argument("sender", ArgumentTypes.player())
                    .executes(::executeAccept)))
            .then(literal("deny")
                .then(argument("sender", ArgumentTypes.player())
                    .executes(::executeDeny)))
            .then(literal("cancel")
                .then(argument("target", ArgumentTypes.player())
                    .executes(::executeCancel)))
            .then(argument("target", ArgumentTypes.player())
                .executes(::executeSend))
            .build()
    }

    private fun executeSend(context: CommandContext<CommandSourceStack>): Int {
        val resolver = context.getArgument("target", PlayerSelectorArgumentResolver::class.java)
        val target = resolver.resolve(context.source).first()

        PlayerTeleportRequest(context.source.executor as Player, target).request()

        return Command.SINGLE_SUCCESS
    }

    private fun executeAccept(context: CommandContext<CommandSourceStack>): Int {
        val resolver = context.getArgument("sender", PlayerSelectorArgumentResolver::class.java)
        val sender = resolver.resolve(context.source).first()
        val request = PlayerTeleportRequest.requests().find { it.source == sender && it.target == context.source.executor as Player }

        if (request != null) {
            request.accept()
        } else {
            context.source.sender.sendMessage(text("You have no active teleport request from", RED)
                .appendSpace().append(sender.name()))
        }

        return Command.SINGLE_SUCCESS
    }

    private fun executeDeny(context: CommandContext<CommandSourceStack>): Int {
        val resolver = context.getArgument("sender", PlayerSelectorArgumentResolver::class.java)
        val sender = resolver.resolve(context.source).first()
        val request = PlayerTeleportRequest.requests().find { it.source == sender && it.target == context.source.executor as Player }

        if (request != null) {
            request.deny()
        } else {
            context.source.sender.sendMessage(text("You have no active teleport request from", RED)
                .appendSpace().append(sender.name()))
        }

        return Command.SINGLE_SUCCESS
    }

    private fun executeCancel(context: CommandContext<CommandSourceStack>): Int {
        val resolver = context.getArgument("target", PlayerSelectorArgumentResolver::class.java)
        val target = resolver.resolve(context.source).first()
        val request = PlayerTeleportRequest.requests().find { it.source == context.source.executor as Player && it.target == target }

        if (request != null) {
            request.cancel()
        } else {
            context.source.sender.sendMessage(text("You have no active teleport request to", RED)
                .appendSpace().append(target.name()))
        }

        return Command.SINGLE_SUCCESS
    }
}