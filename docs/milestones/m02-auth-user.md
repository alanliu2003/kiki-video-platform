# Milestone 2 — Authentication & User Foundation

## Goal

Add the first real application domain: users and authentication. A person can register, log in, receive a JWT access token, call `GET /api/users/me`, and keep a usable authenticated session in the Vue app across page refreshes.

## Scope

- PostgreSQL user persistence through Flyway + MyBatis
- Registration and login APIs
- BCrypt password hashing
- Stateless JWT access-token authentication
- Spring Security protection for `/api/users/me`
- Vue register/login/profile flow with Pinia auth state
- Backend integration tests against PostgreSQL via Testcontainers
- Lightweight frontend Vitest coverage for the auth store and route guard

## Non-goals

- Video metadata, uploads, MinIO, FFmpeg, HLS
- Comments, likes, favorites, follows, danmaku
- WebSockets, RocketMQ, Elasticsearch
- Microservices, Spring Cloud Gateway, Eureka
- OAuth / social login, email verification, password reset, 2FA
- Admin dashboard
- Refresh tokens
- Redis-backed sessions

## Architecture changes

```text
Vue 3
 │
 │ Axios + JWT (Authorization: Bearer)
 ▼
Spring Boot API
 │
 ├── Spring Security (stateless)
 ├── Auth domain
 └── User domain
        │
        ▼
   MyBatis
        │
        ▼
   PostgreSQL
```

MinIO and Redis still run in Docker Compose for later milestones. Application code does not use them.

Persistence choice: **plain MyBatis**. The repository had no JPA/Hibernate layer, and explicit SQL keeps insert, uniqueness, and mapping easy to read.

Refresh-token choice: **access token only**, one hour TTL. A refresh-token design needs stored revocation state to be honest. That is deferred rather than faked.

## Database schema

Flyway migration: `backend/api/src/main/resources/db/migration/V1__create_users.sql`

```text
users
-----
id              BIGSERIAL PRIMARY KEY
username        VARCHAR(30) UNIQUE NOT NULL
email           VARCHAR(255) UNIQUE NOT NULL
password_hash   VARCHAR(255) NOT NULL
display_name    VARCHAR(30) NOT NULL
role            VARCHAR(20) NOT NULL   -- USER | ADMIN
status          VARCHAR(20) NOT NULL   -- ACTIVE | DISABLED
created_at      TIMESTAMPTZ NOT NULL
updated_at      TIMESTAMPTZ NOT NULL
```

Registration always creates `role=USER` and `status=ACTIVE`.

Usernames and emails are normalized to lowercase before insert. Display name keeps the trimmed original username casing.

Uniqueness is enforced by PostgreSQL `UNIQUE` constraints, not only by a SELECT-then-INSERT check. Concurrent duplicate registrations become `DuplicateKeyException` and are translated to `USERNAME_ALREADY_EXISTS` or `EMAIL_ALREADY_EXISTS`.

## Authentication flow

1. `POST /api/auth/register` validates input, hashes the password with BCrypt, inserts the user, and returns a safe user payload. It does **not** issue a token.
2. `POST /api/auth/login` accepts `identifier` as username **or** email, compares the password, and returns a JWT access token plus user info.
3. The Vue client stores the token in `localStorage` and sends `Authorization: Bearer <token>`.
4. `JwtAuthenticationFilter` reconstructs `AuthPrincipal` from JWT claims (`sub`/`userId`, `role`).
5. `GET /api/users/me` loads the current user from PostgreSQL using the authenticated principal. It does not accept a user id from the client.
6. Frontend logout clears local state. The access token remains valid until it expires because there is no server-side session store.

## Security decisions

- Passwords are hashed with `BCryptPasswordEncoder`. Plaintext is never stored or returned.
- JWT uses HS256 via Nimbus JOSE + JWT. Claims are `sub`, `userId`, `role`, `iat`, `exp` only.
- `SessionCreationPolicy.STATELESS` — no HTTP server sessions.
- Public: `POST /api/auth/register`, `POST /api/auth/login`, `GET /api/health`.
- Protected: `GET /api/users/me`.
- Invalid credentials always return the same `INVALID_CREDENTIALS` message.
- JWT secret and TTL come from environment / Spring configuration. The committed default is a local-development placeholder only.
- Frontend tokens live in `localStorage` for Milestone 2 simplicity. XSS can expose those tokens. Production systems often prefer HttpOnly Secure cookies depending on the deployment shape. This is not claimed as the strongest production design.

## API endpoints

| Method | Path | Auth | Success |
| --- | --- | --- | --- |
| GET | `/api/health` | Public | `{"status":"ok"}` |
| POST | `/api/auth/register` | Public | `201` user payload |
| POST | `/api/auth/login` | Public | `200` token + user |
| GET | `/api/users/me` | Bearer JWT | `200` current user |

Error shape:

```json
{
  "code": "USERNAME_ALREADY_EXISTS",
  "message": "Username is already in use",
  "timestamp": "2026-08-28T01:00:00Z"
}
```

## Frontend changes

- `LoginView`, `RegisterView`, `ProfileView`
- Pinia `auth` store: `register`, `login`, `logout`, `fetchCurrentUser`, `initializeAuth`
- Axios interceptor attaches the Bearer token and clears invalid auth on `401`
- Profile route is guarded; unauthenticated users go to `/login`
- After registration the UI redirects to login
- After refresh, `initializeAuth` calls `/api/users/me` before protected content is shown

## Tests

Backend tests use Testcontainers (`postgres:16-alpine`) and Flyway. Docker must be available.

Covered:

- successful registration
- duplicate username / email
- invalid registration input
- password not stored as plaintext
- login by username or email
- wrong password / unknown user
- JWT issued on login
- `/api/users/me` without token, with valid token, with invalid/expired token
- mapper insert + findById

Frontend Vitest covers auth store login/logout/restore and the profile route guard.

## Manual verification

See [docs/development.md](../development.md) for the register → login → profile → refresh → logout flow, plus `curl` / PowerShell examples.

## Known limitations

- No refresh tokens. After one hour the user must log in again.
- Logout is client-side only. A stolen access token works until expiry.
- `localStorage` is XSS-sensitive.
- Disabled users cannot log in; an already-issued token for a later-disabled user gets `USER_NOT_FOUND` from `/api/users/me`.
- No email verification, password reset, lockout, or rate limiting.
- Redis and MinIO remain unused by application code.

## Definition of Done

- [x] Flyway creates `users`
- [x] Registration, login, and `/api/users/me` work
- [x] Duplicate username/email rejected
- [x] Passwords hashed
- [x] JWT required for the current-user API
- [x] Frontend register/login/profile/logout and refresh restore
- [x] Backend tests and frontend build
- [x] Documentation updated
- [x] No production secrets committed
