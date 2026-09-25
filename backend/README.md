# AstroPaper API

Spring Boot backend for the AstroPaper blog. It provides health checks, Flyway-managed MySQL schema, JPA mappings, and public read APIs for published posts, tags, tag filtering, and search. Astro pages still use the static content collection until the SSR integration and Markdown import milestones are implemented.

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

## Public content API

- `GET /api/v1/posts?page=0&size=10` returns published posts whose publication time has arrived.
- `GET /api/v1/posts/{slug}` returns the post metadata and Markdown body.
- `GET /api/v1/tags` lists tags that have at least one public post.
- `GET /api/v1/tags/{tagSlug}/posts?page=0&size=10` filters published posts by tag.
- `GET /api/v1/search?q=keyword&page=0&size=10` searches published post title, description, and Markdown source.

Post metadata preserves the AstroPaper frontmatter names where practical (`pubDatetime`, `modDatetime`, `canonicalURL`, `ogImage`, `hideEditPost`). V3 adds storage for those fields. Existing MDX content is not imported or executed by the API; the migration/import tool will convert supported MDX to Markdown in a later milestone.

No administrator account or sample password is seeded. Bootstrap of the first administrator will be implemented as a deliberate, one-time administrative operation before authentication is enabled.

## Isolated cloud smoke deployment

`deploy/compose.test.yaml` starts a separate MySQL 8.4 and API stack. MySQL has no published host port; the API port is bound to `127.0.0.1` only. The Compose project does not modify the existing site proxy or port 80.

For a server test, copy `target/astro-paper-api.jar`, `deploy/compose.test.yaml`, and `deploy/.env.example` to a private server directory. Create a private `.env` there with distinct random `DB_PASSWORD` and `MYSQL_ROOT_PASSWORD` values, then start the stack with:

```bash
docker compose --project-name astro-paper-api-test -f compose.test.yaml up -d
```

Verify `http://127.0.0.1:18081/api/v1/health` and `http://127.0.0.1:18081/actuator/health` from the server. Keep the private `.env` on the server only. The named MySQL volume retains test data across container restarts; do not remove it unless its data is intentionally disposable.
