package net.tjalp.nexus.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands.literal
import kotlinx.coroutines.launch
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.config.RulesConfig
import net.tjalp.nexus.feature.notices.RulesDialog
import net.tjalp.nexus.profile.attachment.NoticesAttachment
import net.tjalp.nexus.profile.update
import net.tjalp.nexus.util.profile
import org.bukkit.entity.Player

object RulesCommand {

    private val config: RulesConfig
        get() = NexusPlugin.configuration.features.notices.rules

    fun create(): LiteralCommandNode<CommandSourceStack> {
        return literal("rules")
            .requires { NexusPlugin.notices != null && it.sender.hasPermission("nexus.command.rules") && it.executor is Player }
            .executes { context ->
                val executor = context.source.executor!! as Player
                val profile = executor.profile()
                val locale = executor.locale()
                val rulesVersion = config.rulesVersion
                val notices = NexusPlugin.notices ?: error("Notices feature not enabled")

                executor.showDialog(RulesDialog.create(locale) { accepted ->
                    if (!accepted) return@create

                    notices.scheduler.launch {
                        profile.update<NoticesAttachment> {
                            it.setAcceptedRules(rulesVersion)
                        }
                    }
                })

                return@executes Command.SINGLE_SUCCESS
            }
            .build()
    }
}