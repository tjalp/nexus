package net.tjalp.nexus.profile.attachment

import net.tjalp.nexus.profile.AttachmentKey
import net.tjalp.nexus.profile.model.ProfileSnapshot

/**
 * Provider interface for managing attachments associated with profiles.
 *
 * @param T The type of the attachment.
 */
interface AttachmentProvider<T : Any> {

    /**
     * The key used to identify the attachment.
     */
    val key: AttachmentKey<T>

    /**
     * Initializes the attachment provider.
     */
    suspend fun init() {
        // optional
    }

    /**
     * Loads the attachment for the given profile.
     *
     * @param profile The [ProfileSnapshot] to load the attachment for.
     * @return The loaded attachment, or null if not found.
     */
    suspend fun load(profile: ProfileSnapshot): T?

    /**
     * Saves the attachment for the given profile.
     *
     * @param profile The [ProfileSnapshot] to save the attachment for.
     * @param value The attachment value to be saved.
     */
    suspend fun save(profile: ProfileSnapshot, value: T) {
        // optional
    }
}