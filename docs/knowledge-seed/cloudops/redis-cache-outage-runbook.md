# Redis Cache Outage and Latency Runbook

Category: CloudOps Cache
Primary keywords: redis, cache, token, session, latency, memory, eviction, maxmemory, persistence, slowlog
Source references:
- https://redis.io/docs/latest/operate/oss_and_stack/management/optimization/latency/
- https://redis.io/docs/latest/develop/reference/eviction/
- https://redis.io/docs/latest/operate/oss_and_stack/management/persistence/

Purpose

Use this runbook when production cache is down, login is slow, token/session validation fails, Redis latency spikes, keys disappear, or Redis reports memory pressure.

Impact model

Redis failures often appear as login problems, slow API responses, rate-limit errors, missing session state, background job delays, or repeated retries from services that depend on cached data.

Fast triage checklist

1. Confirm whether Redis is reachable from the application host or container network.
2. Check whether Redis is healthy, restarting, or out of memory.
3. Check application logs for connection refused, timeout, authentication failure, or MOVED/ASK cluster errors.
4. Check Redis memory usage and configured eviction policy.
5. Check slow commands and blocking operations.
6. Confirm whether persistence settings are creating heavy disk I/O.
7. Identify whether login/session/token storage depends on Redis.

Operational checks

```text
redis-cli PING
redis-cli INFO memory
redis-cli INFO stats
redis-cli INFO persistence
redis-cli SLOWLOG GET 20
redis-cli CONFIG GET maxmemory
redis-cli CONFIG GET maxmemory-policy
```

If running in Docker, also check:

```text
docker ps
docker logs <redis-container>
docker inspect <redis-container>
```

Common root causes

- Redis container is stopped or restarting.
- Application points to the wrong host or port.
- Redis password changed but application configuration was not updated.
- Memory exceeded `maxmemory`; eviction removed cache entries needed by the application.
- Eviction policy is `noeviction`, so write commands fail when memory is full.
- Slow Lua scripts or large commands block the single Redis event loop.
- RDB/AOF persistence or disk pressure causes latency spikes.
- Host memory pressure causes Redis pages to swap, which can create severe latency.

Recovery actions

1. Restore Redis process or container health.
2. Verify application connectivity with the same host, port, password, and database index used by the service.
3. If memory is full, remove unnecessary keys, raise memory safely, or change eviction policy according to business requirements.
4. If session or token cache is corrupted, force affected users to sign in again after Redis recovers.
5. If slow commands are present, identify the caller and reduce expensive key scans, large payloads, or blocking Lua scripts.
6. If persistence is causing latency, review AOF/RDB settings and disk health.

Safe communication to users

Tell users that cached authentication or frequently accessed support data may be temporarily unavailable. Ask them not to repeatedly retry login during a known cache incident because retries can amplify load.

Escalate immediately when

- Redis is unavailable and login depends on Redis.
- Redis is repeatedly killed by memory pressure.
- The cache outage affects production authentication, ticket creation, or AI answer retrieval.
- Data that should be durable was stored only in Redis.

Prevention controls

- Monitor latency, memory usage, rejected connections, evicted keys, and command duration.
- Set `maxmemory` deliberately and choose an eviction policy that matches the workload.
- Do not use Redis as the only durable source for critical records.
- Keep session/token behavior documented so support can explain user impact during outages.

