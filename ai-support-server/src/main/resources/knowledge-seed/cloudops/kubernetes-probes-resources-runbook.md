# Kubernetes Probes and Resource Pressure Runbook

Category: Kubernetes
Primary keywords: kubernetes, probes, liveness, readiness, startup, resource requests, limits, eviction, oomkilled, node pressure
Source references:
- https://kubernetes.io/docs/concepts/workloads/pods/probes/
- https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/
- https://kubernetes.io/docs/concepts/scheduling-eviction/node-pressure-eviction/

Purpose

Use this runbook when traffic is sent to unready pods, pods restart unexpectedly, pods are evicted, or an application behaves differently after resource limits change.

Probe model

Readiness controls whether a pod receives traffic. A pod can be alive but not ready.

Liveness controls whether Kubernetes restarts a container. Use it for deadlocked or unrecoverable states.

Startup protects slow-starting applications from liveness checks until initialization completes.

Resource model

Requests influence scheduling. Limits constrain maximum usage. If a container exceeds memory limit, it can be killed. Under node pressure, pods may be evicted to reclaim resources.

Fast triage checklist

1. Check pod events for probe failures, OOMKilled, or eviction.
2. Compare current probe timings with actual application startup time.
3. Confirm readiness endpoint checks only required readiness, not every optional dependency.
4. Confirm liveness endpoint does not fail during temporary downstream outages.
5. Check memory and CPU requests/limits.
6. Check node pressure events.

Useful commands

```text
kubectl describe pod <pod> -n <namespace>
kubectl top pod -n <namespace>
kubectl top node
kubectl get events -n <namespace> --sort-by=.lastTimestamp
kubectl describe node <node>
```

Common root causes

- Liveness probe is too aggressive and restarts the app before it recovers.
- Startup probe is missing for slow JVM or migration-heavy startup.
- Readiness probe includes optional dependencies and removes pods from service unnecessarily.
- Memory limit is below real workload needs.
- Requests are too low, causing dense scheduling and node pressure.
- Disk pressure or inode pressure causes pod eviction.

Recovery actions

1. If users are impacted, route traffic away from unhealthy pods or roll back.
2. Adjust startup probe failure threshold for slow startup.
3. Make readiness reflect traffic-serving ability.
4. Make liveness detect only unrecoverable application states.
5. Tune memory limits based on observed peak usage.
6. Investigate node pressure before simply restarting pods.

Escalate immediately when

- Many pods are evicted on the same node.
- The same workload repeatedly OOMKills after normal traffic.
- Probe configuration causes production restart loops.
- Node disk or memory pressure threatens multiple services.

Prevention controls

- Record baseline startup time and peak memory.
- Review probes during every deployment change.
- Monitor restarts, readiness failures, OOMKilled events, and evictions.
- Keep dashboards separated for application health and infrastructure health.
