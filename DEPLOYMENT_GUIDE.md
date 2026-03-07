# Deployment Guide - Redis Network System Updates

## Overview

This deployment includes critical improvements to the Redis-based network system:
- **Automatic crash detection** via Redis keyspace notifications
- **TTL management** for all data structures
- **Bug fixes** for server listing
- **No stale data** guarantees

## Pre-Deployment Checklist

### 1. Redis Configuration

**Required:** Redis must support keyspace notifications.

**Check current config:**
```bash
redis-cli CONFIG GET notify-keyspace-events
```

**If needed, enable notifications:**
```bash
redis-cli CONFIG SET notify-keyspace-events Ex
```

**Make permanent (redis.conf):**
```conf
notify-keyspace-events Ex
```

### 2. Backup Current State

```bash
# Backup Redis data
redis-cli SAVE

# Backup server configurations
cp config.yml config.yml.backup
```

### 3. Review Configuration

Check `heartbeatIntervalSeconds` and `heartbeatTimeoutSeconds`:
```yaml
features:
  servers:
    heartbeatIntervalSeconds: 30   # Recommended: 30
    heartbeatTimeoutSeconds: 60    # Recommended: 60
```

## Deployment Steps

### Option A: Zero-Downtime Rolling Deployment

**Best for:** Production environments with multiple servers

```bash
# 1. Update code on all servers (don't restart yet)
git pull
./gradlew build

# 2. Verify Redis is configured
redis-cli CONFIG GET notify-keyspace-events
# Should show: "Ex" or "AEx"

# 3. Restart servers one at a time
# Server 1
./stop-server.sh survival-1
./start-server.sh survival-1
# Wait 2 minutes, verify it's stable

# Server 2
./stop-server.sh lobby-1
./start-server.sh lobby-1
# Wait 2 minutes

# Continue for all servers...
```

### Option B: Maintenance Window Deployment

**Best for:** Small networks or development

```bash
# 1. Announce maintenance
# Players will be kicked when servers restart

# 2. Stop all servers
./stop-all-servers.sh

# 3. Update code
git pull
./gradlew build

# 4. Clear Redis (optional, but recommended for clean start)
redis-cli FLUSHDB

# 5. Verify Redis configuration
redis-cli CONFIG SET notify-keyspace-events Ex

# 6. Start all servers
./start-all-servers.sh

# 7. Verify all servers registered
redis-cli KEYS nexus:server:info:*
```

## Post-Deployment Verification

### 1. Check Server Registration

```bash
# List all registered servers
redis-cli KEYS nexus:server:info:*

# Check specific server
redis-cli GET nexus:server:info:survival-1

# Verify TTL is set
redis-cli TTL nexus:server:info:survival-1
# Should return ~60 (or your configured TTL)
```

### 2. Check Keyspace Notifications

```bash
# Monitor keyspace events (in separate terminal)
redis-cli PSUBSCRIBE '__keyevent@0__:expired'

# Let it run, you should see events as keys expire
```

### 3. Check Server Logs

Look for these messages on each server:

```
✓ Redis Server: redis_version:7.0.0
✓ Redis Configuration:
  notify-keyspace-events: 'Ex'
  Keyevent notifications (E): ✓ Enabled
  Expired events (x): ✓ Enabled
  ✓ Nexus network ready for automatic crash detection
✓ Registered server 'Survival Server' (survival-1) as online
```

### 4. Test Player Tracking

```bash
# Join server with a player

# Check player is tracked
redis-cli GET nexus:player:info:{uuid}

# Check player is in server set
redis-cli SMEMBERS nexus:server:players:survival-1

# Check player is in global online set
redis-cli SISMEMBER nexus:players:online {uuid}

# Verify TTL is set
redis-cli TTL nexus:player:info:{uuid}
```

### 5. Test Crash Detection

**WARNING:** This will kick players from the test server.

