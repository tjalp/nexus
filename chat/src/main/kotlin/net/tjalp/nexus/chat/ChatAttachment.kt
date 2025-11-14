package net.tjalp.nexus.chat

import net.tjalp.nexus.common.profile.AttachmentKey
import net.tjalp.nexus.common.profile.Attachments

data class ChatAttachment(
    val messageCount: Int
)

object ChatKeys {
    val CHAT: AttachmentKey<ChatAttachment> = Attachments.key("chat")
}
