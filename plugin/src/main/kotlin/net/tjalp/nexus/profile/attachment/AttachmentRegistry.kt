package net.tjalp.nexus.profile.attachment

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import net.tjalp.nexus.profile.AttachmentKey
import net.tjalp.nexus.profile.model.ProfileEntity
import java.util.concurrent.ConcurrentHashMap

/**
 * Registry for attachment providers.
 * This object maintains a mapping of attachment keys to their corresponding providers.
 */
object AttachmentRegistry {
    private val providers = ConcurrentHashMap<AttachmentKey<*>, AttachmentProvider<*>>()

    fun <T : Any> register(provider: AttachmentProvider<T>) {
        providers[provider.key] = provider
    }

    /**
     * Retrieves the attachment provider for the given key.
     *
     * @param key The attachment key.
     * @return The attachment provider associated with the key, or null if not found.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> providerFor(key: AttachmentKey<T>): AttachmentProvider<T>? =
        providers[key] as? AttachmentProvider<T>

    /**
     * Loads all attachments for the given profile.
     *
     * @param profile The profile to load attachments for.
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun load(profile: ProfileEntity) = coroutineScope {
        providers.forEach { (key, value) ->
            async {
                val provider = value as AttachmentProvider<Any>
                val attachment = provider.load(profile) ?: return@async
                profile.setAttachment(key as AttachmentKey<Any>, attachment)
            }
        }
    }
}