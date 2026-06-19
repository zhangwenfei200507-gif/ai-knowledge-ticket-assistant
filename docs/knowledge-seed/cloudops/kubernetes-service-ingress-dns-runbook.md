# Kubernetes Service, Ingress, and DNS Runbook

Category: Kubernetes
Primary keywords: kubernetes, service, ingress, dns, endpoint, 502, 503, pod ready, backend unavailable
Source references:
- https://kubernetes.io/docs/concepts/services-networking/service/
- https://kubernetes.io/docs/concepts/services-networking/ingress/
- https://kubernetes.io/docs/tasks/debug/debug-application/debug-service/

Purpose

Use this runbook when a service returns 502 or 503, ingress cannot reach pods, DNS resolution fails, or users can open the frontend but API calls fail.

Troubleshooting order

1. Confirm pods are running and ready.
2. Confirm the Service selector matches pod labels.
3. Confirm Endpoints or EndpointSlices are populated.
4. Confirm Ingress points to the correct service and port.
5. Confirm DNS works inside the cluster.
6. Confirm network policy is not blocking traffic.

Useful commands

```text
kubectl get pods -n <namespace> -o wide
kubectl get svc -n <namespace>
kubectl describe svc <service> -n <namespace>
kubectl get endpoints -n <namespace>
kubectl describe ingress <ingress> -n <namespace>
kubectl logs <ingress-controller-pod> -n <namespace>
```

Common root causes

- Service selector does not match deployed pod labels.
- Pods are running but not ready.
- Ingress references the wrong service port.
- Backend health endpoint fails readiness checks.
- DNS works on the node but not inside pods.
- NetworkPolicy blocks traffic between ingress and backend namespace.

Escalate when

- Public production ingress returns 5xx for many users.
- Service endpoints are empty after deployment.
- Authentication or document upload API cannot be reached through ingress.

