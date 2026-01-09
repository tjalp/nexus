package net.tjalp.nexus.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands.argument
import io.papermc.paper.command.brigadier.Commands.literal
import io.papermc.paper.command.brigadier.MessageComponentSerializer
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.Component.translatable
import net.kyori.adventure.text.minimessage.translation.Argument
import net.tjalp.nexus.feature.teleportrequests.PlayerTeleportRequest
import net.tjalp.nexus.feature.teleportrequests.TeleportRequestsFeature
import org.bukkit.entity.Player

object TeleportRequestCommand {

    val aliases = setOf("tprequest", "requestteleport", "requesttp", "tpr", "tpa", "tpask", "teleportask")

    private val ERROR_SEND_TO_SELF = SimpleCommandExceptionType(
        MessageComponentSerializer.message()
            .serialize(translatable("command.teleportrequest.send.self"))
    )

    private val ERROR_DUPLICATE = DynamicCommandExceptionType { target: Any? ->
        val name = (target as? Player)?.name() ?: text(target.toString())
        MessageComponentSerializer.message()
            .serialize(translatable(
                "command.teleportrequest.send.duplicate",
                Argument.component("target", name)
            ))
    }

    private val ERROR_NO_REQUEST_RECEIVED = DynamicCommandExceptionType { sender: Any? ->
        val name = (sender as? Player)?.name() ?: text(sender.toString())
        MessageComponentSerializer.message()
            .serialize(translatable(
                "command.teleportrequest.accept.none",
                Argument.component("sender", name)
            ))
    }

    private val ERROR_NO_REQUEST_SENT = DynamicCommandExceptionType { target: Any? ->
        val name = (target as? Player)?.name() ?: text(target.toString())
        MessageComponentSerializer.message()
            .serialize(translatable(
                "command.teleportrequest.cancel.none",
                Argument.component("target", name)
            ))
    }

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
        val executor = context.source.executor as Player

//        if (target == executor) throw ERROR_SEND_TO_SELF.create()
        if (PlayerTeleportRequest.requests().any { it.source == executor && it.target == target }) {
            throw ERROR_DUPLICATE.create(target)
        }

        PlayerTeleportRequest(executor, target).request()

        return Command.SINGLE_SUCCESS
    }

    private fun executeAccept(context: CommandContext<CommandSourceStack>): Int {
        val resolver = context.getArgument("sender", PlayerSelectorArgumentResolver::class.java)
        val sender = resolver.resolve(context.source).first()
        val request = PlayerTeleportRequest.requests().find { it.source == sender && it.target == context.source.executor as Player }
            ?: throw ERROR_NO_REQUEST_RECEIVED.create(sender)

        request.accept()

        return Command.SINGLE_SUCCESS
    }

    private fun executeDeny(context: CommandContext<CommandSourceStack>): Int {
        val resolver = context.getArgument("sender", PlayerSelectorArgumentResolver::class.java)
        val sender = resolver.resolve(context.source).first()
        val request = PlayerTeleportRequest.requests().find { it.source == sender && it.target == context.source.executor as Player }
            ?: throw ERROR_NO_REQUEST_RECEIVED.create(sender)

        request.deny()

        return Command.SINGLE_SUCCESS
    }

    private fun executeCancel(context: CommandContext<CommandSourceStack>): Int {
        val resolver = context.getArgument("target", PlayerSelectorArgumentResolver::class.java)
        val target = resolver.resolve(context.source).first()
        val request = PlayerTeleportRequest.requests().find { it.source == context.source.executor as Player && it.target == target }
            ?: throw ERROR_NO_REQUEST_SENT.create(target)

        request.cancel()

        return Command.SINGLE_SUCCESS
    }
}