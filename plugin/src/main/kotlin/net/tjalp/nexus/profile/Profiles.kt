package net.tjalp.nexus.profile

import net.tjalp.nexus.util.asPlayer
import org.bukkit.entity.Player

val ProfileEvent.Updated.player: Player?
    get() = id.asPlayer()