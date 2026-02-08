@file:OptIn(ExperimentalTime::class)

package net.tjalp.nexus.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType.greedyString
import com.mojang.brigadier.arguments.StringArgumentType.string
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands.*
import io.papermc.paper.command.brigadier.MessageComponentSerializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.kyori.adventure.identity.Identity
import net.kyori.adventure.key.Key.key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.sound.Sound.sound
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.Component.translatable
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor.*
import net.kyori.adventure.text.minimessage.translation.Argument
import net.tjalp.nexus.Constants.MONOCHROME_COLOR
import net.tjalp.nexus.Constants.PRIMARY_COLOR
import net.tjalp.nexus.Constants.PUNISHMENTS_MONOCHROME_COLOR
import net.tjalp.nexus.Constants.PUNISHMENTS_PRIMARY_COLOR
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.feature.punishments.PunishmentReason
import net.tjalp.nexus.feature.punishments.PunishmentSeverity
import net.tjalp.nexus.feature.punishments.PunishmentType
import net.tjalp.nexus.profile.attachment.AttachmentKeys
import org.bukkit.command.ConsoleCommandSender
import java.util.concurrent.CompletableFuture
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

    private val punishments
        get() = NexusPlugin.punishments ?: error("Punishments feature is not enabled")

    fun create(): LiteralCommandNode<CommandSourceStack> {
        val base = literal("punish")
            .requires(restricted { source ->
                source.sender.hasPermission("nexus.command.punish")
                        && NexusPlugin.punishments != null
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
                        .suggests { _, builder -> suggestReasons(builder) }
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
        punishments.scheduler.launch {
            try {
                val uniqueId = withContext(Dispatchers.IO) { NexusPlugin.server.getPlayerUniqueId(target) }
                    ?: throw ERROR_UNKNOWN_TARGET.create(target)
                val targetProfile = NexusPlugin.profiles.get(id = uniqueId) ?: throw ERROR_NO_PROFILE.create(target)
                val att = targetProfile.getAttachment(AttachmentKeys.PUNISHMENT)
                    ?: throw ERROR_NO_PUNISHMENT_DATA.create(target)
                val generalAtt = targetProfile.getAttachment(AttachmentKeys.GENERAL)
                val punishments = att.punishments
                val logComponent = text()
                    .append(
                        translatable(
                            "command.punish.log.header",
                            PUNISHMENTS_PRIMARY_COLOR,
                            Argument.component(
                                "target",
                                text(generalAtt?.lastKnownName ?: target, PUNISHMENTS_MONOCHROME_COLOR)
                            ),
                            Argument.numeric("count", punishments.size)
                        )
                    )

                for (punishment in punishments) {
                    val args = arrayOf(
                        Argument.component("case_id", text(punishment.caseId, PUNISHMENTS_MONOCHROME_COLOR)),
                        Argument.string("type", punishment.type.name),
                        Argument.string("issued_by", punishment.issuedBy),
                        Argument.string("reason", punishment.reason),
                        Argument.string("issued_at", punishment.timestamp.toString()),
                        Argument.string("duration", punishment.severity.duration.toString()),
                        Argument.string("severity", punishment.severity.name),
                        Argument.component(
                            "status",
                            if (punishment.isActive) {
                                translatable("command.punish.log.entry.status.active", GREEN)
                            } else translatable("command.punish.log.entry.status.inactive", RED)
                        ),
                        Argument.string("expires_at", punishment.expiresAt.toString())
                    )
                    logComponent.appendNewline()
                        .append(text("▶ ", DARK_GRAY))
                        .append(translatable("command.punish.log.entry", PUNISHMENTS_PRIMARY_COLOR, *args)
                            .clickEvent(ClickEvent.runCommand("/punish withdraw ${punishment.caseId}")))
                }

                if (punishments.isEmpty()) {
                    logComponent
                        .appendNewline()
                        .append(text("▶ ", DARK_GRAY))
                        .append(translatable("command.punish.log.entry.none", PRIMARY_COLOR))
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
        punishments.scheduler.launch {
            try {
                punishments.withdraw(caseId = caseId)

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

    private fun suggestReasons(builder: SuggestionsBuilder): CompletableFuture<Suggestions> {
        PunishmentReason.entries
            .map { it.key }
            .filter { it.startsWith(builder.remaining, ignoreCase = true) }
            .sorted()
            .forEach { builder.suggest(it) }

        return builder.buildFuture()
    }

    private fun handlePunish(
        context: CommandContext<CommandSourceStack>,
        target: String,
        type: PunishmentType,
        severity: PunishmentSeverity,
        reason: String
    ): Int {
        punishments.scheduler.launch {
            try {
                var senderId = context.source.sender.get(Identity.UUID).getOrNull()?.toString()
                    ?: context.source.sender.get(Identity.NAME).getOrNull()

                if (senderId == null && context.source.sender !is ConsoleCommandSender) {
                    throw ERROR_SOURCE_UUID_UNKNOWN.create()
                } else senderId = "System"

                val uniqueId = withContext(Dispatchers.IO) { NexusPlugin.server.getPlayerUniqueId(target) }
                    ?: throw ERROR_UNKNOWN_TARGET.create(target)
                val targetProfile = NexusPlugin.profiles.get(id = uniqueId) ?: throw ERROR_NO_PROFILE.create(target)

                val punishment = punishments.punish(
                    issuer = senderId,
                    target = targetProfile,
                    type = type,
                    severity = severity,
                    reason = reason
                )

                if (punishment == null) {
                    context.source.sender.sendMessage(translatable(
                        "command.punish.error.execution_failed",
                        RED
                    ))
                    return@launch
                }

                context.source.sender.playSound(sound {
                    it.type(key("minecraft:entity.pillager.death"))
                    it.source(Sound.Source.MASTER)
                }, Sound.Emitter.self())

                context.source.sender.sendMessage(translatable(
                    "command.punish.success",
                    PRIMARY_COLOR,
                    Argument.component(
                        "target",
                        text(targetProfile.getAttachment(AttachmentKeys.GENERAL)?.lastKnownName ?: target, MONOCHROME_COLOR)
                    ),
                    Argument.component("type", text(type.name, MONOCHROME_COLOR)),
                    Argument.component("severity", text(severity.name, MONOCHROME_COLOR)),
                    Argument.component("case_id", text(punishment.caseId, MONOCHROME_COLOR))
                ))
            } catch (e: CommandSyntaxException) {
                val message = (e.componentMessage() ?: translatable("command.punish.error.unknown")).colorIfAbsent(RED)

                context.source.sender.sendMessage(message)
            }
        }
        return Command.SINGLE_SUCCESS
    }
}