```bash
# 1. Identify test server process
ps aux | grep survival-1

# 2. Kill process (simulate crash)
kill -9 <pid>

# 3. Monitor Redis
watch -n 1 'redis-cli GET nexus:server:info:survival-1'

# 4. After 60 seconds, key should disappear
# Check other server logs for offline event:
# "Server 'survival-1' went offline"

# 5. Verify players cleaned up
redis-cli SMEMBERS nexus:server:players:survival-1
# Should be empty or key deleted

# 6. Restart crashed server
./start-server.sh survival-1

# 7. Verify re-registration
redis-cli GET nexus:server:info:survival-1
```

## Rollback Plan

If issues are detected:

### Quick Rollback

```bash
# 1. Stop all servers
./stop-all-servers.sh

# 2. Restore previous version
git checkout <previous-commit>
./gradlew build

# 3. Clear Redis
redis-cli FLUSHDB

# 4. Start servers
./start-all-servers.sh
```

### Data Preservation Rollback

```bash
# 1. Stop servers one at a time (rolling)

# 2. Restore previous version
git checkout <previous-commit>
./gradlew build

# 3. Restart servers one at a time
# Redis data will remain, servers will re-register
```

## Monitoring

### Health Checks

Add these to your monitoring system:

```bash
# 1. Check server count
EXPECTED_SERVERS=5
ACTUAL=$(redis-cli KEYS nexus:server:info:* | wc -l)
if [ $ACTUAL -lt $EXPECTED_SERVERS ]; then
  echo "WARNING: Only $ACTUAL of $EXPECTED_SERVERS servers online"
fi

# 2. Check keyspace notifications
CONFIG=$(redis-cli CONFIG GET notify-keyspace-events | grep -o "Ex")
if [ -z "$CONFIG" ]; then
  echo "ERROR: Keyspace notifications not enabled"
fi

# 3. Check TTLs
for key in $(redis-cli KEYS nexus:server:info:*); do
  TTL=$(redis-cli TTL $key)
  if [ $TTL -lt 0 ]; then
    echo "WARNING: $key has no TTL"
  fi
done

# 4. Check player data consistency
ONLINE_COUNT=$(redis-cli SCARD nexus:players:online)
PLAYER_KEYS=$(redis-cli KEYS nexus:player:info:* | wc -l)
DIFF=$((ONLINE_COUNT - PLAYER_KEYS))
if [ $DIFF -gt 10 ]; then
  echo "WARNING: Player data inconsistency detected"
fi
```

### Metrics to Track

Set up dashboards for:
- Number of online servers
- Total online players
- Player distribution per server
- Server crash events per hour
- Average crash detection time
- Redis memory usage
- Redis operation rate

### Alerting

Configure alerts for:
- Server offline events
- Redis connection failures
- Keyspace notifications disabled
- Keys without TTL
- Player count anomalies

## Troubleshooting

### Problem: "Keyspace notifications not working"

**Symptoms:**
- Crashed servers not detected
- Players remain online after server crash

**Solution:**
```bash
# Check config
redis-cli CONFIG GET notify-keyspace-events

# Enable if needed
redis-cli CONFIG SET notify-keyspace-events Ex

# Make permanent in redis.conf
echo "notify-keyspace-events Ex" >> /etc/redis/redis.conf
systemctl restart redis
```

### Problem: "Server shows offline but is running"

**Symptoms:**
- Server running normally
- Not in `getOnlineServers()` list

**Check:**
```bash
# 1. Check Redis connection
# In server logs, look for "Failed to register server"

# 2. Check TTL is being refreshed
redis-cli TTL nexus:server:info:server-1
# Should be 30-60, if -1 or -2, heartbeat not working

# 3. Check heartbeat logs
# Should see heartbeat every 30 seconds
```

**Solution:**
```bash
# Restart server
./restart-server.sh server-1
```

### Problem: "High memory usage in Redis"

**Symptoms:**
- Redis memory growing
- Not enough memory errors

