package net.tjalp.nexus.common.profile

class AttachmentKey<T : Any> internal constructor(val name: String) {
    override fun toString(): String = "AttachmentKey(name=$name)"
}

object Attachments {
    fun <T : Any> key(name: String): AttachmentKey<T> = AttachmentKey(name)
}