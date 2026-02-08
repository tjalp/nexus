package net.tjalp.nexus

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import kotlinx.coroutines.runBlocking
import net.tjalp.nexus.NexusPlugin.configuration
import net.tjalp.nexus.command.*
import net.tjalp.nexus.config.NexusConfig
import net.tjalp.nexus.feature.FeatureManager
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
    lateinit var features: FeatureManager; private set

    private val listeners = mutableListOf<Listener>()

    // ** Features **

    val chat: ChatFeature? get() = features.getFeature()
    val disguises: DisguiseFeature? get() = features.getFeature()
    val gameRules: GameRulesFeature? get() = features.getFeature()
    val games: GamesFeature? get() = features.getFeature()
    val notices: NoticesFeature? get() = features.getFeature()
    val physicalSpectator: PhysicalSpectatorFeature? get() = features.getFeature()
    val punishments: PunishmentsFeature? get() = features.getFeature()
    val seasons: SeasonsFeature? get() = features.getFeature()
    val teleportRequests: TeleportRequestsFeature? get() = features.getFeature()
    val waypoints: WaypointsFeature? get() = features.getFeature()

    // ** End Features **

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

        features = FeatureManager().also { it.enableFeatures() }

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
                register(RulesCommand.create(), "Show rules dialog")
                register(SeasonCommand.create(), "Season management commands")
                register(TeleportRequestCommand.create(), "Teleport request commands", TeleportRequestCommand.aliases)
                register(TimeZoneCommand.create(), "Time zone management commands")
                register(WaypointCommand.create(), "Waypoint management commands")
            }
        }
    }

    /**
     * Reloads the plugin configuration from disk. This will update the [configuration] property.
     */
    fun reloadConfiguration() {
        configuration = NexusConfig.reload(dataPath)
    }

    override fun onDisable() {
        features.disposeAll()
        listeners.forEach { it.unregister() }
    }

    /**
     * Runs database migrations using Flyway. Migrations should be located in the `resources/db/migration` directory.
     */
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
}