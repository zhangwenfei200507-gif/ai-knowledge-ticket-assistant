# Auth / JWT Runbook for Spring Security APIs

Category: CloudOps Auth
Primary keywords: login, jwt, bearer token, authorization, 401, 403, refresh token, session, Spring Security
Source references:
- https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/index.html
- https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html

Purpose

Use this runbook when users report that they cannot sign in, API calls return 401 or 403, a token is rejected, an admin page does not open after login, or a frontend request works in one browser but fails in another.

Decision model

401 means the request is not authenticated. The token may be missing, expired, malformed, signed by the wrong key, or not being sent by the frontend.

403 means the request is authenticated but not authorized. The token is usually valid, but the user role or permission does not satisfy the endpoint rule.

Fast triage checklist

1. Confirm whether the failing endpoint requires authentication or ADMIN access.
2. Check the browser network request and verify the Authorization header is present.
3. Confirm the header format is exactly `Authorization: Bearer <token>`.
4. Confirm the user exists in the `users` table and has `status = 1`.
5. Confirm the expected role is present. Admin-only endpoints must use an ADMIN user.
6. Check backend logs for JWT parsing or Spring Security denial messages.
7. If Redis stores sessions or refresh metadata in the deployment, check Redis health before assuming an application bug.

Backend checks

Use this when the request returns 401:

```text
Check whether the frontend saved the token.
Check whether the frontend sends the token on every protected request.
Check token expiration time.
Check whether backend secret or signing key changed after token creation.
Check whether the JWT parser accepts the token algorithm.
```

Use this when the request returns 403:

```text
Check the endpoint security rule.
Check the user role in database.
Check whether the backend maps the role as ADMIN or ROLE_ADMIN.
Check whether the current token was issued before the role was changed.
Ask the user to sign in again after role updates.
```

Common root causes

- The frontend request forgot the Authorization header after page refresh.
- The user logged in before being promoted to ADMIN, so the stored token still contains the old role.
- The wrong account is used. Two email accounts can look similar but carry different roles.
- The backend route is correctly protected, but the frontend lets a non-admin open the admin route shell.
- Password reset succeeded, but the user still uses an old saved password.
- Token signing configuration changed during a redeploy and old tokens became invalid.

Recommended response for users

If a normal user cannot open the workspace, ask them to sign out and sign in again, then capture the exact HTTP status from the failing request.

If an admin cannot open the operations center, verify the account role in the database, then have the admin sign in again to refresh the token.

Escalate immediately when

- Multiple users suddenly receive 401 across unrelated endpoints.
- All admin endpoints return 403 for known admin users.
- Login succeeds but all authenticated requests fail.
- Token validation started failing after deployment, secret rotation, or gateway changes.

Resolution notes

For a role change, the clean fix is to update the role in the database, require a fresh login, and verify the new token is used. Do not bypass endpoint authorization in the frontend. The backend must remain the source of truth for protected operations.

