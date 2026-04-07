package net.tjalp.nexus.redis

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.api.coroutines
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import io.lettuce.core.event.connection.ConnectedEvent
import io.lettuce.core.event.connection.DisconnectedEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.reactive.asFlow
import kotlinx.serialization.json.Json

/**
 * A controller for managing Redis connections and pub/sub operations.
 *
 * @param uri The Redis URI for connecting to the Redis server.
 */
@OptIn(ExperimentalLettuceCoroutinesApi::class)
class RedisController(
    uri: RedisURI
) {
    enum class ConnectionState {
        CONNECTED,
        DISCONNECTED
    }

    /**
     * A controller for managing Redis connections and pub/sub operations.
     *
     * @param host The Redis server host.
     * @param port The Redis server port.
     * @param password The optional password for authenticating with the Redis server.
     */
    constructor(host: String, port: Int, password: String? = null) : this(
        RedisURI.builder().withHost(host).withPort(port).withPassword(password).build()
    )

    /**
     * A controller for managing Redis connections and pub/sub operations.
     *
     * @param uri The Redis URI string for connecting to the Redis server.
     */
    constructor(uri: String) : this(RedisURI.create(uri))

    private val client: RedisClient = RedisClient.create(uri)
    private val connection = client.connect()
    private val pubSub = client.connectPubSub()

    /**
     * The Redis commands interface for executing commands on the Redis server.
     */
    val query: RedisCoroutinesCommands<String, String> get() = connection.coroutines()

    /**
     * A flow of Redis transport connection state transitions from Lettuce's event bus.
     *
     * This can be used to observe disconnect/reconnect events without replacing this controller
     * instance, because Lettuce handles automatic reconnection internally.
     */
    fun connectionStates(): Flow<ConnectionState> {
        return client.resources.eventBus().get().asFlow().mapNotNull { event ->
            when (event) {
                is ConnectedEvent -> ConnectionState.CONNECTED
                is DisconnectedEvent -> ConnectionState.DISCONNECTED
                else -> null
            }
        }
    }

    /**
     * Returns whether Redis appears reachable from this controller at the moment.
     */
    suspend fun isReachable(): Boolean = try {
        query.ping()?.equals("PONG", ignoreCase = true) == true
    } catch (_: Exception) {
        false
    }

    /**
     * Publishes a message to a Redis channel corresponding to the given signal key.
     *
     * @param key The signal key representing the channel to publish to.
     * @param message The signal message to publish.
     */
    suspend fun <T : Any> publish(key: SignalKey<T>, message: SignalMessage<T>) {
        val messageSerializer = SignalMessage.serializer(key.serializer)
        query.publish(key.namespace.value, Json.encodeToString(messageSerializer, message))
    }

    /**
     * Publishes a signal to a Redis channel corresponding to the given signal key.
     *
     * @param key The signal key representing the channel to publish to.
     * @param signal The signal data to publish, which will be wrapped in a SignalMessage.
     */
    suspend fun <T : Any> publish(key: SignalKey<T>, signal: T) =
        publish(key, SignalMessage(signal))

    /**
     * Subscribes to a Redis channel corresponding to the given signal key and returns a flow of
     * messages received on that channel.
     *
     * @param key The signal key representing the channel to subscribe to.
     * @return A flow of messages of type T received on the subscribed channel.
     */
    fun <T : Any> subscribe(key: SignalKey<T>): Flow<T> {
        pubSub.reactive().subscribe(key.namespace.value).subscribe()

        val messageSerializer = SignalMessage.serializer(key.serializer)

        return pubSub.reactive().observeChannels().asFlow()
            .filter { it.channel == key.namespace.value }
            .map { Json.decodeFromString(messageSerializer, it.message).data }
    }

    /**
     * Subscribe to Redis keyspace notifications for expired keys matching a pattern.
     * This requires Redis to be configured with `notify-keyspace-events` containing "Ex" or "AE".
     *
     * @param pattern The key pattern to watch for expirations (e.g., "nexus:server:info:*")
     * @return A flow of expired key names
     */
    fun subscribeToKeyExpirations(pattern: String): Flow<String> {
        // Subscribe to keyevent notifications for expired events
        // Pattern: __keyevent@{db}__:expired
        val channel = "__keyevent@0__:expired"
        pubSub.reactive().subscribe(channel).subscribe()

        return pubSub.reactive().observeChannels().asFlow()
            .filter { it.channel == channel }
            .map { it.message } // The message is the expired key name
            .filter { it.startsWith(pattern.substringBefore('*')) }
    }
}
