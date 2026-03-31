# Nexus Server Network - P2P Mode Guide

## Overview

Nexus supports peer-to-peer (P2P) networking for multi-server coordination without external dependencies like Redis. P2P mode is the recommended approach for all network sizes and works seamlessly over the internet and in Docker environments.

## Why P2P Mode?

P2P mode provides:

- **True decentralization**: No central coordination server required (no Redis dependency)
- **Zero external dependencies**: Only requires HTTP connectivity between servers
- **Docker & internet compatible**: Works across Docker networks and over the internet
- **Enhanced reliability**: Players never lose connection during transfers
- **Pre-transfer validation**: Health checks ensure target server is available before transferring
- **Universal scalability**: Same configuration works for 1 server or 100+ servers
- **Automatic crash detection**: Servers detect and handle peer failures gracefully

## Configuration

### Enabling P2P Mode

Edit your `config.yml`:

```yaml
features:
  servers:
    enable: true
    mode: "p2p"  # P2P is now the default mode
    serverId: "survival-1"
    serverName: "Survival Server 1"
    serverType: "SURVIVAL"
    host: "192.168.1.100"  # Your server's IP address or hostname
    port: 25565
    maxPlayers: 100
    heartbeatIntervalSeconds: 5
    heartbeatTimeoutSeconds: 20

    # P2P specific settings
    p2p:
      apiPort: 8080  # HTTP API port for server-to-server communication
      staticServers:  # List of initial servers to connect to
        - "http://192.168.1.100:8080"
        - "http://192.168.1.101:8080"
```

### Docker Configuration

For Docker deployments, use service names or host networking:

```yaml
features:
  servers:
    enable: true
    mode: "p2p"
    serverId: "survival-1"
    serverName: "Survival Server 1"
    serverType: "SURVIVAL"
    host: "mc-survival-1"  # Docker service name
    port: 25565

    p2p:
      apiPort: 8080
      staticServers:
        - "http://mc-survival-1:8080"
        - "http://mc-survival-2:8080"
        - "http://mc-creative-1:8080"
```

### Internet/WAN Configuration

For servers across the internet, use public IPs or hostnames:

```yaml
features:
  servers:
    enable: true
    mode: "p2p"
    serverId: "us-east-1"
    serverName: "US East Server"
    serverType: "SURVIVAL"
    host: "us-east.example.com"  # Public hostname
    port: 25565

    p2p:
      apiPort: 8080
      staticServers:
        - "http://us-east.example.com:8080"
        - "http://eu-west.example.com:8080"
        - "http://ap-south.example.com:8080"
```

## Firewall Requirements

Open these ports on each server:

- **25565** (or your Minecraft port): For players to connect
- **8080** (or your configured apiPort): For HTTP server-to-server communication (TCP)

Example iptables rules:

```bash
# Minecraft port
iptables -A INPUT -p tcp --dport 25565 -j ACCEPT

# P2P API port
iptables -A INPUT -p tcp --dport 8080 -j ACCEPT
```

## How P2P Discovery Works

P2P mode uses HTTP-based discovery with a gossip protocol:

1. **Startup**: Each server starts by contacting servers in its `staticServers` list
2. **Initial Discovery**: Server fetches `/server-info` from each static server to learn about them
3. **Gossip Protocol**: Servers poll `/servers` endpoint of known peers to discover their peers
4. **Continuous Polling**: Every 10 seconds, servers poll all known peers to:
   - Verify they're still online
   - Discover new servers they know about
5. **Health Monitoring**: Servers are marked offline if they stop responding

### Discovery Example

```
Server A starts with staticServers: [B]
  → A contacts B at /server-info
  → A learns about B
  → A polls B at /servers
  → A learns B knows about C
  → A contacts C at /server-info
  → A now knows about B and C
  → Network discovery complete!
```

