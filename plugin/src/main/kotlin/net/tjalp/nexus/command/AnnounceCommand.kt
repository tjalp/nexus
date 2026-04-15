package net.tjalp.nexus.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.command.brigadier.Commands.*
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.feature.notices.AnnouncementType
import net.tjalp.nexus.util.miniMessage
import java.util.*

object AnnounceCommand {

    val aliases = setOf("broadcast")

    private val stacks = WeakHashMap<CommandSourceStack, AnnounceStack>()

    @Suppress("UnstableApiUsage")
    fun create(commands: Commands): Collection<LiteralCommandNode<CommandSourceStack>> {
        val requirement: (CommandSourceStack) -> Boolean = { NexusPlugin.notices != null && it.sender.hasPermission("nexus.command.announce") }
        val dispatcher = commands.dispatcher
        val root = literal("announce").requires(restricted(requirement)).build()
        val node = literal("announce")
            .requires(restricted(requirement))
            .then(literal("to")
                .then(argument("targets", ArgumentTypes.players())
                    .redirect(root) { context ->
                        val selector = context.getArgument("targets", PlayerSelectorArgumentResolver::class.java)
                        val stack = stacks[context.source]?.copy(targets = selector) ?: AnnounceStack(targets = selector)

                        stacks[context.source] = stack

                        return@redirect context.source
                    }))
            .then(literal("sound")
                .then(argument("sound", ArgumentTypes.key())
                    .redirect(root) { context ->
                        val key = context.getArgument("sound", Key::class.java)
                        val sound = Sound.sound().type(key).build()
                        val stack = stacks[context.source]?.copy(sound = sound) ?: AnnounceStack(sound = sound)

                        stacks[context.source] = stack

                        return@redirect context.source
                    }))

        for (type in AnnouncementType.entries) {
            node.then(literal(type.command)
                .then(argument("message", StringArgumentType.greedyString())
                    .executes { context ->
                        val message = StringArgumentType.getString(context, "message")
                        val component = miniMessage.deserialize(message)
                        val additionalStack = stacks[context.source]
                        val targets = additionalStack?.targets?.resolve(context.source)
                            ?.let { Audience.audience(it) }
                            ?: NexusPlugin.server
                        val sound = additionalStack?.sound

                        NexusPlugin.notices?.announce(type = type, message = component, sound = sound, audience = targets)

                        return@executes Command.SINGLE_SUCCESS
                    }))
        }

        /*
         this should be done differently. This *works*, but causes the namespaced command to not work correctly
         https://discord.com/channels/289587909051416579/555462289851940864/1474902804400181382
         */

        dispatcher.root.addChild(root)
        dispatcher.root.addChild(node.build())

        return setOf(node.build())
    }
}

data class AnnounceStack(
    val targets: PlayerSelectorArgumentResolver? = null,
    val sound: Sound? = null
)