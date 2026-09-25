import type { APIRoute } from "astro";
import { getAllPosts, getTags, publicPostUrl } from "@/lib/blog-api";
import config from "@/config";

export const prerender = false;

const escapeXml = (value: string) =>
  value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&apos;");

export const GET: APIRoute = async ({ site }) => {
  const baseUrl = site ?? new URL(config.site.url);
  const [posts, tags] = await Promise.all([getAllPosts(), getTags()]);

  const locations: Array<{ url: string; lastmod?: string | null }> = [
    { url: new URL("/", baseUrl).href },
    { url: new URL("/posts/", baseUrl).href },
    { url: new URL("/tags/", baseUrl).href },
    { url: new URL("/archives/", baseUrl).href },
    { url: new URL("/search/", baseUrl).href },
    { url: new URL("/about/", baseUrl).href },
    ...tags.map(tag => ({
      url: new URL(`/tags/${encodeURIComponent(tag.slug)}/`, baseUrl).href,
    })),
    ...posts.map(post => ({
      url: new URL(publicPostUrl(post.slug, config.site.lang), baseUrl).href,
      lastmod: post.modDatetime ?? post.pubDatetime,
    })),
  ];

  const entries = locations
    .map(
      ({ url, lastmod }) =>
        `  <url><loc>${escapeXml(url)}</loc>${lastmod ? `<lastmod>${escapeXml(new Date(lastmod).toISOString())}</lastmod>` : ""}</url>`
    )
    .join("\n");
  const xml = `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n${entries}\n</urlset>`;

  return new Response(xml, {
    headers: {
      "Content-Type": "application/xml; charset=utf-8",
      "Cache-Control": "public, max-age=300",
    },
  });
};
