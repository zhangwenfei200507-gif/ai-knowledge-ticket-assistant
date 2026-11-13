# JWT Token Refresh and Logout SOP

Category: Auth / JWT
Primary keywords: jwt, token refresh, logout, bearer token, 401, 403, expired token, role change, admin access
Source references:
- https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html
- https://docs.spring.io/spring-security/reference/servlet/authorization/authorize-http-requests.html

Purpose

Use this SOP when a user can sign in but later receives 401, when an administrator role change does not take effect, or when logout does not remove access from the current browser session.

Core behavior

Access tokens are bearer credentials. If a token is valid and accepted by the backend, the request is authenticated. If the token is missing, expired, malformed, or signed with the wrong key, the backend should return 401.

Role and permission problems should return 403. A valid USER token must not access ADMIN endpoints. If a user is promoted to ADMIN, the user should sign in again so the frontend stores a fresh token with the updated role.

Troubleshooting checklist

1. Confirm the browser request includes `Authorization: Bearer <token>`.
2. Confirm the token belongs to the user currently shown in the UI.
3. Confirm the account has `status = 1`.
4. Confirm the role in the token matches the current database role.
5. Confirm the backend security rules protect admin APIs.
6. Sign out and sign in again after role changes.
7. Clear local browser token state if logout appears incomplete.

Common failures

- The frontend keeps an old token after role change.
- A protected request is sent without the Authorization header.
- A user account is active in the database but the token was issued before the latest role update.
- Logout clears only UI state but not the stored browser token.
- The frontend route is visible, but backend APIs correctly reject the user.

Safe fix

Never fix an authorization issue by hiding buttons only. UI checks are useful, but backend authorization is the source of truth. The correct fix is usually token refresh, role verification, and endpoint rule correction.

Escalate when

- Multiple users suddenly receive 401 after deployment.
- Admin accounts receive 403 even after a fresh sign in.
- A normal user can access admin APIs.
- Token signing configuration changed during deployment.

