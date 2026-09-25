# AstroPaper API

Spring Boot backend for the AstroPaper blog. It provides health checks, Flyway-managed MySQL schema, JPA mappings, public read APIs, database-backed authentication, and permission-checked account/role management. The Astro site renders public post pages through its Node SSR adapter and reads content APIs at request time. A separate content preparation tool converts the repository's Markdown/MDX collection into reviewed, insert-only MySQL SQL.

## Requirements

- Java 21
- Maven 3.6.3 or later
- MySQL 8.0 or later for local or server execution

## Build and verify

From the repository root:

```powershell
mvn -f backend/pom.xml clean verify
```

The health controller smoke test uses an isolated in-memory H2 database. Production and server deployments use MySQL and apply the versioned migrations in `src/main/resources/db/migration`.

## Run against MySQL

Create a database and a restricted application account, then configure these environment variables. Do not commit the actual values.

```powershell
$env:DB_URL = "jdbc:mysql://127.0.0.1:3306/astro_paper?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC"
$env:DB_USERNAME = "astro_paper_app"
$env:DB_PASSWORD = "replace-with-a-private-password"
mvn -f backend/pom.xml spring-boot:run
```

The process binds to `127.0.0.1:8081` by default. Check `GET /api/v1/health` for process health and `GET /actuator/health` for application readiness, including database connectivity. Set `SERVER_ADDRESS` and `SERVER_PORT` explicitly if the reverse proxy requires different local values.

## Authentication and account provisioning

Authentication uses a server-side HTTP session and an `HttpOnly`, `SameSite=Lax` session cookie. Unsafe requests require a CSRF token: request `GET /api/v1/auth/csrf`, then send its `token` in the header named by `headerName`. Fetch a fresh token after login or logout. Configure `SESSION_COOKIE_SECURE=true` when the browser reaches the site over HTTPS.

- `POST /api/v1/auth/login` authenticates a JSON `username` and `password` and starts a session.
- `POST /api/v1/auth/logout` ends the session.
- `GET /api/v1/auth/me` returns the signed-in account, role codes, and permission codes.
- `POST /api/v1/admin/users` creates an active `USER` account. Only an active account with the `ADMIN` role and `user:manage` permission can create accounts; there is no public registration endpoint.
- `PUT /api/v1/admin/users/{id}/status` changes an account to `ACTIVE`, `DISABLED`, or `LOCKED`.
- `PUT /api/v1/admin/users/{id}/roles` replaces an account's roles. Assigning `ADMIN` also requires `permission:manage`.
- `GET /api/v1/admin/roles` and `GET /api/v1/admin/permissions` return the seeded access model.
- `PUT /api/v1/admin/roles/{code}/permissions` replaces a non-`ADMIN` role's permissions. Account/access management permissions cannot be assigned to other roles, and the `ADMIN` role cannot be edited.

All account and role operations also check the caller's current database role and permissions in the service layer, so account status and permission changes take effect for existing sessions. The last active administrator cannot be disabled, locked, or stripped of the `ADMIN` role.

The first administrator is created only when the database has no user accounts. For the initial server start, set `ADMIN_BOOTSTRAP_ENABLED=true` plus private `ADMIN_BOOTSTRAP_USERNAME`, `ADMIN_BOOTSTRAP_EMAIL`, `ADMIN_BOOTSTRAP_DISPLAY_NAME`, and `ADMIN_BOOTSTRAP_PASSWORD` values in the server environment. The password must contain at least 12 characters and no more than 72 UTF-8 bytes. The application stores only its encoded hash and never logs the password. Remove all `ADMIN_BOOTSTRAP_*` values after startup. If any account already exists, bootstrap skips without modifying accounts.

The backend's `ADMIN`, `EDITOR`, and `USER` roles and permission codes are seeded by Flyway. This milestone implements login, logout, current-user lookup, first-admin bootstrap, account status changes, role assignment, permission editing, and account creation. Sessions are held by the single API instance; an API restart signs users out.

Only an active administrator with `user:manage` can list accounts or create them. New accounts always receive `USER`; no public registration endpoint exists.

## Public content API

- `GET /api/v1/posts?page=0&size=10` returns published posts whose publication time has arrived.
- `GET /api/v1/posts/{slug}` returns the post metadata and Markdown body.
- `GET /api/v1/tags` lists tags that have at least one public post.
- `GET /api/v1/tags/{tagSlug}/posts?page=0&size=10` filters published posts by tag.
- `GET /api/v1/search?q=keyword&page=0&size=10` searches published post title, description, and Markdown source.

Post metadata preserves the AstroPaper frontmatter names where practical (`pubDatetime`, `modDatetime`, `canonicalURL`, `ogImage`, `hideEditPost`). V3 adds storage for those fields. The API stores and renders Markdown; it does not execute database MDX.

## Comment and guestbook API

