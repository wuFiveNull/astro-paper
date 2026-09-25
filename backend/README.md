# AstroPaper API

Spring Boot backend for the AstroPaper blog. It provides health checks, Flyway-managed MySQL schema, JPA mappings, and public read APIs for published posts, tags, tag filtering, and search. The Astro site now renders public post pages through its Node SSR adapter and reads these APIs at request time. A separate content preparation tool converts the repository's Markdown/MDX collection into reviewed, insert-only MySQL SQL; authenticated write APIs remain planned work.

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

Post metadata preserves the AstroPaper frontmatter names where practical (`pubDatetime`, `modDatetime`, `canonicalURL`, `ogImage`, `hideEditPost`). V3 adds storage for those fields. The API stores and renders Markdown; it does not execute database MDX.

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

No administrator account or sample password is seeded. Bootstrap of the first administrator will be implemented as a deliberate, one-time administrative operation before authentication is enabled. The isolated import test used a disabled test-only author account; it is not a production account.

## Isolated cloud smoke deployment

`deploy/compose.test.yaml` starts a separate MySQL 8.4, API, and Astro SSR stack. MySQL has no published host port; API and website test ports are bound to `127.0.0.1` only. The Compose project does not modify the existing site proxy or port 80.

For a server test, copy `target/astro-paper-api.jar`, `deploy/compose.test.yaml`, and `deploy/.env.example` to a private server directory. Create a private `.env` there with distinct random `DB_PASSWORD` and `MYSQL_ROOT_PASSWORD` values, then make the repository source available to the Compose build context and start the stack with:

```bash
docker compose --project-name astro-paper-api-test -f compose.test.yaml up -d
```

Verify `http://127.0.0.1:18081/api/v1/health`, `http://127.0.0.1:18081/actuator/health`, and `http://127.0.0.1:18080/` from the server. Keep the private `.env` on the server only. The named MySQL volume retains test data across container restarts; do not remove it unless its data is intentionally disposable.
