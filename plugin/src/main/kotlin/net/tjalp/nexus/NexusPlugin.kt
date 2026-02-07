package net.tjalp.nexus

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import kotlinx.coroutines.runBlocking
import net.tjalp.nexus.command.*
import net.tjalp.nexus.config.NexusConfig
import net.tjalp.nexus.feature.chat.ChatFeature
import net.tjalp.nexus.feature.disguises.DisguiseFeature
import net.tjalp.nexus.feature.gamerules.GameRulesFeature
import net.tjalp.nexus.feature.games.GamesFeature
import net.tjalp.nexus.feature.notices.NoticesFeature
import net.tjalp.nexus.feature.physicalspectator.PhysicalSpectatorFeature
import net.tjalp.nexus.feature.punishments.PunishmentsFeature
import net.tjalp.nexus.feature.seasons.SeasonsFeature
import net.tjalp.nexus.feature.teleportrequests.TeleportRequestsFeature
import net.tjalp.nexus.feature.waypoints.WaypointsFeature
import net.tjalp.nexus.lang.Lang
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
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database

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
            NoticesFeature,
            PhysicalSpectatorFeature,
            PunishmentsFeature,
            SeasonsFeature,
            TeleportRequestsFeature,
//            WaypointsFeature
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
        runMigrations()
        profiles = ExposedProfilesService(database)
        PacketManager.init()
        listeners += ProfileListener(profiles).also { it.register() }

        // Register global attachment providers
        AttachmentRegistry.register(GeneralAttachmentProvider.also { runBlocking { it.init() } })

        Lang.init() // initialize localization system

        enableFeatures()

        // register commands
        this.lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { commands ->
            commands.registrar().apply {
                register(BodyCommand.create(), "Body management commands")
                register(DisguiseCommand.create(this@NexusPlugin), "Disguise management commands")
                register(GameCommand.create(), "Game management commands")
                register(LanguageCommand.create(), "Language management commands", LanguageCommand.aliases)
                register(NexusCommand.create(), "Nexus-specific commands")
                register(ProfileCommand.create(), "Profile management commands")
                register(PunishCommand.create(), "Punishment management commands")
                register(RecommendationsCommand.create(), "Show recommendations dialog")
                register(SeasonCommand.create(), "Season management commands")
                register(TeleportRequestCommand.create(), "Teleport request commands", TeleportRequestCommand.aliases)
                register(TimeZoneCommand.create(), "Time zone management commands")
                register(WaypointCommand.create(), "Waypoint management commands")
            }
        }
    }

    fun reloadConfiguration() {
        configuration = NexusConfig.reload(dataPath)
    }

    override fun onDisable() {
        features.filter { it.isEnabled }.forEach { it.disable() }
        listeners.forEach { it.unregister() }
    }

    private fun runMigrations() {
        val conf = configuration.database
        val pluginClassLoader = this::class.java.classLoader
        val flyway = Flyway.configure(pluginClassLoader)
            .dataSource(conf.url, conf.user, conf.password)
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .load()

        logger.info("Running database migrations...")
        flyway.migrate()
    }

    fun enableFeatures() {
        val modules = configuration.features

        features.filter {
            when (it) {
                is ChatFeature -> modules.chat.enable
                is DisguiseFeature -> modules.disguises.enable
                is GameRulesFeature -> modules.gamerules.enable
                is GamesFeature -> modules.games.enable
                is NoticesFeature -> modules.notices.enable
                is PhysicalSpectatorFeature -> modules.physicalSpectator.enable
                is PunishmentsFeature -> modules.punishments.enable
                is SeasonsFeature -> modules.seasons.enable
                is TeleportRequestsFeature -> modules.teleportRequests.enable
                is WaypointsFeature -> modules.waypoints.enable
                else -> error("Unknown feature: ${it.name} ($${it::class.qualifiedName})")
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