# Running on this machine or another machine

## 1. Recommended: Docker-only local setup

You need Docker Engine/Desktop with the Compose v2 plugin, an internet connection for the first image build, and the complete project folder. Java, Maven, Node.js, and a host PostgreSQL installation are **not** required for this path.

On Windows, start Docker Desktop and select Linux containers. On macOS, start Docker Desktop. On Linux, start Docker Engine and ensure your user can run Docker. Allow roughly 4 GB of available memory for Docker when building and testing.

Copy the project folder to the other machine (or clone it after publishing to your own repository). Keep `docker-compose.yml` and the complete `backend` directory together. Build outputs such as `backend/target` are not needed.

From the folder containing `docker-compose.yml`, run:

```powershell
docker info
docker compose config --quiet
docker compose up -d --build --wait backend
docker compose ps
```

The same Docker commands work in PowerShell, Bash, and Zsh. The first build downloads Java/Maven images and dependencies and takes longer than subsequent builds.

Open:

- Application: <http://localhost:8080>
- Swagger: <http://localhost:8080/swagger-ui.html>
- Health: <http://localhost:8080/actuator/health>

The health endpoint must return `{"status":"UP"}`. This checks database connectivity as well as application readiness. Compose starts the backend only after PostgreSQL is healthy.

### What starts

| Service | Inside Docker | Host access | Storage |
|---|---|---|---|
| `backend` | Spring Boot / Java 21 on 8080 | `127.0.0.1:8080` | Accounts and OTPs in process memory |
| `postgres` | PostgreSQL 16 on 5432 | `127.0.0.1:55432` | Named volume `northstar-task_postgres-data` |

These are local demo services. Both published ports bind to loopback, not the LAN. Port 55432 avoids an existing database on the common host port 5432. The backend uses Docker DNS `postgres:5432`, **not** the host's 55432 port.

### Try the UI

1. Select **Create account**.
2. Use a username of 8-50 characters, an email containing `_`, and a password of 8-12 characters containing uppercase, lowercase, a digit, and a symbol.
3. Example: `northstar1`, `north_star@example.com`, `Strong!123`.
4. Enter the development OTP displayed on the verification screen.
5. Sign in, add/edit/delete a product, and sign out.
6. Create an Administrator account to test the settings panel. A Standard User receives HTTP 403 for settings.

**Local demo only:** Compose explicitly selects the `dev` profile. OTPs are returned in API responses, administrator signup is allowed, and private IPs use the configured local country (`US`). Do not publish this stack unchanged to the internet.

### Port conflicts and local configuration

Create a `.env` beside `docker-compose.yml` if different host ports are needed:

```dotenv
APP_PORT=8081
POSTGRES_PORT=55433
LOCAL_COUNTRY=US
```

Then rerun `docker compose up -d --build --wait backend` and open <http://localhost:8081>. The `.env` file is ignored by Git.

`DATABASE_PASSWORD` can also be set for a **new** database volume; the same value is passed to the backend and PostgreSQL. Changing it does not change the password of an already initialized database. Rotate an existing database password explicitly; do not delete the volume to resolve a credential mismatch.

### Stop, restart, update

```powershell
docker compose logs --tail=100 backend postgres
docker compose stop backend postgres
docker compose up -d --wait backend
docker compose up -d --build --wait backend
```

`stop` preserves containers and the database volume. `docker compose down` removes this project's containers and network but preserves its named database volume. Do not add a volume-removal flag unless you intentionally want to erase the database. Restarting/recreating the backend loses the assignment's in-memory accounts and OTPs; product data remains.

## 2. Run the automated tests against real PostgreSQL

From the project root:

```powershell
docker compose --profile test build tests
docker compose --profile test run --rm tests
docker compose --profile test stop postgres-test
docker compose --profile test rm -f postgres-test
```

The test command returns a nonzero exit code on failure. In CI, stop the job immediately on a failed build or test command. Test reports are generated inside the test container under `/workspace/target/surefire-reports`; omit `--rm` and use `docker cp` if you need to export them.

This uses a **separate** `taskapp_test` PostgreSQL database on `postgres-test`, with no host port and ephemeral tmpfs storage. The integration suite's `create-drop` schema policy is safe there; never point `TEST_DATABASE_URL` at a database containing application data.

The test image runs unit tests plus Spring MVC/JPA integration tests. These verify real PostgreSQL queries, decimal persistence, updates, deletion, authorization, OTP lockout, and health. They are not browser tests and do not send real SMTP email.

### HTTP smoke test against the running application

On Windows:

