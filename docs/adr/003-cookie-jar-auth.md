# 003. Cookie-jar authentication

Status: accepted

Context: `POST /users/login` returns a `User` and sets an httpOnly `auth_token`
cookie (30 days). The server also accepts `Authorization: Bearer <jwt>`, but it does
not issue the token in the login response. The v1 app must work without server changes.

Decision: use OkHttp's `CookieJar` with app-private persistence. Probe the session with
`GET /users/me` on launch. Treat 401 as a global session-expired event. Do not add a
token endpoint for v1.

Consequences:

- Zero server changes and correct handling of the existing httpOnly cookie.
- No refresh token, token introspection, or multi-account session switching. Re-login
  after expiry is expected.
- Cookies must never be logged. Cookie storage is cleared when the server URL changes.
- Cookies are domain-scoped; redirects to another host should not leak them.
- If multi-account or long-lived mobile sessions become requirements, add a dedicated
  mobile token/refresh flow and supersede this ADR.
