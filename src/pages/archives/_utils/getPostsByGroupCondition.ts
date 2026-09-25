import type { PostSummary } from "@/lib/blog-api";

type GroupKey = string | number | symbol;
type GroupFunction<T> = (item: T, index?: number) => GroupKey;

export function getPostsByGroupCondition(
  posts: PostSummary[],
  groupFunction: GroupFunction<PostSummary>
) {
  const result: Record<GroupKey, PostSummary[]> = {};

  for (let i = 0; i < posts.length; i++) {
    const item = posts[i];
    const groupKey = groupFunction(item, i);

    if (!result[groupKey]) {
      result[groupKey] = [];
    }

    result[groupKey].push(item);
  }

  return result;
}
