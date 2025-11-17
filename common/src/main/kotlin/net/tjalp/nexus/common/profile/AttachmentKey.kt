package net.tjalp.nexus.common.profile

/**
 * Key used to identify attachments associated with profiles.
 *
 * @param T The type of the attachment.
 * @property name The name of the attachment key.
 */
class AttachmentKey<T : Any> internal constructor(val name: String) {
    override fun toString(): String = "AttachmentKey(name=$name)"
}

/**
 * Factory object for creating [AttachmentKey] instances.
 */
object Attachments {

    /**
     * Creates a new [AttachmentKey] with the given name.
     *
     * @param name The name of the attachment key.
     * @return A new [AttachmentKey] instance.
     */
    fun <T : Any> key(name: String): AttachmentKey<T> = AttachmentKey(name)
}