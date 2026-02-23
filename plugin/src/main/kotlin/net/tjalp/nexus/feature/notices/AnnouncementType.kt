package net.tjalp.nexus.feature.notices

enum class AnnouncementType(val command: String) {
    CHAT("chat"),
    ACTION_BAR("actionbar"),
    TITLE("title"),
}