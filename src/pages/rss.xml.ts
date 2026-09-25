import rss from "@astrojs/rss";
import { getAllPosts, publicPostUrl, sortPosts } from "@/lib/blog-api";
import config from "@/config";

export async function GET() {
  const sortedPosts = sortPosts(await getAllPosts());

  return rss({
    title: config.site.title,
    description: config.site.description,
    site: config.site.url,
    items: sortedPosts.map(post => ({
      link: publicPostUrl(post.slug, config.site.lang),
      title: post.title,
      description: post.description,
      pubDate: new Date(post.modDatetime ?? post.pubDatetime),
    })),
  });
}
