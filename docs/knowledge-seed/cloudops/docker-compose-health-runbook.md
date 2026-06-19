# Docker Compose Service Health Runbook

Category: CloudOps Runtime
Primary keywords: docker, compose, container, healthcheck, restart, network, volume, logs, service unhealthy
Source references:
- https://docs.docker.com/reference/compose-file/
- https://docs.docker.com/reference/compose-file/services/

Purpose

Use this runbook when a local or small production deployment uses Docker Compose and a service is unhealthy, cannot resolve another service by name, loses data after restart, or fails to start in the correct order.

Impact model

Compose problems often show up as backend startup failures, Redis/PostgreSQL connection errors, missing database tables, frontend API failure, or repeated container restart loops.

Fast triage checklist

1. Check container status and health.
2. Check logs for the failing service.
3. Confirm service names match application configuration.
4. Confirm ports are mapped correctly.
5. Confirm volumes are mounted and not accidentally recreated.
6. Check whether healthchecks reflect real readiness, not just process existence.
7. Confirm restart policy is not hiding a crash loop.

Useful commands

```text
docker compose ps
docker compose logs <service>
docker inspect <container>
docker network ls
docker volume ls
docker compose config
```

Common root causes

- Application connects to `localhost` inside a container instead of using the Compose service name.
- Host port and container port are confused.
- A database is running but not ready when the backend starts.
- Healthcheck only checks that a process exists, not that the service can handle real requests.
- A named volume is missing, recreated, or mapped to the wrong path.
- Environment variables in Compose do not match application local config.
- Restart policy keeps restarting a broken container without fixing the cause.

Recovery actions

1. Read the first meaningful error from service logs.
2. Validate the generated Compose configuration.
3. Confirm backend can reach Redis and PostgreSQL using Compose service names.
4. Fix healthchecks so they test real service readiness.
5. Do not delete volumes during incident response unless data loss is acceptable and confirmed.
6. Restart services in dependency order after configuration changes.

Escalate immediately when

- A database or storage volume may have been deleted.
- Production containers restart continuously.
- The service is healthy at container level but failing at application level.
- Authentication, document upload, or ticket creation is unavailable.

Prevention controls

- Use explicit healthchecks for PostgreSQL, Redis, backend, and frontend gateway.
- Keep environment variables documented.
- Separate local development Compose settings from production settings.
- Monitor restart count, health state, and application readiness endpoint.

