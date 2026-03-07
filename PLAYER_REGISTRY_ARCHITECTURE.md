# Player Registry Architecture Diagram

## System Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                      Multi-Server Network                        │
│                                                                   │
│  ┌──────────────┐   ┌──────────────┐   ┌──────────────┐        │
│  │   Server 1   │   │   Server 2   │   │   Server 3   │        │
│  │  (Survival)  │   │   (Lobby)    │   │  (Creative)  │        │
│  │              │   │              │   │              │        │
│  │  Players:    │   │  Players:    │   │  Players:    │        │
│  │  - Alice     │   │  - Bob       │   │  - Charlie   │        │
│  │  - David     │   │  - Eve       │   │              │        │
│  └──────┬───────┘   └──────┬───────┘   └──────┬───────┘        │
│         │                  │                  │                 │
│         └──────────────────┼──────────────────┘                 │
│                            │                                     │
└────────────────────────────┼─────────────────────────────────────┘
                             │
                    ┌────────▼────────┐
                    │  Redis Cluster  │
                    │                 │
                    │  Player Data    │
                    │  Server Data    │
                    │  Pub/Sub        │
                    └─────────────────┘
```

## Redis Data Structure

```
Redis Keys:
├── nexus:server:info:survival-1         [String, TTL=60s]
│   └── {"id":"survival-1","name":"Survival","type":"SURVIVAL",...}
│
├── nexus:server:info:lobby-1            [String, TTL=60s]
├── nexus:server:info:creative-1         [String, TTL=60s]
│
├── nexus:player:info:{alice-uuid}       [String, TTL=60s]
│   └── {"id":"...","username":"Alice","serverId":"survival-1",...}
│
├── nexus:player:info:{bob-uuid}         [String, TTL=60s]
├── nexus:player:info:{charlie-uuid}     [String, TTL=60s]
│
├── nexus:server:players:survival-1      [Set]
│   └── {alice-uuid, david-uuid}
│
├── nexus:server:players:lobby-1         [Set]
│   └── {bob-uuid, eve-uuid}
│
├── nexus:server:players:creative-1      [Set]
│   └── {charlie-uuid}
│
└── nexus:players:online                 [Set]
    └── {alice-uuid, bob-uuid, charlie-uuid, david-uuid, eve-uuid}

Redis Pub/Sub Channels:
├── server:online
├── server:offline
├── server:heartbeat
├── player:online
├── player:offline
└── player:change-server
```

## Component Interaction Flow

### Player Joins Server

```
Player joins "survival-1"
         │
         ▼
┌─────────────────┐
│ ServersFeature  │ onPlayerJoin(event)
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ PlayerRegistry  │ updatePlayerLocation(uuid, "Alice", "survival-1", ttl=60)
└────────┬────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────┐
│ RedisPlayerRegistry                                     │
│                                                         │
│ 1. Check current player state                          │
│ 2. SETEX nexus:player:info:{uuid} 60 {...json...}     │
│ 3. SADD nexus:players:online {uuid}                   │
│ 4. SADD nexus:server:players:survival-1 {uuid}        │
│ 5. PUBLISH player:online {...}                        │
└─────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────┐
│  All Servers    │ Receive PLAYER_ONLINE event
└─────────────────┘
```

### Server Heartbeat (Every 30s)

```
Timer fires
    │
    ▼
┌─────────────────────────────────────────────────────┐
│ ServersFeature.startHeartbeat()                     │
│                                                     │
│ 1. serverRegistry.updateHeartbeat()                │
│    └─> SETEX nexus:server:info:survival-1 60 {...}│
│                                                     │
│ 2. For each online player:                         │
│    └─> playerRegistry.updatePlayerLocation()      │
│        └─> SETEX nexus:player:info:{uuid} 60 {...}│
└─────────────────────────────────────────────────────┘
```

### Server Crashes

```
Server crashes (no cleanup)
         │
         ▼
┌────────────────────┐
│ Heartbeat stops    │ No more SETEX commands
└────────┬───────────┘
         │
    [Time passes]
         │
         ▼
┌──────────────────────────────────────────┐
│ TTL Expiration (60s later)               │
│                                          │
│ Redis auto-deletes:                      │
│ - nexus:server:info:survival-1          │
│ - nexus:player:info:{alice-uuid}        │
│ - nexus:player:info:{david-uuid}        │
└──────────────────────────────────────────┘

OR (if detected quickly):

Server crash detected
         │
         ▼
