package net.tjalp.nexus.common

import kotlin.reflect.KClass

object NexusServices {
    private val services = mutableMapOf<KClass<*>, Any>()

    fun <T : Any> register(type: KClass<T>, instance: T) {
        services[type] = instance
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(type: KClass<T>): T {
        return services[type] as? T
            ?: throw IllegalStateException("Service ${type.simpleName} not registered")
    }

    inline fun <reified T : Any> get(): T = get(T::class)

    fun clear() {
        services.clear()
    }
}