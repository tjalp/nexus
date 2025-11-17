package net.tjalp.nexus.plugin

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import net.tjalp.nexus.chat.ChatFeature
import net.tjalp.nexus.common.*
import net.tjalp.nexus.common.profile.ProfileListener
import net.tjalp.nexus.common.profile.ProfileModule
import net.tjalp.nexus.common.profile.ProfileModuleRegistry
import net.tjalp.nexus.common.profile.ProfilesService
import net.tjalp.nexus.common.profile.attachment.GeneralAttachmentModule
import net.tjalp.nexus.common.profile.service.ExposedProfilesService
import net.tjalp.nexus.gamerules.GameRulesFeature
import net.tjalp.nexus.plugin.command.NexusCommand
import net.tjalp.nexus.plugin.command.ProfileCommand
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

        PacketManager.init(this)

        database = Database.connect(
            "jdbc:postgresql://localhost:5432/postgres",
            driver = "org.postgresql.Driver",
            user = "postgres",
            password = "postgres"
        )

        NexusServices.register(Database::class, database)

        profiles = ExposedProfilesService(
            database,
            ProfileModuleRegistry(profileModules)
        )

        NexusServices.register(ProfilesService::class, profiles)

        listeners += ProfileListener(profiles).also { it.register(this) }

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