- `GET /api/v1/comments?postSlug={slug}&page=0&size=50` returns only published comments for a published article. It exposes the author's display name, not email.
- `POST /api/v1/comments` requires an authenticated account with `comment:create`; send `postSlug`, `body`, and optionally `parentId`. New comments start as `PENDING`, and replies can target only a published comment on the same article.
- `POST /api/v1/messages` requires an authenticated account with `message:create`; send an optional `subject` and required `body`. The sender name and email are copied from the signed-in account, and each message starts as `NEW`.
- `GET /api/v1/admin/comments?status=PENDING` and `PUT /api/v1/admin/comments/{id}/status` require `comment:moderate`. The allowed moderation results are `PUBLISHED` and `REJECTED`.
- `GET /api/v1/admin/messages?status=NEW` requires `message:read`; `PUT /api/v1/admin/messages/{id}/status` requires `message:update`. The allowed states are `IN_PROGRESS`, `RESOLVED`, and `SPAM`.

All write requests also require the session's CSRF token. Public comments and admin/message data use separate DTOs so comment email addresses and private guestbook messages are not exposed publicly.

## Prepare the Markdown/MDX import

Run the migration tool from the repository root with Node.js 22.12 or later:

```powershell
node backend/tools/prepare-content-import.mjs
```

The default is a dry run. It validates frontmatter, route slugs, tags, dates, MDX conversion support, and referenced local images without changing the article sources or writing to a database.

After confirming the target database already has the intended author account, generate the SQL and public image copies:

```powershell
node backend/tools/prepare-content-import.mjs --write --author-id 123
```

The command writes `backend/target/content-import.sql` and copies referenced local images under `public/legacy-assets/`. Review both outputs and commit the public assets before building the web image. The SQL runs in a single transaction, inserts only, and never updates or deletes existing records. Duplicate tag keys or post slugs cause the import to fail; review conflicts rather than silently overwriting them. Run it against the intended database only after taking a backup.

The converter preserves existing route slugs and visible subdirectories, maps `draft: true` to `DRAFT`, keeps author/frontmatter metadata, removes supported `ResponsiveTable` MDX wrappers, and rewrites local image URLs to the copied public assets. It does not execute MDX. An unsupported component or import outside a fenced code example causes validation to fail. The original `.md`/`.mdx` files remain the rollback source.

The importer has been exercised against the isolated cloud test database: 18 source posts, 15 tags, and 33 post/tag links were inserted, and a repeated run failed on a unique tag key without changing those counts. Production content has not been imported.

No administrator account or sample password is seeded. Use the one-time environment bootstrap described above before importing production articles. The isolated import test used a disabled test-only author account; it is not a production account.

## Isolated cloud smoke deployment

`deploy/compose.test.yaml` starts a separate MySQL 8.4, API, and Astro SSR stack. MySQL has no published host port; API and website test ports are bound to `127.0.0.1` only. The Compose project does not modify the existing site proxy or port 80. `deploy/compose.auth-smoke.yaml` is a lower-memory override for testing the API and isolated MySQL without starting/building the website container.

For a server test, copy `target/astro-paper-api.jar`, `deploy/compose.test.yaml`, and `deploy/.env.example` to a private server directory. Create a private `.env` there with distinct random `DB_PASSWORD` and `MYSQL_ROOT_PASSWORD` values, then make the repository source available to the Compose build context and start the stack with:

```bash
docker compose --project-name astro-paper-api-test -f compose.test.yaml up -d
```

Verify `http://127.0.0.1:18081/api/v1/health`, `http://127.0.0.1:18081/actuator/health`, and `http://127.0.0.1:18080/` from the server. Keep the private `.env` on the server only. The named MySQL volume retains test data across container restarts; do not remove it unless its data is intentionally disposable.

For an API-only smoke test on a memory-constrained server, copy the root `Dockerfile`, the locally built JAR, `deploy/compose.test.yaml`, `deploy/compose.auth-smoke.yaml`, and `deploy/auth-smoke-test.sh` into a private directory that preserves the same `backend/deploy/` subdirectory. Start it from that subdirectory with a new Compose project name and an unused loopback port:

```bash
COMPOSE_PROJECT_NAME=astro-paper-api-smoke-local API_HOST_PORT=18082 bash auth-smoke-test.sh
```

The script creates a private `.env` with random test credentials, bootstraps a temporary administrator, recreates the API without bootstrap secrets, and checks health, login, account creation, comment visibility/moderation, guestbook status changes, permission denials, and account disable. It starts only isolated MySQL and API services, skips the Astro image build, caps combined container memory at 640 MiB, and removes its generated `.env`, cookie jars, containers, and dedicated disposable database volume when it exits, including after a failed run. Its project name must start with `astro-paper-api-smoke-`; cleanup is scoped to that Compose project. It does not stop or connect to the production stack.
