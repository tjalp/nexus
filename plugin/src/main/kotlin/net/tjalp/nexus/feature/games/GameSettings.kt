package net.tjalp.nexus.feature.games

import io.papermc.paper.dialog.Dialog

interface GameSettings {

    var maxPlayers: Int
    var minPlayers: Int

    fun dialog(): Dialog
}