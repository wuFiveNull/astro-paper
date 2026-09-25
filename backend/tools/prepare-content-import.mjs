/* eslint-disable no-console -- this file is an explicit command-line utility. */
import { copyFile, mkdir, readFile, readdir, stat, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import kebabCase from "lodash.kebabcase";
import { load } from "js-yaml";
import slugify from "slugify";

const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");
const contentRoot = path.join(repositoryRoot, "src", "content", "posts");
const publicAssetRoot = path.join(repositoryRoot, "public", "legacy-assets");
const defaultSqlPath = path.join(repositoryRoot, "backend", "target", "content-import.sql");
const configSource = await readFile(path.join(repositoryRoot, "astro-paper.config.ts"), "utf8");

const usage = `Prepare a reviewable, insert-only MySQL import from src/content/posts.

Dry run (default):
  node backend/tools/prepare-content-import.mjs

Write SQL and copy referenced local images:
  node backend/tools/prepare-content-import.mjs --write --author-id 1

Options:
  --source-dir <path>  Content directory (default: src/content/posts)
  --sql-out <path>     SQL output path (default: backend/target/content-import.sql)
  --asset-dir <path>   Public asset directory (default: public/legacy-assets)
  --author-id <id>     Existing users.id used as the database author_id (required with --write)
  --write              Generate the SQL and copy assets; without it, only validate and summarize.
  --help               Show this help.
`;

function parseArguments(argv) {
  const options = { write: false, authorId: null, sourceDir: contentRoot, sqlOut: defaultSqlPath, assetDir: publicAssetRoot };
  for (let index = 0; index < argv.length; index += 1) {
    const argument = argv[index];
    if (argument === "--help") {
      console.log(usage);
      process.exit(0);
    }
    if (argument === "--write") {
      options.write = true;
      continue;
    }
    if (["--source-dir", "--sql-out", "--asset-dir", "--author-id"].includes(argument)) {
      const value = argv[index + 1];
      if (!value || value.startsWith("--")) throw new Error(`Missing value for ${argument}.`);
      index += 1;
      if (argument === "--source-dir") options.sourceDir = path.resolve(value);
      if (argument === "--sql-out") options.sqlOut = path.resolve(value);
      if (argument === "--asset-dir") options.assetDir = path.resolve(value);
      if (argument === "--author-id") options.authorId = parsePositiveInteger(value, "--author-id");
      continue;
    }
    throw new Error(`Unknown option: ${argument}`);
  }
  if (options.write && options.authorId === null) {
    throw new Error("--author-id is required with --write and must identify an existing database user.");
  }
  return options;
}

function parsePositiveInteger(value, name) {
  if (!/^\d+$/.test(value) || Number(value) < 1 || !Number.isSafeInteger(Number(value))) {
    throw new Error(`${name} must be a positive integer.`);
  }
  return Number(value);
}

function readConfigString(property, fallback) {
  const siteConfig = configSource.match(/site\s*:\s*\{([\s\S]*?)\n\s*\},/);
  const match = siteConfig?.[1].match(new RegExp(`\\b${property}\\s*:\\s*(["'])(.*?)\\1`));
  return match?.[2] ?? fallback;
}

const defaultAuthor = readConfigString("author", "AstroPaper");
const defaultTimezone = readConfigString("timezone", "UTC");

function toSlug(value) {
  const text = String(value).trim();
  const result = /[^\x00-\x7F]/.test(text)
    ? kebabCase(text)
    : slugify(text, { lower: true });
  return result.replace(/^-+|-+$/g, "");
}

function findFrontmatter(source, filename) {
  const normalized = source.replace(/^\uFEFF/, "").replace(/\r\n?/g, "\n");
  const lines = normalized.split("\n");
  if (lines[0]?.trim() !== "---") throw new Error(`${filename}: missing YAML frontmatter opener.`);
  const closingIndex = lines.findIndex((line, index) => index > 0 && /^(---|\.\.\.)\s*$/.test(line));
  if (closingIndex < 0) throw new Error(`${filename}: missing YAML frontmatter closer.`);

  const metadata = load(lines.slice(1, closingIndex).join("\n"));
  if (metadata === null || typeof metadata !== "object" || Array.isArray(metadata)) {
    throw new Error(`${filename}: frontmatter must be a YAML mapping.`);
  }
  return { metadata, body: lines.slice(closingIndex + 1).join("\n").trim() };
}

function isFenceStart(line) {
  return line.match(/^\s{0,3}(`{3,}|~{3,})/);
}

function isFenceEnd(line, fence) {
  return new RegExp(`^\\s{0,3}${fence.character}{${fence.length},}\\s*$`).test(line);
}

function convertMdxBody(body, filename, rewriteImageUrl) {
  const output = [];
  let fence = null;

  for (const line of body.split("\n")) {
    if (fence) {
      output.push(line);
      if (isFenceEnd(line, fence)) fence = null;
      continue;
    }

    const openingFence = isFenceStart(line);
    if (openingFence) {
      output.push(line);
      fence = { character: openingFence[1][0], length: openingFence[1].length };
      continue;
    }

    if (/^\s*import\s+ResponsiveTable\s+from\s+(['"])@\/components\/ResponsiveTable\.astro\1\s*;?\s*$/.test(line)) {
      output.push("");
      continue;
    }
    if (/^\s*<ResponsiveTable\b[^>]*>\s*$/.test(line) || /^\s*<\/ResponsiveTable\s*>\s*$/.test(line)) {
      output.push("");
      continue;
    }
    if (/^\s*(import|export)\s/.test(line)) {
      throw new Error(`${filename}: unsupported executable MDX statement outside a fenced code block: ${line.trim()}`);
    }
    if (/<\/?[A-Z][A-Za-z0-9.]*(?:\s|\/?>)/.test(line)) {
      throw new Error(`${filename}: unsupported MDX component outside a fenced code block: ${line.trim()}`);
    }

    let converted = line;
    if (!converted.includes("<!--")) {
      converted = converted.replace(/(!\[[^\]]*\]\()([^\s)]+)([^)]*\))/g, (_match, prefix, url, suffix) => `${prefix}${rewriteImageUrl(url)}${suffix}`);
      converted = converted.replace(/(\bsrc\s*=\s*["'])([^"']+)(["'])/gi, (_match, prefix, url, suffix) => `${prefix}${rewriteImageUrl(url)}${suffix}`);
    }
    output.push(converted);
  }

  if (fence) throw new Error(`${filename}: unclosed fenced code block.`);
  return output.join("\n").trim();
}

function parseDate(value, field, filename, optional = false) {
  if (value === undefined || value === null || value === "") {
    if (optional) return null;
    throw new Error(`${filename}: ${field} is required.`);
  }
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.valueOf())) throw new Error(`${filename}: ${field} is not a valid date.`);
  return date;
}

function sqlDate(date) {
  if (!date) return "NULL";
  const iso = date.toISOString();
  return sql(`${iso.slice(0, 19).replace("T", " ")}.${iso.slice(20, 23)}000`);
}

function sql(value) {
  if (value === null || value === undefined) return "NULL";
  return `'${String(value).replaceAll("'", "''")}'`;
}

function isWithin(parent, child) {
  const relative = path.relative(parent, child);
  return relative !== "" && relative !== ".." && !relative.startsWith(`..${path.sep}`) && !path.isAbsolute(relative);
}

function makeAssetResolver(repositoryRoot, sourceFile, contentDirectory, options) {
  const publicRoot = path.join(repositoryRoot, "public");
  const assetCopies = options.assetCopies;
  return rawUrl => {
    const decodedUrl = rawUrl.trim();
    if (
      !decodedUrl ||
      /^https?:/i.test(decodedUrl) ||
      decodedUrl.startsWith("data:") ||
      decodedUrl.startsWith("//") ||
      decodedUrl.startsWith("#") ||
      decodedUrl.startsWith("/")
    ) return rawUrl;

    let sourceAsset;
    if (decodedUrl.startsWith("@/assets/")) {
      sourceAsset = path.resolve(repositoryRoot, "src", "assets", decodedUrl.slice("@/assets/".length));
    } else if (decodedUrl.startsWith("@/")) {
      throw new Error(`${path.relative(repositoryRoot, sourceFile)}: unsupported local asset alias: ${decodedUrl}`);
    } else {
      sourceAsset = path.resolve(path.dirname(sourceFile), decodeURIComponent(decodedUrl));
    }

    if (!isWithin(repositoryRoot, sourceAsset)) {
      throw new Error(`${path.relative(repositoryRoot, sourceFile)}: local image escapes the repository: ${decodedUrl}`);
    }
    if (!isWithin(path.join(repositoryRoot, "src", "assets"), sourceAsset) &&
        !isWithin(contentDirectory, sourceAsset)) {
      throw new Error(`${path.relative(repositoryRoot, sourceFile)}: local image is outside supported asset directories: ${decodedUrl}`);
    }

    const sourceRoot = isWithin(path.join(repositoryRoot, "src", "assets"), sourceAsset)
      ? path.join(repositoryRoot, "src", "assets")
      : contentDirectory;
    const sourceRelative = path.relative(sourceRoot, sourceAsset);
    const destinationRelative = sourceRoot === path.join(repositoryRoot, "src", "assets")
      ? path.join("images", sourceRelative)
      : path.join("posts", sourceRelative);
    const destinationPath = path.resolve(options.assetDir, destinationRelative);
    const publicRelative = path.relative(publicRoot, destinationPath);
    const publicUrl = `/${publicRelative.split(path.sep).map(encodeURIComponent).join("/")}`;

    const existing = assetCopies.get(destinationPath);
    if (existing && existing !== sourceAsset) {
      throw new Error(`Multiple local assets map to the same public path: ${destinationRelative}`);
    }
    assetCopies.set(destinationPath, sourceAsset);
    return publicUrl;
  };
}

async function listContentFiles(sourceDirectory) {
  const files = [];
  async function walk(directory) {
    const entries = await readdir(directory, { withFileTypes: true });
    entries.sort((first, second) => first.name.localeCompare(second.name));
    for (const entry of entries) {
      const fullPath = path.join(directory, entry.name);
      if (entry.isDirectory()) {
        await walk(fullPath);
        continue;
      }
      if (!entry.isFile() || entry.name.startsWith("_")) continue;
      if (/\.(?:md|mdx)$/i.test(entry.name)) files.push(fullPath);
    }
  }
  await walk(sourceDirectory);
  return files;
}

function readBoolean(value, fallback, field, filename) {
  if (value === undefined || value === null) return fallback;
  if (typeof value !== "boolean") throw new Error(`${filename}: ${field} must be true or false.`);
  return value;
}

function validateText(value, field, maxLength, filename, required = false) {
  if (value === undefined || value === null) {
    if (required) throw new Error(`${filename}: ${field} is required.`);
    return null;
  }
  if (typeof value !== "string") throw new Error(`${filename}: ${field} must be text.`);
  if (required && !value.trim()) throw new Error(`${filename}: ${field} must not be blank.`);
  if (value.length > maxLength) throw new Error(`${filename}: ${field} exceeds ${maxLength} characters.`);
  return value;
}

function postSlug(metadata, file, sourceDirectory) {
  const relativeFile = path.relative(sourceDirectory, file);
  const fileParts = relativeFile.split(path.sep);
  const parentSegments = fileParts.slice(0, -1)
    .filter(segment => !segment.startsWith("_"))
    .map(toSlug)
    .filter(Boolean);
  const explicitSlug = validateText(metadata.slug, "slug", 180, relativeFile);
  const stem = explicitSlug ?? path.basename(file, path.extname(file));
  const leaf = toSlug(stem.split(/[\\/]/).filter(Boolean).at(-1) ?? "");
  const slugValue = [...parentSegments, leaf].join("/");
  if (!slugValue || slugValue.length > 180) throw new Error(`${relativeFile}: derived post slug is empty or exceeds 180 characters.`);
  return slugValue;
}

async function parsePost(file, sourceDirectory, repositoryRoot, assetDir, assetCopies) {
  const source = await readFile(file, "utf8");
  const filename = path.relative(repositoryRoot, file).split(path.sep).join("/");
  const { metadata, body } = findFrontmatter(source, filename);
  const title = validateText(metadata.title, "title", 255, filename, true);
  const description = validateText(metadata.description ?? "", "description", 500, filename);
  const author = validateText(metadata.author ?? defaultAuthor, "author", 100, filename, true);
  const publishedAt = parseDate(metadata.pubDatetime, "pubDatetime", filename);
  const modifiedAt = parseDate(metadata.modDatetime, "modDatetime", filename, true);
  const timezone = validateText(metadata.timezone ?? defaultTimezone, "timezone", 64, filename);
  const canonicalUrl = validateText(metadata.canonicalURL, "canonicalURL", 2048, filename);
  const hideEditPost = readBoolean(metadata.hideEditPost, false, "hideEditPost", filename);
  const featured = readBoolean(metadata.featured, false, "featured", filename);
  const draft = readBoolean(metadata.draft, false, "draft", filename);
  const rawTags = metadata.tags ?? ["others"];
  if (!Array.isArray(rawTags) || rawTags.some(tag => typeof tag !== "string")) {
    throw new Error(`${filename}: tags must be a list of strings.`);
  }
  const tags = [...new Set(rawTags.map(tag => tag.trim()).filter(Boolean))];
  if (!tags.length) tags.push("others");
  for (const tag of tags) {
    if (tag.length > 100 || !toSlug(tag)) throw new Error(`${filename}: tag is empty or exceeds 100 characters: ${tag}`);
  }

  const resolver = makeAssetResolver(repositoryRoot, file, sourceDirectory, { assetDir, assetCopies });
  let ogImage = validateText(metadata.ogImage, "ogImage", 2048, filename);
  if (ogImage) ogImage = resolver(ogImage);
  const markdown = path.extname(file).toLowerCase() === ".mdx"
    ? convertMdxBody(body, filename, url => resolver(url))
    : rewriteMarkdownImages(body, resolver);

  return {
    source: filename,
    slug: postSlug(metadata, file, sourceDirectory),
    title,
    description,
    author,
    publishedAt,
    modifiedAt,
    timezone,
    featured,
    draft,
    tags,
    canonicalUrl,
    ogImage,
    hideEditPost,
    markdown,
  };
}

function rewriteMarkdownImages(body, resolver) {
  const output = [];
  let fence = null;
  for (const line of body.split("\n")) {
    if (fence) {
      output.push(line);
      if (isFenceEnd(line, fence)) fence = null;
      continue;
    }
    const openingFence = isFenceStart(line);
    if (openingFence) {
      output.push(line);
      fence = { character: openingFence[1][0], length: openingFence[1].length };
      continue;
    }
    let converted = line;
    if (!converted.includes("<!--")) {
      converted = converted.replace(/(!\[[^\]]*\]\()([^\s)]+)([^)]*\))/g, (_match, prefix, url, suffix) => `${prefix}${resolver(url)}${suffix}`);
      converted = converted.replace(/(\bsrc\s*=\s*["'])([^"']+)(["'])/gi, (_match, prefix, url, suffix) => `${prefix}${resolver(url)}${suffix}`);
    }
    output.push(converted);
  }
  if (fence) throw new Error("Markdown source contains an unclosed fenced code block.");
  return output.join("\n").trim();
}

