package net.tjalp.nexus.profile.attachment

import kotlinx.serialization.Serializable

/**
 * Represents an attachment that can be associated with a profile.
 * Attachments are used to store additional data related to a profile,
 * such as punishments, notices, or general information. Each
 * attachment type should implement this interface.
 */
@Serializable
sealed interface ProfileAttachment