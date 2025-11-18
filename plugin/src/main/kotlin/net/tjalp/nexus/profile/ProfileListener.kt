package net.tjalp.nexus.profile

import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent
import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor.RED
import net.tjalp.nexus.profile.attachment.GeneralTable
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
        val username = event.connection.profile.name ?: ""
        val id = ProfileId(uniqueId)

        runBlocking {
            try {
                val profile = profiles.get(id, cache = true, allowCreation = true) ?: error("Failed to load or create profile")
                profile.update(additionalStatements = arrayOf({
                    GeneralTable.update({ GeneralTable.profileId eq id.value }) {
                        it[GeneralTable.lastKnownName] = username
                    }
                }))
            } catch (e: Throwable) {
                e.printStackTrace()
                event.connection.disconnect(text("An error occurred while loading your profile. Please try again later.", RED))
            }
        }
    }

    @EventHandler
    fun on(event: PlayerConnectionCloseEvent) {
        val id = ProfileId(event.playerUniqueId)

        profiles.uncache(id)
    }
}