**Check:**
```bash
# Find keys without TTL
redis-cli --scan --pattern 'nexus:*' | while read key; do
  TTL=$(redis-cli TTL $key)
  if [ $TTL -eq -1 ]; then
    echo "$key has no TTL"
  fi
done
```

**Solution:**
```bash
# Manual cleanup
redis-cli DEL <key-without-ttl>

# Or full cleanup (will kick all players)
redis-cli FLUSHDB
./restart-all-servers.sh
```

### Problem: "Players stuck online after quit"

**Check:**
```bash
# Get player info
redis-cli GET nexus:player:info:{uuid}

# Check TTL
redis-cli TTL nexus:player:info:{uuid}
```

**Solution:**
```bash
# Manual cleanup
redis-cli DEL nexus:player:info:{uuid}
redis-cli SREM nexus:players:online {uuid}
redis-cli SREM nexus:server:players:server-1 {uuid}

# Or wait for TTL to expire (max 60 seconds)
```

## Performance Tuning

### For High Player Count (>1000 players)

Consider adjusting heartbeat intervals:

```yaml
features:
  servers:
    heartbeatIntervalSeconds: 60   # Less frequent
    heartbeatTimeoutSeconds: 120   # Longer grace period
```

**Trade-offs:**
- Less Redis load
- Slower crash detection (60-120s instead of 30-60s)
- Lower memory churn

### For Critical Reliability (fast crash detection)

```yaml
features:
  servers:
    heartbeatIntervalSeconds: 15   # More frequent
    heartbeatTimeoutSeconds: 30    # Shorter timeout
```

**Trade-offs:**
- Faster crash detection (15-30s)
- More Redis operations
- Higher memory churn
- Better for production environments

### For Development

```yaml
features:
  servers:
    heartbeatIntervalSeconds: 5    # Very frequent
    heartbeatTimeoutSeconds: 10    # Very short
```

**Trade-offs:**
- Instant crash detection (5-10s)
- High Redis load (not for production)
- Good for testing

## Success Criteria

Deployment is successful when:

- ✅ All servers show in `redis-cli KEYS nexus:server:info:*`
- ✅ All server keys have TTL: `redis-cli TTL nexus:server:info:*`
- ✅ Keyspace notifications enabled: `redis-cli CONFIG GET notify-keyspace-events` returns "Ex"
- ✅ Server logs show configuration validation success
- ✅ Test crash detection works (60s max)
- ✅ Players tracked correctly on join/quit
- ✅ No errors in server logs
- ✅ Redis memory usage stable

## Support

If issues persist after following this guide:

1. Check server logs: `/logs/latest.log`
2. Check Redis logs: `/var/log/redis/redis-server.log`
3. Run validation: `RedisConfig.validateConfiguration()`
4. Review documentation:
   - `REDIS_IMPLEMENTATION.md` - Technical details
   - `REDIS_QUICK_REFERENCE.md` - Commands and API
   - `CHANGES_SUMMARY.md` - What changed

## Maintenance

### Weekly

```bash
# Check for keys without TTL
redis-cli --scan --pattern 'nexus:*' | while read key; do
  TTL=$(redis-cli TTL $key)
  if [ $TTL -eq -1 ]; then
    echo "$key has no TTL - investigate"
  fi
done
```

### Monthly

```bash
# Review Redis memory usage
redis-cli INFO memory

# Review keyspace statistics
redis-cli INFO keyspace

# Review server uptimes and crash events
# Check monitoring dashboards
```

### After Each Crash

```bash
# Verify cleanup happened
# Check logs for "went offline" message
# Verify players were cleaned up
# Review crash cause

# If cleanup didn't work:
# 1. Check keyspace notifications still enabled
# 2. Restart remaining servers to re-establish subscriptions
```

## Conclusion

This deployment adds critical reliability improvements to the network:
- **Automatic crash detection**
- **No stale data**
- **Redis best practices**

Follow this guide for smooth deployment and minimal downtime.

