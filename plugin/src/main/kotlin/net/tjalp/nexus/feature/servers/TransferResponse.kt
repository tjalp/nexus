package net.tjalp.nexus.feature.servers

data class TransferResponse(
    val status: TransferStatus
)

enum class TransferStatus {
    SUCCESS,
    SERVER_NOT_FOUND,
    ALREADY_ON_SERVER,
    UNAVAILABLE,
    UNKNOWN
}