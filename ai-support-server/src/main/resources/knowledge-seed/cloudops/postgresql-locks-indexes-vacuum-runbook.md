# PostgreSQL Locks, Indexes, and Vacuum Runbook

Category: PostgreSQL
Primary keywords: postgres, postgresql, lock, blocked query, index, slow query, vacuum, dead tuple, bloat
Source references:
- https://www.postgresql.org/docs/current/monitoring-stats.html
- https://www.postgresql.org/docs/current/explicit-locking.html
- https://www.postgresql.org/docs/current/routine-vacuuming.html

Purpose

Use this runbook when the application is slow, database writes hang, a migration appears stuck, or dashboard queries become delayed.

Fast triage

1. Check active queries and wait events.
2. Identify blocking sessions.
3. Check whether indexes exist for high-frequency filters.
4. Check whether dead tuples or table bloat may require vacuum.
5. Capture the query before terminating sessions.

Useful SQL

```sql
select pid, state, wait_event_type, wait_event, now() - query_start as age, query
from pg_stat_activity
where state <> 'idle'
order by age desc;

select blocked.pid as blocked_pid,
       blocking.pid as blocking_pid,
       blocked.query as blocked_query,
       blocking.query as blocking_query
from pg_stat_activity blocked
join pg_locks blocked_locks on blocked_locks.pid = blocked.pid
join pg_locks blocking_locks
  on blocking_locks.locktype = blocked_locks.locktype
 and blocking_locks.database is not distinct from blocked_locks.database
 and blocking_locks.relation is not distinct from blocked_locks.relation
 and blocking_locks.page is not distinct from blocked_locks.page
 and blocking_locks.tuple is not distinct from blocked_locks.tuple
 and blocking_locks.pid <> blocked_locks.pid
join pg_stat_activity blocking on blocking.pid = blocking_locks.pid
where not blocked_locks.granted and blocking_locks.granted;
```

Common root causes

- Long idle transaction holds locks.
- Migration changes a busy table during traffic.
- Missing index causes full table scans.
- Vacuum cannot keep up with frequent updates or deletes.
- Connection pool sends too many concurrent queries.

Safe recovery

Terminate a blocker only after capturing its query, user, and impact. For slow queries, prefer adding indexes or rewriting query patterns instead of increasing timeouts blindly.

Escalate when

- Writes are blocked in production.
- Migration is stuck and deployment is blocked.
- Disk usage grows quickly due to bloat.
- Database response time affects login, document upload, or ticket creation.

