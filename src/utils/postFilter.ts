import type { PostSummary } from "@/lib/blog-api";
import config from "@/config";

/**
 * Determines whether a post is eligible to be listed/rendered.
 *
 * - Excludes drafts always
 * - In production, excludes scheduled posts until `pubDatetime` minus the configured margin
 * - In dev, always shows non-draft posts to make authoring easier
 */
export function postFilter({ pubDatetime }: PostSummary) {
  const isPublishTimePassed =
    Date.now() >
    new Date(pubDatetime).getTime() - config.posts.scheduledPostMargin;
  return import.meta.env.DEV || isPublishTimePassed;
}
