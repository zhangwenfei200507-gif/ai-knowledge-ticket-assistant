# Redis Session and Token Storage SOP

Category: Redis Cache
Primary keywords: redis, token storage, session cache, login unavailable, ttl, expired key, memory pressure, eviction
Source references:
- https://redis.io/docs/latest/commands/ttl/
- https://redis.io/docs/latest/develop/reference/eviction/
- https://redis.io/docs/latest/operate/oss_and_stack/management/optimization/latency/

Purpose

Use this SOP when login depends on Redis, tokens disappear, user sessions expire too early, or Redis memory pressure causes authentication instability.

Key checks

1. Check Redis connectivity with `PING`.
2. Check whether login tokens or session keys have TTL values.
3. Check whether keys are being evicted because memory is full.
4. Check Redis latency and slow commands.
5. Check application logs for Redis timeout or connection refused errors.

Useful commands

```text
redis-cli PING
redis-cli INFO memory
redis-cli INFO stats
redis-cli TTL <token-key>
redis-cli SLOWLOG GET 20
redis-cli CONFIG GET maxmemory
redis-cli CONFIG GET maxmemory-policy
```

Expected behavior

Session or token keys should have predictable expiration. Permanent keys should not be used for short-lived login state. If the system uses Redis to track logout or active tokens, Redis unavailability can make login validation unstable.

Common root causes

- Token keys expire earlier than the frontend expects.
- Redis eviction removes active token records.
- Redis is healthy locally but unreachable from the backend container network.
- The backend points to the wrong Redis host, port, password, or database index.
- Redis latency causes authentication checks to time out.

Recovery actions

1. Restore Redis connectivity.
2. Verify token key TTL policy.
3. If memory is full, remove safe temporary keys or increase memory.
4. Review eviction policy before production traffic resumes.
5. Ask affected users to sign in again after Redis recovery.

Escalate when

- Authentication depends on Redis and Redis is unavailable.
- Evicted token/session keys affect many users.
- Redis memory usage repeatedly reaches the configured limit.

