# Verification report and test guide

Verified on 2026-11-14 using Windows with Docker Desktop Linux containers, Java 21, Spring Boot 3.4.4, and PostgreSQL 16.15. These are local functional tests, not a production certification.

## Results

| Layer | Result | What was actually exercised |
|---|---|---|
| Maven / H2 suite | 13 tests passed, zero failures/errors/skips | Unit tests plus Spring MVC, security, and JPA integration |
| Docker PostgreSQL suite | The same 13 tests passed, zero failures/errors/skips | Integration tests used `org.postgresql.jdbc.PgConnection` and PostgreSQL 16, not H2 |
| Live Docker HTTP smoke test | 37 checks passed with restart verification | Actual HTTP API, running backend, application PostgreSQL, both service restarts |
| Browser workflow | Passed | Signup, OTP, login, product create/edit/delete, admin settings, reload restoration, logout |
| Responsive browser layout | Passed at 320, 390, 768, and 1280 pixel viewports | Document scroll width did not exceed client width |
| Docker packaging | Passed | Multi-stage image build, application and database readiness checks |
| Frontend syntax | Passed | `node --check` on the browser script |

The H2 and PostgreSQL runs execute the same 13 tests; they are not 26 distinct test cases.

## Automated suite coverage

### `AuthCountryPolicyTest`: 3 tests

- Blocked-country registration is rejected before hashing/delivery.
- Country-provider failure does not allow registration.
- Public Administrator registration is rejected when disabled.

These are policy tests with a mocked country lookup. They do not prove a third-party provider's country accuracy.

### `AuthDtosValidationTest`: 3 tests

- Required registration values are accepted.
- A password lacking required character classes is rejected.
- Email without the task's required underscore is rejected.

### `JwtServiceTest`: 2 tests

- A signed JWT is accepted and rejected after logout revocation.
- An already expired JWT is rejected.

### `TaskAuthIntegrationTest`: 5 tests

- Signup/OTP/login, dashboard welcome, standard-role settings denial, product create/read/update/list/delete, 404 after deletion, logout, and 401 after logout.
- Administrator settings access and the exact `Login Failed` message for incorrect credentials.
- Anonymous product access and malformed tokens rejected; database-backed health is UP.
- Invalid product input rejected before persistence.
- Five wrong OTP submissions invalidate a challenge, including rejection of its previously correct code afterward.

## Running against real PostgreSQL

From the project root:

```powershell
docker compose --profile test build tests
docker compose --profile test run --rm tests
```

The test service supplies `TEST_DATABASE_URL`, username, password, and PostgreSQL driver. `application-test.yml` consumes these overrides. Without them, the fast suite defaults to H2.

The database is `taskapp_test` in `postgres-test`, not the app's `taskapp` database. `create-drop` affects only this disposable test schema. The test service uses tmpfs storage with no published port.

Clean up the dedicated test database after testing:

```powershell
docker compose --profile test stop postgres-test
docker compose --profile test rm -f postgres-test
```

## Live HTTP and restart verification

Start the app:

```powershell
docker compose up -d --build --wait backend
```

Then run:

```powershell
powershell -NoProfile -File .\scripts\smoke-test.ps1 -VerifyRestart
```

The script checks HTTP status codes and key response values. It verifies:

- Health, static UI, and generated OpenAPI document are served.
- Unauthenticated product access is denied.
- Unverified accounts cannot log in; OTP activation enables login.
- Standard users cannot access settings, while Administrator users can.
- Products can be created, updated, read, paged, and deleted, with decimal prices preserved.
- A product remains readable after both PostgreSQL and backend services restart.
- The pre-restart ordinary user session no longer has a server-side account.
- Logout immediately rejects subsequent protected requests.

**Important:** this restart check verifies the current in-memory-account behavior. It does not prove that token revocation survives a restart or identity recreation. Those are known architecture limitations.

The script creates unique accounts and deletes its temporary product. Browser test products were also deleted. Disposable test containers were cleaned up without removing the application volume or touching another project's database.

## Browser testing and fixes

The browser UI was exercised through the running Docker app, not by opening static HTML alone. A mobile overflow defect was found: the product form was missing the shared form class, and grid minimum widths could expand past a narrow viewport. The fix adds the form styling and explicit shrinkable grid tracks/items; long dashboard usernames now wrap instead of expanding the page.

After rebuilding the container, the populated catalog was rechecked at 320, 390, 768, and 1280 pixel viewport widths. At every size, `document.documentElement.scrollWidth` equaled `clientWidth` (accounting for the browser's vertical scrollbar). Reload restored the authenticated workspace; deletion produced the empty catalog; logout returned the required username message.

## Not verified / known limits

- Actual SMTP delivery, email-provider credentials, or inbox receipt.
- Public-IP geolocation accuracy or trusted reverse-proxy integration.
- Deployment on a second physical computer or ARM architecture. The documented path uses a fresh Docker build; this session tested Windows-hosted Linux containers.
- Production profile startup with real secrets/admin/SMTP configuration.
- Load, penetration, accessibility, backup-restore, or multi-replica testing.
- Concurrency/lost-update behavior, unexpected database outages, and every malformed request shape.

Use [RUNNING.md](RUNNING.md) for other-machine setup, port changes, data transfer, and troubleshooting, and [ARCHITECTURE.md](ARCHITECTURE.md) for design limitations.
