package net.tjalp.nexus

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import kotlinx.coroutines.runBlocking
import net.tjalp.nexus.command.*
import net.tjalp.nexus.config.NexusConfig
import net.tjalp.nexus.feature.chat.ChatFeature
import net.tjalp.nexus.feature.disguises.DisguiseFeature
import net.tjalp.nexus.feature.gamerules.GameRulesFeature
import net.tjalp.nexus.feature.games.GamesFeature
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
import org.spongepowered.configurate.kotlin.extensions.get
import org.spongepowered.configurate.kotlin.extensions.set

object NexusPlugin : JavaPlugin() {

    lateinit var profiles: ProfilesService; private set
    lateinit var database: Database; private set
    lateinit var scheduler: Scheduler; private set
    lateinit var configuration: NexusConfig; private set

    private val listeners = mutableListOf<Listener>()

    val features: List<Feature>
        get() = listOf(
            ChatFeature,
            DisguiseFeature,
//            EffortShopFeature,
            GameRulesFeature,
            GamesFeature,
            SeasonsFeature,
            TeleportRequestsFeature
        )

    override fun onEnable() {
        reloadConfiguration()

        scheduler = Scheduler(id = "nexus")
        database = Database.connect(
            url = configuration.database.url,
            driver = configuration.database.driver,
            user = configuration.database.user,
            password = configuration.database.password
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
            commands.registrar().register(GameCommand.create(), "Game management commands")
            commands.registrar().register(NexusCommand.create(), "Nexus-specific commands")
            commands.registrar().register(ProfileCommand.create(this), "Profile management commands")
            commands.registrar().register(SeasonCommand.create(), "Season management commands")
            commands.registrar().register(TeleportRequestCommand.create(), "Teleport request commands",
                TeleportRequestCommand.aliases)
        }
    }

    /**
     * Reloads the configuration from disk.
     */
    fun reloadConfiguration() {
        val rootNode = NexusConfig.LOADER.load()
        configuration = rootNode?.get() ?: error("Failed to load configuration")
        rootNode.set(NexusConfig::class, configuration)
        NexusConfig.LOADER.save(rootNode)
    }

    override fun onDisable() {
        features.filter { it.isEnabled }.forEach { it.disable() }
        listeners.forEach { it.unregister() }
    }

    private fun enableFeatures() {
        val modules = configuration.modules

        features.filter {
            when(it) {
                is ChatFeature -> modules.chat.enable
                is DisguiseFeature -> modules.disguises.enable
                is GameRulesFeature -> modules.gamerules.enable
                is GamesFeature -> modules.games.enable
                is SeasonsFeature -> modules.seasons.enable
                is TeleportRequestsFeature -> modules.teleportRequests.enable
                else -> false
            }
        }.forEach {
            try {
                it.enable()
            } catch (e: Throwable) {
                logger.severe("Failed to enable feature '${it.name}': ${e.message}")
                e.printStackTrace()
            }
        }
    }
}