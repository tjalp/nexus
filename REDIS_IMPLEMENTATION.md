# Redis Network System - Implementation Details

## Overview

The Nexus multi-server network uses Redis as a centralized data store and pub/sub system. This document details the implementation, focusing on crash recovery and data consistency.

## Key Improvements Implemented

### 1. **Keyspace Notifications for Crash Detection**

Redis keyspace notifications automatically detect when server keys expire, enabling immediate crash detection.

**How it works:**
- Each server's info key has a TTL (default: 60 seconds)
- Servers refresh TTL via heartbeat (default: every 30 seconds)
- If a server crashes, it stops sending heartbeats
- After TTL expires, Redis emits a keyevent notification
- All servers listening to `__keyevent@0__:expired` are notified
- Automatic cleanup is triggered

**Configuration:**
```kotlin
// Automatically configured on server startup
RedisConfig.enableKeyspaceNotifications(redis)
```

Redis must have: `CONFIG SET notify-keyspace-events Ex`
- `E` = keyevent notifications
- `x` = expired event notifications

### 2. **TTL Management for All Data Structures**

Every piece of data in Redis has a TTL to prevent stale data:

| Data Structure | TTL Source | Purpose |
|----------------|------------|---------|
| `nexus:server:info:{serverId}` | Server heartbeat | Server metadata |
| `nexus:server:players:{serverId}` | Server heartbeat | Players on server |
| `nexus:player:info:{uuid}` | Player heartbeat | Player metadata |
| `nexus:players:online` | No TTL* | Global online set |

*The global online set is cleaned up via keyspace notifications when player info keys expire.

### 3. **Automatic Cleanup on Server Crash**

**Flow:**
```
T=0s    Server crashes
        └─> Heartbeat stops
        
T=30s   Next heartbeat interval passes
        └─> No heartbeat received
        
T=60s   TTL expires on nexus:server:info:server-1
        └─> Redis emits keyevent notification
        └─> RedisServerRegistry catches expiration
            ├─> Deletes nexus:server:players:server-1
            └─> Publishes SERVER_OFFLINE event
                └─> ServersFeature receives event
                    └─> Calls playerRegistry.cleanupServerPlayers()
                        ├─> Publishes PLAYER_OFFLINE for each player
                        ├─> Removes from nexus:players:online
                        └─> Deletes player info keys

Maximum stale data time: TTL duration (60s default)
Typical detection time: 30-60s after crash
```

### 4. **Per-Server Player Set TTL**

The per-server player sets now have TTL:

```kotlin
// When adding a player to a server
redis.query.sadd(SERVER_PLAYERS_PREFIX + serverId, playerIdStr)
redis.query.expire(SERVER_PLAYERS_PREFIX + serverId, ttl) // ← NEW

// When heartbeat refreshes
redis.query.setex(SERVER_INFO_PREFIX + serverId, ttl, json)
redis.query.expire(SERVER_PLAYERS_PREFIX + serverId, ttl) // ← Refresh TTL
```

This ensures that if a server crashes:
1. Server info key expires
2. Per-server player set expires at same time
3. No stale player references remain

### 5. **Fixed HGETALL Bug**

**Previous code (WRONG):**
```kotlin
redis.query.hgetall("$SERVER_INFO_PREFIX*")  // ❌ HGETALL is for hash fields!
```

**New code (CORRECT):**
```kotlin
redis.query.keys(SERVER_INFO_PREFIX + "*").collect { key ->  // ✓ KEYS for pattern matching
    val json = redis.query.get(key)
    // ...
}
```

**Why this matters:**
- `HGETALL` retrieves fields from a single Redis hash
- We're using String keys, not hash fields
- `KEYS` or `SCAN` is needed for pattern matching across keys

## Redis Data Structures

### Server Data

```
nexus:server:info:survival-1     [String, TTL=60s]
{
  "id": "survival-1",
  "name": "Survival Server",
  "type": "SURVIVAL",
  "host": "play.example.com",
  "port": 25566,
  "maxPlayers": 100,
  "online": true
}

nexus:server:players:survival-1  [Set, TTL=60s]
{
  "550e8400-e29b-41d4-a716-446655440000",
  "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
  "6ba7b814-9dad-11d1-80b4-00c04fd430c8"
}
```

### Player Data

```
nexus:player:info:550e8400-e29b-41d4-a716-446655440000  [String, TTL=60s]
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "username": "Alice",
  "serverId": "survival-1",
  "lastSeen": "2026-03-07T15:30:00Z"
}

nexus:players:online  [Set, No TTL]
{
  "550e8400-e29b-41d4-a716-446655440000",
  "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
  ...
}
```

## Pub/Sub Channels

