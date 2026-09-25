# Content import preparation

`prepare-content-import.mjs` prepares the existing AstroPaper posts for the MySQL schema. It reads `src/content/posts/`, preserves the source files, validates metadata, converts supported MDX to Markdown, generates a transaction-wrapped insert-only SQL file, and stages local images under `public/legacy-assets/`.

## Requirements

- Node.js 22.12 or later
- Dependencies installed from the repository root with `pnpm install`

## Dry run

From the repository root:

```powershell
node backend/tools/prepare-content-import.mjs
```

The dry run lists derived route slugs and publication state. It does not write SQL, copy assets, connect to MySQL, or edit Markdown/MDX source files.

## Generate import artifacts

Create the intended author account through the eventual admin bootstrap flow, then pass its existing `users.id`:

```powershell
node backend/tools/prepare-content-import.mjs --write --author-id 123
```

Outputs:

- `backend/target/content-import.sql`
- Referenced local images under `public/legacy-assets/`

`--sql-out`, `--asset-dir`, and `--source-dir` can override output or input locations. The asset output must stay inside the repository's `public/` directory so the generated URLs are served by Astro.

## Conversion and safety rules

- Frontmatter fields map to the existing `posts`/`tags` schema; `draft: true` becomes `DRAFT`, otherwise the row becomes `PUBLISHED` and remains hidden until `published_at`.
- Explicit Astro slugs are retained. Visible subdirectories remain in the route; directory segments beginning with `_` are omitted as in the existing route helper.
- Missing author and timezone values use the values from `astro-paper.config.ts`.
- The only supported executable MDX construct is the `ResponsiveTable` import and wrapper. The import and wrapper tags are removed; Markdown tables are wrapped by the runtime Markdown renderer in the existing responsive overflow styles. Code fences are left intact. Other MDX imports/components outside code fences stop the run for manual review.
- Relative images and `@/assets/images/...` references are copied to public paths and rewritten in the generated database content and OG metadata. Remote and root-relative URLs remain unchanged.
- SQL contains one transaction and plain inserts. It does not update, upsert, or delete. A duplicate tag key, slug, or invalid author foreign key causes the client to fail; the open transaction rolls back when the connection closes. The script never contacts a database.
- Existing Markdown/MDX files remain the rollback source. Review the generated SQL and assets, back up the target database, and verify the author ID before applying the SQL.

The cloud verification used only the isolated `astro-paper-api-test` MySQL volume. It imported 18 posts, 15 tags, and 33 post/tag relations; repeating the import failed on the unique tag key and left all three counts unchanged. The test volume retains this sample content and a disabled test-only author. Production data was not imported.
