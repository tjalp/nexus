package net.tjalp.nexus.redis

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

/**
 * Represents a namespace for signals, allowing for categorization and organization of different types of signals.
 *
 * @property value The string value of the namespace.
 */
@Serializable
@JvmInline
value class SignalNamespace(val value: String)

/**
 * Represents a key for a signal, containing the namespace, type, and serializer for the signal data.
 *
 * @param T The type of the signal data.
 * @property namespace The namespace of the signal.
 * @property type The KClass representing the type of the signal data.
 * @property serializer The KSerializer for serializing and deserializing the signal data.
 */
data class SignalKey<T : Any>(
    val namespace: SignalNamespace,
    val type: KClass<T>,
    val serializer: KSerializer<T>
)

/**
 * Represents a message containing signal data to be published or received.
 *
 * @param T The type of the signal data.
 * @property data The actual data of the signal message.
 */
@Serializable
data class SignalMessage<T>(
//    val sender: String,
    val data: T
)
