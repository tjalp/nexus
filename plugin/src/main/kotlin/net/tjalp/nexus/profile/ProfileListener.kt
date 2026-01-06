package net.tjalp.nexus.profile

import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent
import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor.RED
import net.tjalp.nexus.NexusPlugin
import net.tjalp.nexus.profile.attachment.AttachmentKeys.GENERAL
import net.tjalp.nexus.profile.attachment.GeneralTable
import net.tjalp.nexus.profile.model.ProfileSnapshot
import net.tjalp.nexus.util.profile
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerLocaleChangeEvent
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.ExperimentalTime

class ProfileListener(private val profiles: ProfilesService) : Listener {

    @OptIn(ExperimentalTime::class)
    @Suppress("UnstableApiUsage")
    @EventHandler
    fun on(event: AsyncPlayerConnectionConfigureEvent) {
        val uniqueId = event.connection.profile.id ?: error("No profile ID in connection")
        val username = event.connection.profile.name
        var profile: ProfileSnapshot? = null

        runBlocking {
            try {
                profile = profiles.get(uniqueId, cache = true, allowCreation = true) ?: error("Failed to load or create profile")
            } catch (e: Throwable) {
                e.printStackTrace()
                event.connection.disconnect(text("An error occurred while loading your profile. Please try again later.", RED))
            }
        }

        NexusPlugin.scheduler.launch {
            profile?.update {
                GeneralTable.update({ GeneralTable.profileId eq uniqueId }) {
                    it[GeneralTable.lastKnownName] = username
                }
            }
        }
    }

    @EventHandler
    fun on(event: PlayerConnectionCloseEvent) {
        profiles.uncache(event.playerUniqueId)
    }

    @EventHandler
    fun on(event: PlayerLocaleChangeEvent) {
        val profile = event.player.profile()
        val locale = event.locale()

        if (profile.getAttachment(GENERAL)?.preferredLocale != null) return

        NexusPlugin.scheduler.launch {
            profile.update {
                GeneralTable.update({ GeneralTable.profileId eq profile.id }) {
                    it[GeneralTable.preferredLocale] = locale.toLanguageTag()
                }
            }
        }
    }
}