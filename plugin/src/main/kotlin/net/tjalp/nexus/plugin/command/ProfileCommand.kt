package net.tjalp.nexus.plugin.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.tjalp.nexus.common.profile.ProfileId
import net.tjalp.nexus.plugin.NexusPlugin
import org.bukkit.command.CommandSender
import java.util.*


object ProfileCommand {

    fun create(nexus: NexusPlugin): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal("profile")
            .requires(Commands.restricted { source -> source.sender.hasPermission("nexus.command.profile") })
            .then(Commands.literal("get")
                .then(Commands.argument("uniqueId", ArgumentTypes.uuid())
                    .executes { context ->
                        val uniqueId = context.getArgument("uniqueId", UUID::class.java)

                        fetchProfile(nexus, uniqueId, context.source.sender)

                        return@executes Command.SINGLE_SUCCESS
                    })
                .then(Commands.argument("name", ArgumentTypes.player())
                    .executes { context ->
                        val argument = context.getArgument( "name", PlayerSelectorArgumentResolver::class.java)
                        val uniqueId = argument.resolve(context.source).first().uniqueId

                        fetchProfile(nexus, uniqueId, context.source.sender)

                        return@executes Command.SINGLE_SUCCESS
                    }))
            .build()
    }

    private fun fetchProfile(nexus: NexusPlugin, uniqueId: UUID, sender: CommandSender) {
        CoroutineScope(Dispatchers.Default).launch {
            val profile = nexus.profiles.get(ProfileId(uniqueId))

            if (profile == null) {
                sender.sendRichMessage("<red>No profile found for UUID $uniqueId</red>")
                return@launch
            }

            sender.sendRichMessage("<green>Profile for UUID $uniqueId:</green>\n$profile")
        }
    }
}