This gossip-based approach means:
- You only need to configure 1-2 static servers
- New servers automatically discover the entire network
- Works across Docker networks, NAT, and the internet
- No multicast or broadcast (which don't work over internet/Docker)

## Features

### Player Transfers

Players can seamlessly transfer between servers with enhanced safety:

```
/server transfer <server-id> [player]
```

P2P mode adds:
- **Health check**: Verifies target server is responding
- **Capacity check**: Ensures target server has room for the player
- **Graceful fallback**: If transfer fails, player stays on current server with notification

### Global Chat

Chat messages are broadcast across all servers in real-time using HTTP POST requests to `/chat-message` endpoint.

### Server Status API

P2P mode exposes HTTP endpoints for monitoring:

- `GET /health` - Server health and player count
- `GET /server-info` - Server information
- `GET /servers` - List all online servers
- `GET /players` - List players on this server
- `GET /player/{uuid}` - Get specific player information
- `GET /stats` - Network-wide statistics
- `POST /chat-message` - Receive global chat messages from other servers

Example:
```bash
curl http://localhost:8080/health
```

Response:
```json
{
  "healthy": true,
  "playerCount": 42,
  "maxPlayers": 100
}
```

## Architecture

### Data Flow

```
Player Transfer Request
        ↓
1. Check target server exists
2. HTTP health check to target
3. Verify player capacity
4. Mark player as TRANSFERRING
5. Initiate Minecraft transfer
6. Target server receives player
7. Target notifies network via P2P
```

### Crash Recovery

If a server crashes:

1. HTTP requests to that server start failing
2. Other servers detect timeouts during polling
3. Crashed server marked as offline
4. Players on crashed server removed from network registry
5. When server restarts, it re-announces and rejoins network via gossip protocol

## Troubleshooting

### Servers not discovering each other

1. **Check static server URLs are correct**:
   ```bash
   curl http://192.168.1.100:8080/server-info
   ```

2. **Verify at least one server is reachable** from the `staticServers` list

3. **Check server logs** for discovery attempts:
   ```
   [INFO] Discovered server 'Survival-2' via HTTP
   ```

4. **Ensure network connectivity** between servers (ping, telnet to port 8080)

### HTTP API not responding

1. Check if port is open:
   ```bash
   netstat -tlnp | grep 8080
   ```

2. Test locally:
   ```bash
   curl http://localhost:8080/health
   ```

3. Check firewall rules allow TCP traffic on apiPort

### Player transfers failing

Check server logs for specific error messages:

- "Target server is offline or not found" - Server not in registry or unreachable
- "Cannot transfer: Server health check failed" - HTTP API not accessible
- "Cannot transfer: Server is at capacity" - Target server is full
- "Failed to reach server" - Network connectivity issue

### Docker-specific issues

1. **Container-to-container communication**: Ensure containers are on the same Docker network or use host networking
2. **Port mapping**: Make sure apiPort is exposed: `-p 8080:8080`
3. **Service names**: Use Docker service names in `staticServers` and `host` fields
4. **DNS resolution**: Verify containers can resolve each other's hostnames

## Migrating from Redis to P2P

1. **Backup your data**: Database (PostgreSQL) contains player profiles
2. **Update config**: Change `mode` from "redis" to "p2p"
3. **Configure P2P settings**:
   - Set `host` to your server's reachable IP/hostname
   - Set `apiPort` (default: 8080)
   - Add at least one other server to `staticServers`
4. **Open firewall ports**: Allow TCP traffic on apiPort
5. **Restart servers**: All servers in your network
6. **Verify**: Check logs for "Registered server in P2P mode"

## Best Practices

1. **Use static IPs or hostnames**: Avoid DHCP for server hosts in production
2. **Configure multiple static servers**: Add 2-3 servers to `staticServers` for redundancy
3. **Monitor health endpoints**: Set up external monitoring of `/health`
4. **Keep clocks synced**: Use NTP to ensure consistent timestamps
5. **Plan capacity**: Configure `maxPlayers` appropriately
6. **Test transfers**: Verify transfers work before production use
7. **Use DNS for internet deployments**: Easier to update IPs without config changes

## Performance Tuning

The same settings work well for all network sizes:

```yaml
heartbeatIntervalSeconds: 5  # Local server player count updates
heartbeatTimeoutSeconds: 20  # TTL for player records
```

P2P discovery polling is fixed at 10-second intervals, which provides good balance between:
- Quick discovery of new servers
- Low network overhead
- Responsive failure detection

## Support

For issues or questions:

1. Check server logs for error messages
2. Verify network configuration (firewall, connectivity between servers)
3. Test HTTP endpoints manually with curl
4. Ensure at least one server in `staticServers` is reachable
5. Report bugs with full logs and network topology

## Future Enhancements

Planned features for P2P mode:

- [ ] Encryption for server-to-server communication (TLS)
- [ ] Authentication tokens for API endpoints
- [ ] Load balancing and player distribution
- [ ] WebSocket support for real-time updates
- [ ] Metrics and monitoring dashboard
