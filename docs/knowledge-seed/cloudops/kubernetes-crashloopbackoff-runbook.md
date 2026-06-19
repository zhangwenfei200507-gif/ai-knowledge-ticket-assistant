# Kubernetes CrashLoopBackOff Runbook

Category: CloudOps Kubernetes
Primary keywords: kubernetes, k8s, pod, crashloopbackoff, logs, events, deployment, restart, image, configmap, secret
Source references:
- https://kubernetes.io/docs/tasks/debug/debug-application/debug-running-pod/
- https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/

Purpose

Use this runbook when a Kubernetes pod repeatedly starts and exits, shows CrashLoopBackOff, or restarts after deployment.

Impact model

CrashLoopBackOff means the container repeatedly fails and Kubernetes delays restarts with backoff. The visible symptom may be service outage, missing pods behind a Service, failed login, unavailable API, or failed background processing.

Fast triage checklist

1. Identify namespace, workload, pod name, and latest deployment version.
2. Describe the pod and read events.
3. Check current logs and previous container logs.
4. Inspect environment variables, ConfigMaps, Secrets, mounted volumes, and command arguments.
5. Check image pull and startup command.
6. Check probes. A bad liveness probe can restart a healthy but slow-starting application.
7. Check resource limits and OOMKilled events.

Useful commands

```text
kubectl get pods -n <namespace>
kubectl describe pod <pod> -n <namespace>
kubectl logs <pod> -n <namespace>
kubectl logs <pod> -n <namespace> --previous
kubectl get events -n <namespace> --sort-by=.lastTimestamp
kubectl describe deployment <deployment> -n <namespace>
```

Common root causes

- Application throws an exception during startup.
- Required environment variable is missing or wrong.
- Secret or ConfigMap was renamed but deployment still references the old key.
- Container command exits immediately.
- Database, Redis, or external dependency is unreachable at startup.
- Liveness probe starts too early and kills the application.
- Memory limit is too low and the container is OOMKilled.
- New image tag contains a breaking change.

Recovery actions

1. Roll back to the last known good deployment if user impact is high.
2. Capture previous logs before restarting or deleting pods.
3. Fix configuration, secrets, or probe timing.
4. If OOMKilled, raise memory limits or reduce application memory pressure.
5. If dependency startup is slow, use startup probes and readiness probes rather than aggressive liveness checks.
6. Confirm the Service has ready endpoints after recovery.

Escalate immediately when

- Multiple production pods enter CrashLoopBackOff after a deployment.
- Authentication, database access, or ticket creation is unavailable.
- Logs indicate data migration failure or possible data loss.
- Rollback fails.

Prevention controls

- Keep deployment change history.
- Use readiness probes for traffic gating and startup probes for slow initialization.
- Avoid making liveness probes depend on optional downstream services.
- Keep resource requests and limits realistic.
- Add deployment smoke tests after release.