| Channel | Purpose | Payload |
|---------|---------|---------|
| `server:online` | Server comes online | `ServerOnlineEvent` |
| `server:offline` | Server goes offline | `ServerOfflineEvent` |
| `server:heartbeat` | Server heartbeat | `ServerHeartbeat` |
| `player:online` | Player joins network | `PlayerOnlineEvent` |
| `player:offline` | Player leaves network | `PlayerOfflineEvent` |
| `player:change-server` | Player changes servers | `PlayerChangeServerEvent` |
| `__keyevent@0__:expired` | Redis key expired | Key name (string) |

## Best Practices

### 1. **Always Set TTL**

When storing temporary data, always set TTL:

```kotlin
// ✓ Good
redis.query.setex(key, ttl, value)

// ❌ Bad (data persists forever if not cleaned up)
redis.query.set(key, value)
```

### 2. **Refresh TTL on Updates**

When updating related data, refresh all TTLs:

```kotlin
// Update player location
redis.query.setex(PLAYER_INFO_PREFIX + uuid, ttl, json)
redis.query.expire(SERVER_PLAYERS_PREFIX + serverId, ttl) // ← Don't forget!
```

### 3. **Use Keyspace Notifications**

Subscribe to key expirations for automatic cleanup:

```kotlin
redis.subscribeToKeyExpirations(SERVER_INFO_PREFIX).collect { expiredKey ->
    // Handle server crash
}
```

### 4. **Graceful Shutdown**

Always clean up on graceful shutdown:

```kotlin
override fun onDisable() {
    serverRegistry.unregisterServer(serverId)  // Immediate cleanup
    // Don't rely on TTL for graceful shutdowns
}
```

## Performance Considerations

### KEYS vs SCAN

Current implementation uses `KEYS` for simplicity:

```kotlin
redis.query.keys(SERVER_INFO_PREFIX + "*").collect { key -> ... }
```

**For production with many servers (>100), consider SCAN:**

```kotlin
// Cursor-based iteration, non-blocking
var cursor = "0"
do {
    val result = redis.query.scan(cursor, "MATCH", "$SERVER_INFO_PREFIX*")
    cursor = result.cursor
    result.keys.forEach { key -> ... }
} while (cursor != "0")
```

### Pipelining for Bulk Operations

For cleaning up multiple players, consider pipelining:

```kotlin
// Current: Multiple round-trips
for (playerId in playerIds) {
    redis.query.del(PLAYER_INFO_PREFIX + playerId)
    redis.query.srem(ONLINE_PLAYERS_SET, playerId)
}

// Better: Single pipeline
redis.query.multi()
playerIds.forEach { playerId ->
    redis.query.del(PLAYER_INFO_PREFIX + playerId)
    redis.query.srem(ONLINE_PLAYERS_SET, playerId)
}
redis.query.exec()
```

## Testing Crash Recovery

### Scenario 1: Server Crashes

1. Start 2 servers
2. Kill one server process (simulate crash)
3. Wait 60 seconds (or configured TTL)
4. Check other server logs for SERVER_OFFLINE event
5. Verify players from crashed server are marked offline

### Scenario 2: Network Partition

1. Start 2 servers
2. Block network between server and Redis
3. Server can't send heartbeat
4. After TTL, server is marked offline
5. Reconnect network
6. Server re-registers automatically

### Scenario 3: Redis Restart

1. Stop Redis
2. Restart Redis
3. All servers lose state
4. Servers re-register on next heartbeat
5. Players are tracked from that point

## Configuration

### Server Configuration

```yaml
features:
  servers:
    serverId: "survival-1"
    serverName: "Survival Server"
    serverType: "SURVIVAL"
    host: "play.example.com"
    port: 25566
    maxPlayers: 100
    heartbeatIntervalSeconds: 30    # How often to send heartbeat
    heartbeatTimeoutSeconds: 60     # TTL for server/player data
```

### Redis Configuration

**Required settings:**
```
notify-keyspace-events Ex
```

**Optional (for performance):**
```
maxmemory-policy volatile-ttl   # Evict keys with TTL when memory is full
```

## Troubleshooting

### "Server crash detection not working"

Check Redis config:
```bash
redis-cli CONFIG GET notify-keyspace-events
```

Should return: `"Ex"` or `"AEx"` (A includes all events)

### "Stale player data"

- Verify heartbeat is running: Check server logs for heartbeat messages
- Verify TTL is set: `redis-cli TTL nexus:player:info:{uuid}`
- Check keyspace notifications: `redis-cli CONFIG GET notify-keyspace-events`

### "High Redis memory usage"

- Check for keys without TTL: `redis-cli KEYS nexus:* | xargs redis-cli TTL`
- Any key returning `-1` has no TTL and needs fixing
- Consider lowering TTL values (balance between crash detection speed and memory)

## Future Improvements

1. **Cursor-based SCAN** for large server counts
2. **Pipeline bulk operations** for cleanup
3. **Lua scripts** for atomic multi-key operations
4. **Redis Cluster** support for horizontal scaling
5. **Metrics collection** (crash count, recovery time, etc.)
6. **Configurable TTL** per environment (dev vs prod)