function chooseCanonicalTags(posts) {
  const candidates = [...posts].sort((first, second) => second.publishedAt - first.publishedAt);
  const canonicalNames = new Map();
  for (const post of candidates) {
    for (const name of post.tags) {
      const slug = toSlug(name);
      if (!canonicalNames.has(slug)) canonicalNames.set(slug, name);
    }
  }
  for (const post of posts) {
    post.tags = [...new Set(post.tags.map(name => canonicalNames.get(toSlug(name))))];
  }
  return [...canonicalNames].map(([slug, name]) => ({ slug, name })).sort((first, second) => first.slug.localeCompare(second.slug));
}

function buildSql(posts, tags, authorId) {
  const statements = [
    "-- Generated by backend/tools/prepare-content-import.mjs. Review before applying.",
    "-- Insert-only: duplicate slugs or conflicting tags stop this transaction; existing content is never updated.",
    "SET NAMES utf8mb4;",
    "SET SESSION sql_mode = CONCAT(@@sql_mode, ',NO_BACKSLASH_ESCAPES');",
    "START TRANSACTION;",
    `SET @import_author_id = ${authorId};`,
  ];

  if (tags.length) {
    statements.push("INSERT INTO tags (slug, name) VALUES");
    statements.push(tags.map(tag => `  (${sql(tag.slug)}, ${sql(tag.name)})`).join(",\n") + ";");
  }

  for (const post of posts) {
    const status = post.draft ? "DRAFT" : "PUBLISHED";
    statements.push(
      `INSERT INTO posts (slug, title, description, content_markdown, cover_image_url, status, author_id, author_name, published_at, modified_at, timezone, featured, canonical_url, og_image_url, hide_edit_post) VALUES (${[
        sql(post.slug),
        sql(post.title),
        sql(post.description),
        sql(post.markdown),
        "NULL",
        sql(status),
        "@import_author_id",
        sql(post.author),
        sqlDate(post.publishedAt),
        sqlDate(post.modifiedAt),
        sql(post.timezone),
        post.featured ? "TRUE" : "FALSE",
        sql(post.canonicalUrl),
        sql(post.ogImage),
        post.hideEditPost ? "TRUE" : "FALSE",
      ].join(", ")});`
    );
    statements.push("SET @import_post_id = LAST_INSERT_ID();");
    if (post.tags.length) {
      statements.push(
        `INSERT INTO post_tags (post_id, tag_id) SELECT @import_post_id, id FROM tags WHERE slug IN (${post.tags.map(tag => sql(toSlug(tag))).join(", ")});`
      );
    }
  }

  statements.push("COMMIT;");
  statements.push("SELECT COUNT(*) AS imported_posts FROM posts WHERE slug IN (");
  statements.push(posts.map(post => `  ${sql(post.slug)}`).join(",\n") + ");");
  return `${statements.join("\n\n")}\n`;
}

