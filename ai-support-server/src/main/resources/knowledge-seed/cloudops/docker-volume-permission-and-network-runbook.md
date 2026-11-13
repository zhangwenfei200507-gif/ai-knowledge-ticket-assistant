# Docker Volume Permission and Network Runbook

Category: Docker
Primary keywords: docker, compose, volume permission, bind mount, network, container dns, service name, port mapping
Source references:
- https://docs.docker.com/engine/storage/volumes/
- https://docs.docker.com/compose/how-tos/networking/
- https://docs.docker.com/reference/compose-file/services/

Purpose

Use this runbook when containers cannot read mounted files, PostgreSQL data disappears, Redis cannot be reached, or the backend connects to the wrong host.

Network model

Inside Docker Compose, containers should usually connect to other services by service name, not `localhost`. `localhost` inside a container refers to that container itself.

Volume model

Named volumes persist container data. Bind mounts expose host paths. Wrong paths or permissions can make containers start but fail at runtime.

Checklist

1. Run `docker compose ps`.
2. Inspect service logs.
3. Confirm service names in application configuration.
4. Confirm host port and container port are not confused.
5. Confirm named volumes exist.
6. Confirm bind-mounted files are readable by the container process.

Useful commands

```text
docker compose config
docker compose ps
docker compose logs <service>
docker inspect <container>
docker volume ls
docker network inspect <network>
```

Common root causes

- Backend container uses `localhost:5432` instead of the PostgreSQL service name.
- Host maps PostgreSQL to 5433, but containers still need 5432 internally.
- Bind-mounted files are created with unexpected permissions.
- A named volume is deleted during cleanup.
- Two Compose projects create similarly named networks.

Escalate when

- A database volume may have been deleted.
- Production containers restart because mounted configuration is unreadable.
- A network change breaks backend access to Redis or PostgreSQL.

