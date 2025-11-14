package net.tjalp.nexus.common.profile

class AttachmentKey<T : Any> internal constructor(val name: String)

object Attachments {
    fun <T : Any> key(name: String): AttachmentKey<T> = AttachmentKey(name)
}