async function ensureNoAssetOverwrite(assetCopies) {
  for (const [destination, source] of assetCopies) {
    try {
      const [existing, incoming] = await Promise.all([readFile(destination), readFile(source)]);
      if (!existing.equals(incoming)) throw new Error(`Refusing to overwrite a different asset: ${destination}`);
    } catch (error) {
      if (error instanceof Error && error.message.startsWith("Refusing to overwrite")) throw error;
      if (error?.code !== "ENOENT") throw error;
    }
  }
}

async function main() {
  const options = parseArguments(process.argv.slice(2));
  const sourceDirectory = options.sourceDir;
  const sourceStats = await stat(sourceDirectory).catch(() => null);
  if (!sourceStats?.isDirectory()) throw new Error(`Content directory does not exist: ${sourceDirectory}`);
  if (!isWithin(path.join(repositoryRoot, "public"), options.assetDir)) {
    throw new Error("--asset-dir must be a new or existing directory inside the repository public/ directory.");
  }

  const files = await listContentFiles(sourceDirectory);
  if (!files.length) throw new Error(`No Markdown or MDX posts found in ${sourceDirectory}`);
  const assetCopies = new Map();
  const posts = [];
  for (const file of files) {
    posts.push(await parsePost(file, sourceDirectory, repositoryRoot, options.assetDir, assetCopies));
  }
  for (const sourceAsset of assetCopies.values()) {
    try {
      await stat(sourceAsset);
    } catch {
      throw new Error(`Referenced local image does not exist: ${path.relative(repositoryRoot, sourceAsset)}`);
    }
  }

  const duplicateSlugs = posts.map(post => post.slug).filter((slug, index, all) => all.indexOf(slug) !== index);
  if (duplicateSlugs.length) throw new Error(`Duplicate post slugs in source: ${[...new Set(duplicateSlugs)].join(", ")}`);
  const tags = chooseCanonicalTags(posts);

  const drafts = posts.filter(post => post.draft).length;
  const mdx = posts.filter(post => post.source.toLowerCase().endsWith(".mdx")).length;
  console.log(`Validated ${posts.length} posts (${posts.length - drafts} published, ${drafts} drafts), ${tags.length} tags, ${mdx} MDX conversions, and ${assetCopies.size} local assets.`);
  for (const post of posts) {
    console.log(`  ${post.draft ? "DRAFT" : "PUBLISHED"}\t${post.slug}\t${post.source}`);
  }
  console.log("Source files are left unchanged. Import SQL is insert-only and wrapped in one transaction.");

  if (!options.write) {
    console.log("Dry run only. Use --write --author-id <existing users.id> to write the SQL and copy public assets.");
    return;
  }

  await ensureNoAssetOverwrite(assetCopies);
  const sqlText = buildSql(posts, tags, options.authorId);
  await mkdir(path.dirname(options.sqlOut), { recursive: true });
  await writeFile(options.sqlOut, sqlText, "utf8");
  for (const [destination, source] of assetCopies) {
    await mkdir(path.dirname(destination), { recursive: true });
    await copyFile(source, destination);
  }

  console.log(`SQL written to ${path.relative(repositoryRoot, options.sqlOut)}.`);
  console.log(`${assetCopies.size} public image assets copied under ${path.relative(repositoryRoot, options.assetDir)}.`);
  console.log("Review the SQL and confirm the author ID before applying it to the intended database.");
}

main().catch(error => {
  console.error(error instanceof Error ? error.message : String(error));
  process.exitCode = 1;
});
