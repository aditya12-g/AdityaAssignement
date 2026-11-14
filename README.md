# Northstar Account & Product Portal

A full-stack machine-test implementation of the supplied backend task. It combines a Spring Boot REST API with a small responsive browser UI served by the same application.

## What is implemented

- Sign-up with username, email, password, and account type validation.
- Country lookup using the request IP and configurable blocked country codes (defaults: Syria `SY`, Afghanistan `AF`, and Iran `IR`).
- Six-digit email verification code with a five-minute expiry. The dev profile returns the code in the response; non-dev profiles send it through configured SMTP and never return it.
- BCrypt password hashing and expiring signed JWT sessions.
- Authenticated dashboard and product CRUD, including pagination and sorting, backed by PostgreSQL.
- Administrator-only settings access.
- Logout revokes the active token for the lifetime of the running process.
- Swagger UI at `/swagger-ui.html`.

The PDF both calls for in-memory user data and specifies PostgreSQL product persistence. This app keeps accounts, pending OTPs, and token revocations in memory, and stores products in PostgreSQL. User accounts and revocation state therefore do not survive an application restart.

## Run locally (Docker recommended)

Requirements: Docker Desktop/Engine with Compose. No local Java, Maven, Node.js, or PostgreSQL installation is needed for the containerized setup.

From the project root:

```powershell
docker compose up -d --build --wait backend
docker compose ps
```

Open [http://localhost:8080](http://localhost:8080). PostgreSQL runs in its own container, with product data stored in a named volume. Its host port is **55432** to avoid conflicting with another PostgreSQL server; the backend connects internally to `postgres:5432`.

The Compose stack selects the `dev` profile, uses country code `US` for private client addresses, and returns OTPs to the browser. It permits Administrator signup for local role testing. Both published ports bind only to localhost. **Do not expose this dev setup to the internet.**

See [docs/RUNNING.md](docs/RUNNING.md) for setup on another machine, port changes, native Java development, data transfer, troubleshooting, and deployment limitations.

Example registration data that satisfies the PDF constraints:

| Field | Example |
|---|---|
| Username | `northstar1` |
| Email | `north_star@example.com` |
| Password | `Strong!123` |
| Account type | `Standard User` or `Administrator` |

For production, public Administrator self-registration is disabled. Provision an administrator using `ADMIN_USERNAME`, `ADMIN_EMAIL`, and `ADMIN_PASSWORD`; the configured account is hashed on startup. The password must satisfy the same 8–12 character policy. SMTP must also be configured for OTP delivery.

## Configuration

| Variable | Default / purpose |
|---|---|
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/taskapp` |
| `DATABASE_USERNAME` / `DATABASE_PASSWORD` | `taskapp` / `taskapp` for the local Compose database |
| `JWT_SECRET` | Local dev key; set a unique random value of at least 32 characters outside `dev` |
| `JWT_EXPIRATION` | `PT15M` |
| `OTP_EXPIRATION` | `PT5M` |
| `OTP_INCLUDE_IN_RESPONSE` | `true` in dev, `false` outside dev |
| `GEO_PROVIDER_URL` | `https://ipapi.co` |
| `BLOCKED_COUNTRIES` | `SY,AF,IR` (comma-separated ISO 3166-1 alpha-2 codes) |
| `LOCAL_COUNTRY` | `US` in dev; set empty outside dev so private/proxy addresses fail closed |
| `ALLOW_PUBLIC_ADMIN_REGISTRATION` | `true` in dev; set `false` when testing a locally exposed app |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` | Required outside dev |
| `ADMIN_USERNAME`, `ADMIN_EMAIL`, `ADMIN_PASSWORD` | Required outside dev to bootstrap the in-memory administrator |

Activate a non-dev profile with `SPRING_PROFILES_ACTIVE=prod`. Country lookup uses the server-observed client address, not a country supplied by the browser. The service calls the configured geolocation provider for public IPs and rejects sign-up if lookup fails. A deployment behind a reverse proxy must configure a trusted proxy/client-IP integration; do not blindly trust client-provided `X-Forwarded-For`.

## API overview

See [docs/API.md](docs/API.md) for request/response examples and [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for deployment/component diagrams, data ownership, request sequences, restart behavior, and production limitations. OpenAPI is available at `/v3/api-docs`.

## Tests

```powershell
cd backend
mvn test
```

The default test suite uses H2 for fast MVC/JPA tests. To run the same suite against **real PostgreSQL**, without installing Java/Maven locally:

```powershell
docker compose --profile test build tests
docker compose --profile test run --rm tests
```

Tests use a separate disposable database and never the application volume. Stop the test database afterward with `docker compose --profile test stop postgres-test`.

For full HTTP and restart/persistence checks against the running Docker stack:

```powershell
powershell -NoProfile -File .\scripts\smoke-test.ps1 -VerifyRestart
```

See [docs/TESTING.md](docs/TESTING.md) for verification scope and results.
