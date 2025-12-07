package net.tjalp.nexus

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import kotlinx.coroutines.runBlocking
import net.tjalp.nexus.command.DisguiseCommand
import net.tjalp.nexus.command.NexusCommand
import net.tjalp.nexus.command.ProfileCommand
import net.tjalp.nexus.command.TeleportRequestCommand
import net.tjalp.nexus.feature.Feature
import net.tjalp.nexus.feature.chat.ChatFeature
import net.tjalp.nexus.feature.disguises.DisguiseFeature
import net.tjalp.nexus.feature.gamerules.GameRulesFeature
import net.tjalp.nexus.feature.seasons.SeasonsFeature
import net.tjalp.nexus.feature.teleportrequests.TeleportRequestsFeature
import net.tjalp.nexus.profile.ProfileListener
import net.tjalp.nexus.profile.ProfilesService
import net.tjalp.nexus.profile.attachment.AttachmentRegistry
import net.tjalp.nexus.profile.attachment.GeneralAttachmentProvider
import net.tjalp.nexus.profile.service.ExposedProfilesService
import net.tjalp.nexus.scheduler.Scheduler
import net.tjalp.nexus.util.PacketManager
import net.tjalp.nexus.util.register
import net.tjalp.nexus.util.unregister
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin
import org.jetbrains.exposed.v1.jdbc.Database

object NexusPlugin : JavaPlugin() {

    lateinit var profiles: ProfilesService; private set
    lateinit var database: Database; private set
    lateinit var scheduler: Scheduler; private set

    private val listeners = mutableListOf<Listener>()

    val features: List<Feature>
        get() = listOf(
            ChatFeature,
            DisguiseFeature,
//            EffortShopFeature,
            GameRulesFeature,
            SeasonsFeature,
            TeleportRequestsFeature
        )

    override fun onEnable() {
        saveDefaultConfig()

        scheduler = Scheduler(id = "nexus")
        database = Database.connect(
            url = config.getString("database.url") ?: error("Database URL not specified in config"),
            driver = config.getString("database.driver") ?: error("Database driver not specified in config"),
            user = config.getString("database.user") ?: error("Database user not specified in config"),
            password = config.getString("database.user") ?: error("Database password not specified in config")
        )
        profiles = ExposedProfilesService(database)
        PacketManager.init()
        listeners += ProfileListener(profiles).also { it.register() }

        // Register global attachment providers
        AttachmentRegistry.register(GeneralAttachmentProvider.also { runBlocking { it.init() } })

        enableFeatures()

        // register commands
        this.lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { commands ->
            commands.registrar().register(DisguiseCommand.create(this), "Disguise management commands")
            commands.registrar().register(NexusCommand.create(this), "Nexus-specific commands")
            commands.registrar().register(ProfileCommand.create(this), "Profile management commands")
            commands.registrar().register(TeleportRequestCommand.create(), "Teleport request commands",
                TeleportRequestCommand.aliases)
        }
    }

    override fun onDisable() {
        features.filter { it.isEnabled }.forEach { it.disable() }
        listeners.forEach { it.unregister() }
    }

    private fun enableFeatures() {
        features.filter { config.getBoolean("modules.${it.name}.enabled", true) }
            .forEach {
                try {
                    it.enable()
                } catch (e: Throwable) {
                    logger.severe("Failed to enable feature '${it.name}': ${e.message}")
                    e.printStackTrace()
                }
            }
    }
}