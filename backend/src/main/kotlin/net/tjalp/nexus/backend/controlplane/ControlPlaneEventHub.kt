package net.tjalp.nexus.backend.controlplane

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import net.tjalp.nexus.controlplane.ControlPlaneEvent

class ControlPlaneEventHub {
    private val events = MutableSharedFlow<ControlPlaneEvent>(extraBufferCapacity = 256)

    suspend fun publish(event: ControlPlaneEvent) {
        events.emit(event)
    }

    fun subscribe(serverId: String): Flow<ControlPlaneEvent> {
        return events.filter { it.serverId == serverId }
    }
}

