@file:OptIn(ExperimentalTime::class)

package net.tjalp.nexus.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType.greedyString
import com.mojang.brigadier.arguments.StringArgumentType.string
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands.*
import io.papermc.paper.command.brigadier.MessageComponentSerializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.kyori.adventure.identity.Identity
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.Component.translatable
import net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY
import net.kyori.adventure.text.format.NamedTextColor.RED
import net.kyori.adventure.text.minimessage.translation.Argument
import net.tjalp.nexus.Constants.COMPLEMENTARY_COLOR
import net.tjalp.nexus.Constants.MONOCHROME_COLOR
import net.tjalp.nexus.Constants.PRIMARY_COLOR
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.feature.punishments.PunishmentSeverity
import net.tjalp.nexus.feature.punishments.PunishmentType
import net.tjalp.nexus.feature.punishments.PunishmentsFeature
import net.tjalp.nexus.profile.attachment.AttachmentKeys
import kotlin.jvm.optionals.getOrNull
import kotlin.time.ExperimentalTime

object PunishCommand {

    private val ERROR_UNKNOWN_TARGET = DynamicCommandExceptionType { target: Any? ->
        MessageComponentSerializer.message().serialize(translatable(
            "command.punish.log.error.unknown_target",
            Argument.string("target", target.toString())
        ))
    }

    private val ERROR_NO_PROFILE = DynamicCommandExceptionType { target: Any? ->
        MessageComponentSerializer.message().serialize(translatable(
            "command.punish.log.error.no_profile",
            Argument.string("target", target.toString())
        ))
    }

    private val ERROR_NO_PUNISHMENT_DATA = DynamicCommandExceptionType { target: Any? ->
        MessageComponentSerializer.message().serialize(translatable(
            "command.punish.log.error.no_punishment_data",
            Argument.string("target", target.toString())
        ))
    }

    private val ERROR_SOURCE_UUID_UNKNOWN = SimpleCommandExceptionType(
        MessageComponentSerializer.message().serialize(
            translatable("command.punish.error.source_uuid_unknown")
        )
    )

    fun create(): LiteralCommandNode<CommandSourceStack> {
        val base = literal("punish")
            .requires(restricted { source ->
                source.sender.hasPermission("nexus.command.punish")
                        && PunishmentsFeature.isEnabled
            })
            .then(literal("log")
                .then(argument("target", string())
                    .executes { context -> handleLog(context, context.getArgument("target", String::class.java)) }))
            .then(literal("withdraw")
                .then(argument("case_id", string())
                    .executes { context -> handleWithdraw(context, context.getArgument("case_id", String::class.java)) }))

        for (type in PunishmentType.entries) {
            val targetArgument = argument("target", string())

            for (severity in PunishmentSeverity.entries) {
                val severityLiteral = literal(severity.name.lowercase())
                    .then(argument("reason", greedyString())
                        .executes { context ->
                            handlePunish(
                                context,
                                context.getArgument("target", String::class.java),
                                type,
                                severity,
                                context.getArgument("reason", String::class.java))
                        })

                targetArgument.then(severityLiteral)
            }

            base.then(literal(type.name.lowercase()).then(targetArgument))
        }

        return base.build()
    }

