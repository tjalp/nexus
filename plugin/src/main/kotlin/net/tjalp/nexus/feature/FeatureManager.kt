package net.tjalp.nexus.feature

import net.tjalp.nexus.Feature
import net.tjalp.nexus.NexusPlugin
import kotlin.reflect.KClass

/**
 * The FeatureManager is responsible for managing the lifecycle of features in the Nexus plugin.
 * It allows for enabling, disabling, and reloading features based on their definitions and the
 * plugin's configuration.
 */
class FeatureManager {

    private val _features = mutableMapOf<KClass<out Feature>, Feature>()
//    val features: Map<KClass<out Feature>, Feature> get() = _features

    /**
     * Retrieves an enabled feature of the specified class, or null if it is not enabled.
     *
     * @param clazz The class of the feature to retrieve
     * @return The enabled feature instance, or null if not enabled
     */
    fun <T : Feature> getFeature(clazz: KClass<T>): T? {
        return _features[clazz]?.let { it as? T }?.takeIf { it.isEnabled }
    }

    /**
     * Retrieves an enabled feature of the specified class, or null if it is not enabled.
     */
    inline fun <reified T : Feature> getFeature(): T? = getFeature(T::class)

    /**
     * Enables features based on the list of feature definitions. This will dispose of all
     * currently enabled features before enabling the new ones, ensuring a clean state. Each feature
     * will be enabled if its definition indicates that it should be enabled according to the plugin's
     * configuration.
     */
    fun enableFeatures() {
        disposeAll()

        FeatureRegistry.definitions.forEach { definition ->
            if (definition.shouldEnable(NexusPlugin.configuration.features)) {
                enableFeature(definition)
            }
        }
    }

    /**
     * Reloads a specific feature by its class. This will dispose of the currently enabled feature (if any)
     * and then attempt to enable it again based on the provided list of feature definitions. This
     * is useful for dynamically updating a feature's state without affecting other features.
     *
     * @param clazz The class of the feature to reload
     */
    fun <T : Feature> reloadFeature(clazz: KClass<T>) {
        _features[clazz]?.let { feature ->
            if (feature.isEnabled) {
                feature.dispose()
                NexusPlugin.logger.info("Disposed feature: ${feature.id}")
            }
        }

        FeatureRegistry.definitions.find { it.featureClass == clazz }?.let { definition ->
            if (definition.shouldEnable(NexusPlugin.configuration.features)) {
                enableFeature(definition)
            }
        }
    }

    /**
     * Enables a single feature based on its definition. This will create an instance of the feature,
     * store it in the manager's map, and call its enable method. If any exceptions occur during this process,
     * they will be caught and logged, allowing the plugin to continue functioning even if a feature
     * fails to enable properly.
     *
     * @param definition The feature definition to enable
     */
    fun enableFeature(definition: FeatureDefinition) {
        try {
            val feature = definition.create()
            _features[definition.featureClass] = feature
            feature.enable()
            NexusPlugin.logger.info("Enabled feature: ${feature.id}")
        } catch (e: Exception) {
            NexusPlugin.logger.severe("Failed to enable feature '${definition.featureClass.simpleName}': ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Disposes of all currently enabled features. This will call the dispose method on each feature and clear
     * the manager's map of features. If any exceptions occur during this process, they will
     * be caught and logged, allowing the plugin to continue functioning even if a feature fails to dispose properly.
     */
    fun disposeAll() {
        _features.values.filter { it.isEnabled }.forEach { feature ->
            try {
                feature.dispose()
                NexusPlugin.logger.info("Disposed feature: ${feature.id}")
            } catch (e: Exception) {
                NexusPlugin.logger.severe("Failed to dispose feature '${feature.id}': ${e.message}")
                e.printStackTrace()
            }
        }
        _features.clear()
    }
}