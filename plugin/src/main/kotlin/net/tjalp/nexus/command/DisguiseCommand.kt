package net.tjalp.nexus.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands.argument
import io.papermc.paper.command.brigadier.Commands.literal
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import io.papermc.paper.command.brigadier.argument.resolvers.selector.EntitySelectorArgumentResolver
import io.papermc.paper.registry.RegistryKey
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.feature.disguises.DisguiseFeature
import org.bukkit.entity.EntityType

object DisguiseCommand {

    fun create(nexus: NexusPlugin): LiteralCommandNode<CommandSourceStack> {
        return literal("disguise")
            .requires { DisguiseFeature.provider != null && it.sender.hasPermission("nexus.command.disguise") }
            .then(literal("set")
                .then(argument("targets", ArgumentTypes.entities())
                    .then(argument("entity", ArgumentTypes.resource(RegistryKey.ENTITY_TYPE))
                        .executes { context ->
                            val targetResolver = context.getArgument("targets", EntitySelectorArgumentResolver::class.java)
                            val targets = targetResolver.resolve(context.source)
                            val entityType = context.getArgument("entity", EntityType::class.java)

                            for (target in targets) {
                                DisguiseFeature.provider?.disguise(target, entityType)
                            }

                            context.source.sender.sendMessage("Disguised ${targets.size} entity(ies) as ${entityType.name}")

                            return@executes Command.SINGLE_SUCCESS
                        })))
            .then(literal("reset")
                .then(argument("targets", ArgumentTypes.entities())
                    .executes { context ->
                        val targetResolver = context.getArgument("targets", EntitySelectorArgumentResolver::class.java)
                        val targets = targetResolver.resolve(context.source)

                        for (target in targets) {
                            DisguiseFeature.provider?.undisguise(target)
                        }

                        context.source.sender.sendMessage("Removed disguises from ${targets.size} entity(ies)")

                        return@executes Command.SINGLE_SUCCESS
                    }))
            .then(literal("query")
                .then(argument("target", ArgumentTypes.entity())
                    .executes { context ->
                        val targetResolver = context.getArgument("target", EntitySelectorArgumentResolver::class.java)
                        val target = targetResolver.resolve(context.source).firstOrNull() ?: run {
                            context.source.sender.sendMessage("No entity found")
                            return@executes Command.SINGLE_SUCCESS
                        }
                        val disguise = DisguiseFeature.provider?.getDisguise(target)

                        if (disguise != null) {
                            context.source.sender.sendMessage("Entity is disguised as ${disguise.name}")
                        } else {
                            context.source.sender.sendMessage("Entity is not disguised")
                        }

                        return@executes Command.SINGLE_SUCCESS
                    }))
            .build()
    }
}