    private fun handleLog(context: CommandContext<CommandSourceStack>, target: String): Int {
        NexusPlugin.scheduler.launch {
            try {
                val uniqueId = withContext(Dispatchers.IO) { NexusPlugin.server.getPlayerUniqueId(target) }
                    ?: throw ERROR_UNKNOWN_TARGET.create(target)
                val targetProfile = NexusPlugin.profiles.get(id = uniqueId) ?: throw ERROR_NO_PROFILE.create(target)
                val att = targetProfile.getAttachment(AttachmentKeys.PUNISHMENT)
                    ?: throw ERROR_NO_PUNISHMENT_DATA.create(target)
                val generalAtt = targetProfile.getAttachment(AttachmentKeys.GENERAL)
                val punishments = att.punishments
                val logComponent = text()
                    .append(text("▶ ", DARK_GRAY))
                    .append(
                        translatable(
                            "command.punish.log.header",
                            PRIMARY_COLOR,
                            Argument.component(
                                "target",
                                text(generalAtt?.lastKnownName ?: target, MONOCHROME_COLOR)
                            ),
                            Argument.numeric("count", punishments.size)
                        )
                    )

                for (punishment in punishments) {
                    logComponent.appendNewline().append(
                        translatable(
                            "command.punish.log.entry",
                            COMPLEMENTARY_COLOR,
                            Argument.string("case_id", punishment.caseId),
                            Argument.string("type", punishment.type.name),
                            Argument.string("issued_by", punishment.issuedBy),
                            Argument.string("reason", punishment.reason),
                            Argument.string("issued_at", punishment.timestamp.toString()),
                            Argument.string("duration", punishment.severity.duration.toString()),
                            Argument.string("severity", punishment.severity.name)
                            //                    Argument.string("status", if (punishment.isActive) "Active" else "Inactive")
                            //                    Argument.string("expires_at", punishment.getExpirationTime().toString()
                        )
                    )
                }

                if (punishments.isEmpty()) {
                    logComponent
                        .appendNewline()
                        .append(translatable("command.punish.log.entry.none", RED))
                }

                context.source.sender.sendMessage(logComponent)
            } catch (e: CommandSyntaxException) {
                val message = (e.componentMessage() ?: translatable("command.punish.log.error.unknown")).colorIfAbsent(RED)

                context.source.sender.sendMessage(message)
            }
        }

        return Command.SINGLE_SUCCESS
    }

    private fun handleWithdraw(context: CommandContext<CommandSourceStack>, caseId: String): Int {
        NexusPlugin.scheduler.launch {
            try {
                PunishmentsFeature.withdraw(caseId = caseId)

                context.source.sender.sendMessage(translatable(
                    "command.punish.withdraw.success",
                    PRIMARY_COLOR,
                    Argument.component("case_id", text(caseId, MONOCHROME_COLOR))
                ))
            } catch (e: CommandSyntaxException) {
                val message = (e.componentMessage() ?: translatable("command.punish.withdraw.error.unknown")).colorIfAbsent(RED)

                context.source.sender.sendMessage(message)
            }
        }
        return Command.SINGLE_SUCCESS
    }

    private fun handlePunish(
        context: CommandContext<CommandSourceStack>,
        target: String,
        type: PunishmentType,
        severity: PunishmentSeverity,
        reason: String
    ): Int {
        NexusPlugin.scheduler.launch {
            try {
                val senderUniqueId = context.source.sender.get(Identity.UUID).getOrNull()
                    ?: throw ERROR_SOURCE_UUID_UNKNOWN.create()
                val uniqueId = withContext(Dispatchers.IO) { NexusPlugin.server.getPlayerUniqueId(target) }
                    ?: throw ERROR_UNKNOWN_TARGET.create(target)
                val targetProfile = NexusPlugin.profiles.get(id = uniqueId) ?: throw ERROR_NO_PROFILE.create(target)

                PunishmentsFeature.punish(
                    issuer = senderUniqueId,
                    target = targetProfile,
                    type = type,
                    severity = severity,
                    reason = reason
                )

                context.source.sender.sendMessage(translatable(
                    "command.punish.success",
                    PRIMARY_COLOR,
                    Argument.component(
                        "target",
                        text(targetProfile.getAttachment(AttachmentKeys.GENERAL)?.lastKnownName ?: target, MONOCHROME_COLOR)
                    ),
                    Argument.component("type", text(type.name, MONOCHROME_COLOR)),
                    Argument.component("severity", text(severity.name, MONOCHROME_COLOR))
                ))
            } catch (e: CommandSyntaxException) {
                val message = (e.componentMessage() ?: translatable("command.punish.error.unknown")).colorIfAbsent(RED)

                context.source.sender.sendMessage(message)
            }
        }
        return Command.SINGLE_SUCCESS
    }
}