```powershell
powershell -NoProfile -File .\scripts\smoke-test.ps1
powershell -NoProfile -File .\scripts\smoke-test.ps1 -VerifyRestart
```

If script execution is blocked, review the script and run it using a process-only policy:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\smoke-test.ps1 -VerifyRestart
```

On macOS/Linux with PowerShell 7 installed, invoke the script using `pwsh -File` and the local path to `scripts/smoke-test.ps1`. PowerShell is optional for running the app and containerized integration suite.

`-VerifyRestart` restarts **this project's** backend and database, verifies that the old session no longer has an account, creates a new account, and verifies that the product survived the restart. This is disruptive to active Northstar sessions; do not run it during a demo. It does not restart unrelated Docker projects.

The script creates uniquely named users and one temporary product. It deletes the product on success and attempts cleanup on failure. Users remain in memory until the backend restarts.

For alternate host ports, pass `-BaseUrl http://localhost:8081`. The restart option operates on this checkout's Compose file, so only use it when the base URL refers to this exact stack.

## 3. Optional: Java locally, PostgreSQL in Docker

Install JDK 21 and Maven 3.9+. Start only the database:

```powershell
docker compose up -d --wait postgres
$env:DATABASE_URL = "jdbc:postgresql://localhost:55432/taskapp"
Set-Location backend
mvn spring-boot:run
```

The native application's fallback database URL uses port 5432; explicitly set the URL above for this Compose database. Stop the containerized backend first if it already occupies port 8080.

For fast tests without Docker:

```powershell
Set-Location backend
mvn test
```

Without `TEST_DATABASE_*` overrides, integration tests use H2. This is a useful fast check, **not** a substitute for the PostgreSQL test command.

## 4. Moving application data to another machine

Copying source code does not copy Docker volumes. To move products:

1. Stop writes in the source app.
2. Create a database dump inside the source PostgreSQL container:
   `docker compose exec -T postgres pg_dump -U taskapp -d taskapp -Fc -f /tmp/taskapp.dump`.
3. Find its container ID with `docker compose ps -q postgres`, then use `docker cp <container-id>:/tmp/taskapp.dump taskapp.dump`.
4. Transfer the dump securely to the other machine. Start only its `postgres` service.
5. Copy the dump into that container with `docker cp taskapp.dump <destination-container-id>:/tmp/taskapp.dump`.
6. Restore into an **empty** destination database:
   `docker compose exec -T postgres pg_restore -U taskapp -d taskapp --no-owner --no-acl /tmp/taskapp.dump`.
7. Start the destination backend and verify products after registering a new account.

Do not restore over a populated destination without an explicit migration plan. Treat database backups as sensitive data. This binary-file approach avoids PowerShell text-redirection encoding issues.

Accounts cannot be transferred because they are intentionally in memory. Persist accounts first if real identity continuity is required.

## 5. Production is a separate deployment task

The supplied Compose file is deliberately a localhost dev setup, not a hardened production template. For an actual deployment:

- Configure a non-dev profile, database credentials, a strong random `JWT_SECRET`, SMTP settings, and bootstrap administrator credentials.
- Do not just put `SPRING_PROFILES_ACTIVE=prod` in `.env`: the demo Compose file explicitly selects `dev` and only forwards its documented variables. Use a reviewed deployment-specific Compose configuration that sets the full production environment.
- Configure TLS and trusted proxy client-IP handling. Unknown/private request locations fail closed outside dev unless explicitly configured.
- Use database migrations, durable identity/session storage, backups, rate limits, monitoring, and an appropriate password policy.
- Run one backend replica until all in-memory identity/session state has been externalized.

See [ARCHITECTURE.md](ARCHITECTURE.md) for limitations and the path to a durable deployment.

## Troubleshooting

| Symptom | Action |
|---|---|
| Docker pipe/socket unavailable | Start Docker Desktop/Engine; verify `docker info` before building. |
| Port already allocated | Set `APP_PORT` or `POSTGRES_PORT` in `.env`; do not stop another project's services. |
| Backend fails to connect | Read Compose logs; container URL must be `postgres:5432`; native URL must use the published host port. |
| PostgreSQL password mismatch | Existing volumes retain their original password. Check the original configuration and rotate explicitly. |
| Sign-in fails after backend restart | Register/verify again; accounts are not persisted by design. |
| Country lookup fails outside dev | Check provider access, trusted proxy integration, and the actual observed client IP. |
| Build is slow on first use | Java images and Maven dependencies must download; subsequent unchanged dependency layers are cached. |
| Test container fails | Read its Maven output; check `postgres-test` health and ensure tests target only the disposable database. |