┌────────────────────┐
│ Another server     │ Notices missing heartbeat
│ detects offline    │
└────────┬───────────┘
         │
         ▼
┌────────────────────┐
│ ServerRegistry     │ PUBLISH server:offline
└────────┬───────────┘
         │
         ▼
┌────────────────────┐
│ ServersFeature     │ Receives offline event
└────────┬───────────┘
         │
         ▼
┌──────────────────────────────────────────┐
│ PlayerRegistry.cleanupServerPlayers()    │
│                                          │
│ 1. SMEMBERS nexus:server:players:survival-1 │
│ 2. For each player:                      │
│    - SREM nexus:players:online {uuid}   │
│    - DEL nexus:player:info:{uuid}       │
│    - PUBLISH player:offline             │
│ 3. DEL nexus:server:players:survival-1  │
└──────────────────────────────────────────┘
```

## Query Performance

### Get Player by UUID
```
getPlayer(uuid)
    │
    ▼
GET nexus:player:info:{uuid}
    │
    └─> O(1) - Single Redis command
```

### Get Players on Server
```
getPlayersByServer("survival-1")
    │
    ▼
SMEMBERS nexus:server:players:survival-1
    │ Returns: [uuid1, uuid2, ...]
    ▼
For each UUID:
    GET nexus:player:info:{uuid}
    │
    └─> O(N) where N = players on server
```

### Get Player Count on Server (Optimized)
```
getPlayerCountOnServer("survival-1")
    │
    ▼
SCARD nexus:server:players:survival-1
    │
    └─> O(1) - Single Redis command
```

### Get All Online Players
```
getOnlinePlayers()
    │
    ▼
SMEMBERS nexus:players:online
    │ Returns: [uuid1, uuid2, uuid3, ...]
    ▼
For each UUID:
    GET nexus:player:info:{uuid}
    │
    └─> O(N) where N = total online players
```

## Crash Recovery Timeline

```
T=0s    Server crashes
        │
        ├─> Server stops sending heartbeats
        └─> Players still show as "online" in Redis
        
T=30s   Heartbeat interval passes
        │
        └─> Other servers may notice missing heartbeat
        
T=45s   ServerRegistry publishes SERVER_OFFLINE
        │
        ├─> ServersFeature receives event
        └─> Calls playerRegistry.cleanupServerPlayers()
            │
            ├─> Players removed from nexus:players:online
            ├─> Player info keys deleted
            └─> PLAYER_OFFLINE events published
        
T=60s   TTL expires (fallback)
        │
        └─> Redis auto-deletes any remaining keys
        
Result: Maximum ~60s "ghost" time
        Typical: ~30-45s if crash detected
```

## Class Hierarchy

```
┌─────────────────────┐
│  PlayerRegistry     │ (Interface)
│                     │
│ - getPlayer()       │
│ - getOnlinePlayers()│
│ - updatePlayerLocation()
│ - cleanupServerPlayers()
│ - events: Flow      │
└──────────┬──────────┘
           │
           │ implements
           │
           ▼
┌─────────────────────┐
│ RedisPlayerRegistry │ (Implementation)
│                     │
│ Uses:               │
│ - RedisController   │
│ - CoroutineScope    │
│                     │
│ Data structures:    │
│ - player:info:{uuid}│
│ - server:players:*  │
│ - players:online    │
└─────────────────────┘
```

## Integration Points

```
┌─────────────────┐
│ NexusPlugin     │
│                 │
│ - redis         │
└────────┬────────┘
         │
         │ provides
         │
         ▼
┌─────────────────┐
│ ServersFeature  │
│                 │
│ Creates:        │
│ - ServerRegistry│
│ - PlayerRegistry│◄──── You access this!
│                 │
│ Handles:        │
│ - Player join   │
│ - Player quit   │
│ - Heartbeat     │
│ - Server crash  │
└─────────────────┘
```

## Usage in Your Code

```kotlin
// Get the feature
val serversFeature = getFeature<ServersFeature>()
val playerRegistry = serversFeature.playerRegistry

// Query players
val player = playerRegistry.getPlayer(uuid)
val allPlayers = playerRegistry.getOnlinePlayers()
val serverPlayers = playerRegistry.getPlayersByServer("survival-1")

// Subscribe to events
scope.launch {
    playerRegistry.playerOnlineEvents.collect { event ->
        println("${event.player.username} joined!")
    }
}
```

This architecture ensures:
✅ Fast queries (O(1) or O(N))
✅ Automatic crash recovery
✅ Real-time event distribution
✅ Minimal memory usage
✅ Clear separation of concerns

