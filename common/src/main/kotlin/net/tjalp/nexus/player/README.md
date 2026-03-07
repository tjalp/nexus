# Player Registry System

## Overview

The Player Registry system provides a crash-resilient way to track player locations across your multi-server network. It separates player tracking concerns from the server registry, making it easier to query players by server or globally.

## Architecture

### Data Structures

The system uses Redis with the following data structures:

1. **Player Info Keys**: `nexus:player:info:{uuid}`
   - Stores: JSON serialized `PlayerInfo` object
   - TTL: Synced with server heartbeat (default 60s)
   - Expires automatically if server crashes

2. **Server Player Sets**: `nexus:server:players:{serverId}`
   - Stores: Set of player UUID strings on each server
   - Used for efficient "players on server X" queries
   - Cleaned up when server goes offline

3. **Global Online Set**: `nexus:players:online`
   - Stores: Set of all online player UUIDs
   - Used for efficient "all online players" queries
   - Updated on join/leave/crash

### Crash Resilience

**How it handles server crashes:**

1. **TTL-based expiration**: Player data expires with the server heartbeat TTL (typically 60s)
2. **Server offline listener**: When a server goes offline, `cleanupServerPlayers()` is called
3. **Heartbeat refresh**: Every heartbeat updates player TTLs to keep them alive

**Timeline when a server crashes:**
- T+0s: Server crashes, stops sending heartbeats
- T+0s to T+60s: Players still appear online (grace period)
- T+60s: Player keys expire automatically via Redis TTL
- Meanwhile: If another server detects the offline event, cleanup happens immediately

## Usage

### Basic Queries

```kotlin
val playerRegistry: PlayerRegistry = // injected

// Get a specific player
val player = playerRegistry.getPlayer(playerId)
println("Player ${player?.username} is on server ${player?.serverId}")

// Get all online players
val onlinePlayers = playerRegistry.getOnlinePlayers()
println("${onlinePlayers.size} players online")

// Get players on a specific server
val serverPlayers = playerRegistry.getPlayersByServer("lobby-1")
serverPlayers.forEach { player ->
    println("${player.username} is on lobby-1")
}

// Get all players (including recently offline)
val allPlayers = playerRegistry.getAllPlayers()
```

### Updating Player Location

```kotlin
// Player joins a server
playerRegistry.updatePlayerLocation(
    playerId = player.uniqueId,
    username = player.name,
    serverId = "survival-1",
    ttl = 60
)

// Player leaves the network
playerRegistry.updatePlayerLocation(
    playerId = player.uniqueId,
    username = player.name,
    serverId = null
)
```

### Event Subscriptions

```kotlin
// Subscribe to player online events
scope.launch {
    playerRegistry.playerOnlineEvents.collect { event ->
        println("${event.player.username} came online on ${event.player.serverId}")
    }
}

// Subscribe to player offline events
scope.launch {
    playerRegistry.playerOfflineEvents.collect { event ->
        println("Player ${event.playerId} went offline from ${event.lastServerId}")
    }
}

// Subscribe to server change events
scope.launch {
    playerRegistry.playerChangeServerEvents.collect { event ->
        println("Player ${event.playerId} moved from ${event.fromServerId} to ${event.toServerId}")
    }
}
```

### Server Crash Cleanup

```kotlin
// Listen for server offline events and cleanup players
scope.launch {
    serverRegistry.serverOfflineEvents.collect { event ->
        playerRegistry.cleanupServerPlayers(event.serverId)
        println("Cleaned up players from crashed server ${event.serverId}")
    }
}
```

## Integration with ServersFeature

The `ServersFeature` automatically handles:
- Creating the `PlayerRegistry` instance
- Tracking players on join/quit
- Refreshing player TTLs during heartbeat
- Cleaning up players when servers go offline

Access the player registry from your feature:
```kotlin
val serversFeature = getFeature<ServersFeature>()
val playerRegistry = serversFeature.playerRegistry

// Now you can query players
val players = playerRegistry.getOnlinePlayers()
```

## Performance Considerations

### Query Performance

- `getPlayer(uuid)`: O(1) - Single Redis GET
- `getOnlinePlayers()`: O(N) - One GET per online player
- `getPlayersByServer(serverId)`: O(N) - One GET per player on that server
- `getAllPlayers()`: Currently same as `getOnlinePlayers()`

### Optimization Tips

1. **Cache frequently accessed data**: If you need to check the same player repeatedly, cache the result
2. **Batch operations**: When checking multiple players, consider batching Redis calls
3. **Use events**: Subscribe to events instead of polling for changes

## Future Enhancements

Potential improvements to consider:

1. **Persistent History**: Store offline players with longer TTL for "recently seen" queries
2. **Batch Updates**: Add methods to update multiple players at once
3. **Redis Pipeline**: Use Redis pipelining for bulk operations
4. **Secondary Indexes**: Add indexes for username lookups
5. **Metrics**: Track player count history and server load

## Migration from ServerRegistry

If you were using `ServerRegistry` for player tracking:

**Old code:**
```kotlin
serverRegistry.setPlayerServer(playerId, serverId)
val serverId = serverRegistry.getPlayerServer(playerId)
val players = serverRegistry.getPlayersOnServer(serverId)
```

**New code:**
```kotlin
playerRegistry.updatePlayerLocation(playerId, username, serverId, ttl)
val player = playerRegistry.getPlayer(playerId)
val serverId = player?.serverId
val players = playerRegistry.getPlayersByServer(serverId)
```

The old `ServerRegistry` methods are deprecated but kept for backwards compatibility (returning empty results).

