import { sortPosts, type PostSummary } from "@/lib/blog-api";
import { postFilter } from "./postFilter";

/**
 * Returns posts that are eligible to be shown to users, sorted by “last updated”
 * descending (uses `modDatetime` when present, otherwise `pubDatetime`).
 *
 * Note: filtering respects drafts and scheduled posts via `postFilter()`.
 */
export function getSortedPosts(posts: PostSummary[]) {
  return sortPosts(posts.filter(postFilter));
}
