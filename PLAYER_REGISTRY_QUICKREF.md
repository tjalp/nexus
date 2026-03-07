# Player Registry Quick Reference

## Getting Started

```kotlin
// Access the player registry
val playerRegistry = getFeature<ServersFeature>().playerRegistry
```

## Common Operations

### Check if player is online
```kotlin
val player = playerRegistry.getPlayer(uuid)
if (player != null) {
    println("${player.username} is online on ${player.serverId}")
}
```

### Get all online players
```kotlin
val players = playerRegistry.getOnlinePlayers()
println("${players.size} players online")
```

### Get players on a specific server
```kotlin
val players = playerRegistry.getPlayersByServer("lobby-1")
players.forEach { player ->
    println(player.username)
}
```

### Get player count (efficient)
```kotlin
val count = playerRegistry.getPlayerCountOnServer("survival-1")
println("$count players on survival-1")
```

### Find which server a player is on
```kotlin
val player = playerRegistry.getPlayer(uuid)
val serverId = player?.serverId
if (serverId != null) {
    val server = serverRegistry.getServer(serverId)
    println("On ${server?.name}")
}
```

## Event Subscriptions

### Player comes online
```kotlin
scope.launch {
    playerRegistry.playerOnlineEvents.collect { event ->
        val player = event.player
        println("${player.username} joined ${player.serverId}")
    }
}
```

### Player goes offline
```kotlin
scope.launch {
    playerRegistry.playerOfflineEvents.collect { event ->
        println("Player ${event.playerId} left ${event.lastServerId}")
    }
}
```

### Player changes servers
```kotlin
scope.launch {
    playerRegistry.playerChangeServerEvents.collect { event ->
        println("Player ${event.playerId}")
        println("  From: ${event.fromServerId}")
        println("  To: ${event.toServerId}")
    }
}
```

## Administrative Operations

### Manual player cleanup
```kotlin
playerRegistry.removePlayer(uuid)
```

### Cleanup crashed server
```kotlin
playerRegistry.cleanupServerPlayers("survival-1")
```

## Advanced Queries

### Find least populated server
```kotlin
suspend fun findLeastPopulated(type: ServerType): String? {
    return serverRegistry.getServersByType(type)
        .minByOrNull { server ->
            playerRegistry.getPlayerCountOnServer(server.id)
        }?.id
}
```

### Check if player can join server
```kotlin
suspend fun canJoin(serverId: String): Boolean {
    val server = serverRegistry.getServer(serverId) ?: return false
    if (server.maxPlayers < 0) return true // Unlimited
    
    val count = playerRegistry.getPlayerCountOnServer(serverId)
    return count < server.maxPlayers
}
```

### Get online friends
```kotlin
suspend fun getOnlineFriends(friendUuids: List<UUID>): List<PlayerInfo> {
    return friendUuids.mapNotNull { uuid ->
        playerRegistry.getPlayer(uuid)?.takeIf { it.serverId != null }
    }
}
```

## Data Models

### PlayerInfo
```kotlin
data class PlayerInfo(
    val id: UUID,              // Player UUID
    val username: String,      // Current username
    val serverId: String?,     // Current server (null = offline)
    val lastSeen: Instant      // Last activity timestamp
)
```

### Events
```kotlin
// Player came online
data class PlayerOnlineEvent(val player: PlayerInfo)

// Player went offline  
data class PlayerOfflineEvent(
    val playerId: String,      // UUID as string
    val lastServerId: String   // Last server they were on
)

// Player changed servers
data class PlayerChangeServerEvent(
    val playerId: String,      // UUID as string
    val fromServerId: String?, // Previous server (null if joining)
    val toServerId: String?    // New server (null if leaving)
)
```

## Performance Tips

✅ **DO**: Use `getPlayerCountOnServer()` instead of `getPlayersByServer().size`
✅ **DO**: Cache player info if you need it repeatedly in a short time
✅ **DO**: Use events instead of polling for changes
❌ **DON'T**: Call `getOnlinePlayers()` on every tick
❌ **DON'T**: Query Redis synchronously from main thread

## Crash Recovery

The system automatically handles server crashes:

1. **TTL-based**: Player data expires after 60s if heartbeat stops
2. **Event-based**: Server offline events trigger immediate cleanup
3. **Heartbeat**: Regular refreshes keep data alive while server runs

You don't need to do anything special - it just works!

## Redis Keys (FYI)

- `nexus:player:info:{uuid}` - Player data (TTL: 60s)
- `nexus:server:players:{serverId}` - Players per server
- `nexus:players:online` - All online players

## Need Help?

- See `README.md` for detailed documentation
- See `PlayerRegistryExamples.kt` for more examples
- See `PLAYER_REGISTRY_ARCHITECTURE.md` for system design

