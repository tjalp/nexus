package net.tjalp.nexus.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.command.brigadier.Commands.argument
import io.papermc.paper.command.brigadier.Commands.literal
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component.translatable
import net.kyori.adventure.text.minimessage.translation.Argument
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.command.argument.LocaleArgument
import net.tjalp.nexus.lang.langPointer
import net.tjalp.nexus.profile.attachment.GeneralTable
import net.tjalp.nexus.util.profile
import org.bukkit.entity.Player
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.update
import java.util.*

object LanguageCommand {

    val aliases = setOf("lang")

    fun create(): LiteralCommandNode<CommandSourceStack> {
        return literal("language")
            .requires(Commands.restricted {
                source -> source.sender.hasPermission("nexus.command.language") && source.executor is Player
            })
            .then(argument("language", LocaleArgument)
                .executes { context ->
                    val locale = context.getArgument("language", Locale::class.java)
                    val sender = context.source.sender
                    val player = context.source.executor as Player
                    val profile = player.profile()

                    NexusPlugin.scheduler.launch {
                        profile.update {
                            GeneralTable.update({ GeneralTable.profileId eq profile.id }) {
                                it[GeneralTable.preferredLocale] = locale.toLanguageTag()
                            }
                        }

                        val target = (sender as? Player)?.langPointer() ?: sender

                        sender.sendMessage(
                            translatable("command.language.set",
                                Argument.string("lang", locale.getDisplayName(locale)),
                                Argument.target(target)
                            )
                        )
                    }

                    return@executes Command.SINGLE_SUCCESS
                })
            .build()
    }
}