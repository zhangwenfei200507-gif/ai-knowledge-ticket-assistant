# PostgreSQL Connection and Timeout Runbook

Category: PostgreSQL
Primary keywords: postgresql, postgres, database, connection timeout, max_connections, statement_timeout, idle transaction, lock, slow query
Source references:
- https://www.postgresql.org/docs/current/runtime-config-connection.html
- https://www.postgresql.org/docs/current/runtime-config-client.html

Purpose

Use this runbook when the application cannot connect to PostgreSQL, queries time out, the connection pool is exhausted, migrations hang, or users report slow actions that depend on database reads or writes.

Impact model

PostgreSQL failures can appear as login failure, document upload failure, ticket creation failure, empty admin dashboard data, slow AI log retrieval, or backend startup failure.

Fast triage checklist

1. Confirm the database container or server is running.
2. Confirm host, port, database name, username, and password.
3. Check whether the application pool is exhausted.
4. Check active sessions and locks.
5. Check whether a query is slow or waiting on another transaction.
6. Confirm `max_connections` is sufficient for the application and admin tools.
7. Check timeout settings before assuming the database is down.

Useful SQL checks

```sql
select now();

select count(*) as active_connections
from pg_stat_activity;

select pid, usename, application_name, state, wait_event_type, wait_event, query
from pg_stat_activity
order by state, query_start nulls last;

select pid, state, now() - query_start as running_for, query
from pg_stat_activity
where state <> 'idle'
order by running_for desc;
```

Timeout settings to inspect

```sql
show max_connections;
show statement_timeout;
show idle_in_transaction_session_timeout;
show lock_timeout;
```

Common root causes

- PostgreSQL container is stopped or not healthy.
- Application uses the wrong mapped port. Docker host port and container port may differ.
- Connection pool size is too high for `max_connections`.
- Long-running queries block normal requests.
- An idle transaction holds locks and blocks updates.
- Migrations are running while application traffic continues.
- Network or DNS configuration changed.
- Credentials changed but application configuration was not updated.

Recovery actions

1. Restore database availability and verify direct connection.
2. If connections are exhausted, identify which application owns them before killing sessions.
3. If a transaction is blocking critical work, capture the query and owner before terminating it.
4. If timeout settings are too aggressive, adjust at application or role level rather than globally unless the policy is deliberate.
5. After recovery, verify login, document upload, AI question log insertion, and admin dashboard loading.

Escalate immediately when

- Production writes fail.
- Authentication or admin operations cannot read user records.
- Migrations are blocked and deployment cannot proceed.
- There is possible data corruption or disk exhaustion.

Prevention controls

- Keep connection pool size below database capacity.
- Add timeout policies for long-running statements and idle transactions.
- Monitor connection count, locks, slow queries, deadlocks, and disk usage.
- Keep backup and restore procedures tested before production incidents.
