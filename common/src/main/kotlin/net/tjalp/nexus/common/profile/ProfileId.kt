package net.tjalp.nexus.common.profile

import java.util.*

/**
 * Value class representing a unique identifier for a profile.
 *
 * @property value The UUID value of the profile ID.
 */
@JvmInline
value class ProfileId(val value: UUID)