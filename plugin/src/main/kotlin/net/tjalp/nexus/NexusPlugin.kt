package net.tjalp.nexus

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import net.tjalp.nexus.command.NexusCommand
import net.tjalp.nexus.command.ProfileCommand
import net.tjalp.nexus.feature.chat.ChatFeature
import net.tjalp.nexus.feature.gamerules.GameRulesFeature
import net.tjalp.nexus.profile.ProfileListener
import net.tjalp.nexus.profile.ProfileModule
import net.tjalp.nexus.profile.ProfileModuleRegistry
import net.tjalp.nexus.profile.ProfilesService
import net.tjalp.nexus.profile.attachment.GeneralAttachmentModule
import net.tjalp.nexus.profile.service.ExposedProfilesService
import net.tjalp.nexus.util.register
import net.tjalp.nexus.util.unregister
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin
import org.jetbrains.exposed.v1.jdbc.Database

class NexusPlugin : JavaPlugin() {

    lateinit var profiles: ProfilesService; private set
    lateinit var database: Database; private set

    private val listeners = mutableListOf<Listener>()

    val features: List<Feature> by lazy {
        listOf(
            ChatFeature(),
            GameRulesFeature()
        )
    }

    val profileModules: Collection<ProfileModule>
        get() = features.flatMap { it.profileModules } + GeneralAttachmentModule

    override fun onEnable() {
        saveDefaultConfig()

        NexusServices.register(JavaPlugin::class, this)

        database = Database.connect(
            url = config.getString("database.url") ?: error("Database URL not specified in config"),
            driver = config.getString("database.driver") ?: error("Database driver not specified in config"),
            user = config.getString("database.user") ?: error("Database user not specified in config"),
            password = config.getString("database.user") ?: error("Database password not specified in config")
        )

        NexusServices.register(Database::class, database)

        profiles = ExposedProfilesService(
            database,
            ProfileModuleRegistry(profileModules)
        )

        NexusServices.register(ProfilesService::class, profiles)

        listeners += ProfileListener(profiles).also { it.register() }

        enableFeatures()

        // register commands
        this.lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { commands ->
            commands.registrar().register(NexusCommand.create(this), "Nexus-specific commands")
            commands.registrar().register(ProfileCommand.create(this), "Profile management commands")
        }
    }

    override fun onDisable() {
        features.forEach { it.disable() }
        listeners.forEach { it.unregister() }
        NexusServices.clear()
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