# Nexus Server Network - P2P Mode Guide

## Overview

Nexus now supports two modes for multi-server networking:

1. **Redis Mode** (default): Uses Redis for centralized coordination
2. **P2P Mode** (new): Fully decentralized peer-to-peer networking without Redis

## Why P2P Mode?

P2P mode eliminates Redis as a single point of failure and provides:

- **True decentralization**: No central coordination server required
- **Automatic discovery**: Servers find each other via UDP multicast
- **Enhanced reliability**: Players never lose connection during transfers
- **Pre-transfer validation**: Health checks ensure target server is available before transferring
- **Scalability**: Works for 1-10 servers and beyond
- **Resilience**: Automatic crash detection and recovery

## Configuration

### Enabling P2P Mode

Edit your `config.yml`:

```yaml
features:
  servers:
    enable: true
    mode: "p2p"  # Change from "redis" to "p2p"
    serverId: "survival-1"
    serverName: "Survival Server 1"
    serverType: "SURVIVAL"
    host: "192.168.1.100"  # Your server's IP address
    port: 25565
    maxPlayers: 100
    heartbeatIntervalSeconds: 5
    heartbeatTimeoutSeconds: 20

    # P2P specific settings
    p2p:
      apiPort: 8080  # HTTP API port for server-to-server communication
      multicastGroup: "239.255.42.99"  # Multicast group for discovery
      multicastPort: 9999  # Multicast port for discovery
      staticServers: []  # Fallback list of servers (optional)
```

### Network Configuration

#### Automatic Discovery (Multicast)

For automatic server discovery to work, ensure:

1. **Firewall rules**: Allow UDP traffic on multicast port (default: 9999)
2. **Network support**: Your network must support multicast (most LANs do)
3. **Same subnet**: All servers should be on the same network segment

#### Static Server List (Optional)

If multicast doesn't work in your environment, you can manually specify servers:

```yaml
p2p:
  apiPort: 8080
  staticServers:
    - "http://192.168.1.100:8080"
    - "http://192.168.1.101:8080"
    - "http://192.168.1.102:8080"
```

## Firewall Requirements

Open these ports on each server:

- **25565** (or your Minecraft port): For players to connect
- **8080** (or your configured apiPort): For HTTP server-to-server communication
- **9999 UDP** (or your configured multicastPort): For multicast discovery (if using)

Example iptables rules:

```bash
# Minecraft port
iptables -A INPUT -p tcp --dport 25565 -j ACCEPT

# P2P API port
iptables -A INPUT -p tcp --dport 8080 -j ACCEPT

# Multicast discovery
iptables -A INPUT -p udp --dport 9999 -j ACCEPT
```

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

Chat messages are broadcast across all servers in real-time. Works the same in both Redis and P2P modes.

### Server Status API

P2P mode exposes HTTP endpoints for monitoring:

- `GET /health` - Server health and player count
- `GET /server-info` - Server information
- `GET /servers` - List all online servers
- `GET /players` - List players on this server
- `GET /player/{uuid}` - Get specific player information
- `GET /stats` - Network-wide statistics

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

### How P2P Discovery Works

1. **Startup**: Each server broadcasts its presence via UDP multicast
2. **Heartbeat**: Servers send periodic heartbeats (every 10 seconds)
3. **Discovery**: Other servers receive broadcasts and add to their registry
4. **Health Monitoring**: Servers are marked offline if heartbeats stop (30-second timeout)

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

1. Heartbeats stop
2. Other servers detect timeout (30 seconds)
3. Crashed server marked as offline
4. Players on crashed server removed from network registry
5. When server restarts, it re-announces and rejoins network

## Comparison: Redis vs P2P

| Feature | Redis Mode | P2P Mode |
|---------|-----------|----------|
| External Dependencies | Requires Redis | None |
| Discovery | Manual configuration | Automatic (multicast) or static |
| Single Point of Failure | Yes (Redis) | No |
| Network Overhead | Low (central hub) | Medium (peer-to-peer) |
| Setup Complexity | Medium | Low |
| Transfer Health Checks | No | Yes |
| Scalability | Excellent | Good (100+ servers) |

## Troubleshooting

### Servers not discovering each other

1. **Check multicast support**:
   ```bash
   # On Linux
   ip link show | grep MULTICAST
   ```

2. **Test multicast**:
   ```bash
   # Terminal 1 (receiver)
   socat - UDP4-RECV:9999,reuseaddr,ip-add-membership=239.255.42.99:0.0.0.0

   # Terminal 2 (sender)
   echo "test" | socat - UDP4-DATAGRAM:239.255.42.99:9999,broadcast
   ```

3. **Use static server list** as fallback if multicast doesn't work

### HTTP API not responding

1. Check if port is open:
   ```bash
   netstat -tlnp | grep 8080
   ```

2. Test locally:
   ```bash
   curl http://localhost:8080/health
   ```

3. Check firewall rules

### Player transfers failing

Check server logs for specific error messages:

- "Target server is offline" - Server not in registry
- "Server health check failed" - HTTP API not accessible
- "Server is full" - Target server at max capacity
- "Failed to reach server" - Network connectivity issue

## Migrating from Redis to P2P

1. **Backup your data**: Database (PostgreSQL) contains player profiles
2. **Update config**: Change `mode` from "redis" to "p2p"
3. **Configure P2P settings**: Set `host`, `apiPort`, etc.
4. **Open firewall ports**: Allow traffic on API and multicast ports
5. **Restart servers**: All servers in your network
6. **Verify**: Check logs for "Registered server in P2P mode"

## Best Practices

1. **Use static IPs**: Avoid DHCP for server hosts in production
2. **Monitor health endpoints**: Set up external monitoring of `/health`
3. **Keep clocks synced**: Use NTP to ensure consistent timestamps
4. **Plan capacity**: Configure `maxPlayers` appropriately
5. **Test transfers**: Verify transfers work before production use

## Performance Tuning

### For Small Networks (1-10 servers)

Default settings work well. Consider:

```yaml
heartbeatIntervalSeconds: 5  # Check health often
heartbeatTimeoutSeconds: 20  # Quick failure detection
```

### For Larger Networks (10+ servers)

Reduce overhead:

```yaml
heartbeatIntervalSeconds: 10  # Less frequent checks
heartbeatTimeoutSeconds: 30  # More tolerance for network jitter
```

## Support

For issues or questions:

1. Check server logs for error messages
2. Verify network configuration (firewall, multicast)
3. Test with static server list as fallback
4. Report bugs with full logs and network topology

## Future Enhancements

Planned features for P2P mode:

- [ ] Encryption for server-to-server communication (TLS)
- [ ] Authentication tokens for API endpoints
- [ ] Load balancing and player distribution
- [ ] Gossip protocol for large networks (100+ servers)
- [ ] WebSocket support for real-time updates
- [ ] Metrics and monitoring dashboard
