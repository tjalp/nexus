package net.tjalp.nexus.profile.attachment

import net.tjalp.nexus.profile.AttachmentKey
import net.tjalp.nexus.profile.Attachments

object AttachmentKeys {
    val GENERAL: AttachmentKey<GeneralAttachment> = Attachments.key("general")
    val EFFORT_SHOP: AttachmentKey<EffortShopAttachment> = Attachments.key("effort_shop")
    val PUNISHMENT: AttachmentKey<PunishmentAttachment> = Attachments.key("punishments")
}