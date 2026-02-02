package net.tjalp.nexus.feature.punishments

import com.ibm.icu.text.DateFormat
import com.ibm.icu.text.RelativeDateTimeFormatter
import com.ibm.icu.text.RelativeDateTimeFormatter.Direction
import com.ibm.icu.text.RelativeDateTimeFormatter.RelativeUnit
import com.ibm.icu.util.Calendar
import kotlinx.datetime.TimeZone
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.Component.*
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor.AQUA
import net.kyori.adventure.text.format.NamedTextColor.GRAY
import net.kyori.adventure.text.format.TextDecoration.UNDERLINED
import net.kyori.adventure.text.minimessage.translation.Argument
import net.tjalp.nexus.Constants.PUNISHMENTS_MONOCHROME_COLOR
import net.tjalp.nexus.Constants.PUNISHMENTS_PRIMARY_COLOR
import java.util.*
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
object PunishComponents {

    private val APPEAL: Component
        get() = translatable(
            "punishment.appeal",
            GRAY,
            Argument.component(
                "appeal_url", text("https://nexus.example.com/appeal", AQUA, UNDERLINED)
            )
        )

    /**
     * Create a ban component
     *
     * @param punishment The punishment to create the component for
     * @param timeZone The time zone to use for formatting
     * @param locale The locale to use for formatting
     * @return Ban component
     */
    fun ban(punishment: Punishment, timeZone: TimeZone, locale: Locale): Component {
        val reason = formatReason(punishment)
        val header = translatable(
            "punishment.ban.header",
            PUNISHMENTS_PRIMARY_COLOR,
            Argument.component("reason", reason.colorIfAbsent(PUNISHMENTS_MONOCHROME_COLOR))
        )

        val expires = if (punishment.duration.isInfinite()) {
            translatable("punishment.ban.permanent", PUNISHMENTS_MONOCHROME_COLOR)
        } else translatable(
            "punishment.ban.expires_at",
            GRAY,
            Argument.component(
                "expiry_time",
                formatWithRelativeTime(punishment.expiresAt, timeZone, locale)
            )
        )
        return textOfChildren(
            header,
            newline(),
            newline(), expires,
            newline(),
            newline(), APPEAL,
        )
    }

    /**
     * Create a kick component
     *
     * @param punishment The punishment to create the component for
     * @return Kick component
     */
    fun kick(punishment: Punishment): Component {
        val reason = formatReason(punishment)
        val header = translatable(
            "punishment.kick.header",
            PUNISHMENTS_PRIMARY_COLOR,
            Argument.component("reason", reason.colorIfAbsent(PUNISHMENTS_MONOCHROME_COLOR))
        )

        return textOfChildren(
            header,
            newline(),
            newline(), APPEAL,
        )
    }

    /**
     * Create a mute component
     *
     * @param punishment The punishment to create the component for
     * @param timeZone The time zone to use for formatting
     * @param locale The locale to use for formatting
     * @return Mute component
     */
    fun mute(punishment: Punishment, timeZone: TimeZone, locale: Locale): Component {
        val reason = formatReason(punishment)
        val header = translatable(
            "punishment.mute.header",
            PUNISHMENTS_PRIMARY_COLOR,
            Argument.component("reason", reason.colorIfAbsent(PUNISHMENTS_MONOCHROME_COLOR))
        )

        val expires = if (punishment.duration.isInfinite()) {
            translatable("punishment.mute.permanent", PUNISHMENTS_MONOCHROME_COLOR)
        } else translatable(
            "punishment.mute.expires_at",
            GRAY,
            Argument.component(
                "expiry_time",
                formatWithRelativeTime(punishment.expiresAt, timeZone, locale)
            )
        )

        return textOfChildren(
            header,
            newline(),
            newline(), expires,
            newline(),
            newline(), APPEAL,
        )
    }

    /**
     * Format the reason of a punishment
     *
     * @param punishment The punishment to format the reason for
     * @return Formatted reason component
     */
    private fun formatReason(punishment: Punishment): Component {
        return PunishmentReason.entries.firstOrNull {
            it.key.equals(punishment.reason, ignoreCase = true)
        }?.reason ?: text(punishment.reason)
    }

    /**
     * Format a date with both absolute and relative time
     *
     * @param expiresAt The instant to format
     * @param timeZone The time zone to use for formatting
     * @param locale The locale to use for formatting
     * @param dateStyle The date style (default: FULL)
     * @param timeStyle The time style (default: SHORT)
     * @return Formatted string with absolute date and relative time
     */
    fun formatWithRelativeTime(
        expiresAt: Instant,
        timeZone: TimeZone,
        locale: Locale,
        dateStyle: Int = DateFormat.FULL,
        timeStyle: Int = DateFormat.SHORT
    ): Component {
        val date = Date(expiresAt.toEpochMilliseconds())
        val calendar = Calendar.getInstance(com.ibm.icu.util.TimeZone.getTimeZone(timeZone.id))

        val formatter = DateFormat.getDateTimeInstance(calendar, dateStyle, timeStyle, locale)
        val relativeFormatter = RelativeDateTimeFormatter.getInstance(locale)

        val absoluteDate = formatter.format(date)

        val duration = expiresAt - Clock.System.now()

        val relativeTime = when {
            duration < 1.minutes -> relativeFormatter.format(
                duration.toDouble(DurationUnit.SECONDS).roundToInt().toDouble(), Direction.NEXT, RelativeUnit.SECONDS
            )

            duration < 1.hours -> relativeFormatter.format(
                duration.toDouble(DurationUnit.MINUTES).roundToInt().toDouble(), Direction.NEXT, RelativeUnit.MINUTES
            )

            duration < 1.days -> relativeFormatter.format(
                duration.toDouble(DurationUnit.HOURS).roundToInt().toDouble(), Direction.NEXT, RelativeUnit.HOURS
            )

            duration < 30.days -> relativeFormatter.format(
                duration.toDouble(DurationUnit.DAYS).roundToInt().toDouble(), Direction.NEXT, RelativeUnit.DAYS
            )

            duration < 365.days -> relativeFormatter.format(
                (duration.toDouble(DurationUnit.DAYS) / 30).roundToInt().toDouble(), Direction.NEXT, RelativeUnit.MONTHS
            )

            else -> relativeFormatter.format(
                (duration.toDouble(DurationUnit.DAYS) / 365).roundToInt().toDouble(), Direction.NEXT, RelativeUnit.YEARS
            )
        }

        return text(relativeTime, PUNISHMENTS_MONOCHROME_COLOR)
            .hoverEvent(HoverEvent.showText(text(absoluteDate, PUNISHMENTS_PRIMARY_COLOR)))
    }
}