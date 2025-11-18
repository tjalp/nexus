package net.tjalp.nexus

import kotlin.reflect.KClass

/**
 * A simple service locator for managing singleton services within the Nexus application.
 */
object NexusServices {
    private val services = mutableMapOf<KClass<*>, Any>()

    /**
     * Registers a service instance for the given type.
     *
     * @param type The KClass of the service type.
     * @param instance The service instance to register.
     */
    fun <T : Any> register(type: KClass<T>, instance: T) {
        services[type] = instance
    }

    /**
     * Retrieves a service instance for the given type.
     *
     * @param type The KClass of the service type.
     * @return The service instance associated with the given type.
     * @throws IllegalStateException if the service is not registered.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(type: KClass<T>): T {
        return services[type] as? T
            ?: throw IllegalStateException("Service ${type.simpleName} not registered")
    }

    /**
     * Retrieves a service instance for the given type using reified type parameters.
     *
     * @param T The type of the service.
     * @return The service instance associated with the given type.
     * @throws IllegalStateException if the service is not registered.
     */
    inline fun <reified T : Any> get(): T = get(T::class)

    /**
     * Clears all registered services.
     */
    fun clear() {
        services.clear()
    }
}