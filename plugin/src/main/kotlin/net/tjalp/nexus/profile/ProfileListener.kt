package net.tjalp.nexus.profile

import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent
import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor.RED
import net.tjalp.nexus.NexusServices
import net.tjalp.nexus.profile.attachment.GeneralTable
import net.tjalp.nexus.profile.model.ProfileSnapshot
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
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

        NexusServices.get<CoroutineScope>().launch {
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
}