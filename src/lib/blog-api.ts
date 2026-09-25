import { getRelativeLocaleUrl } from "astro:i18n";

export type PostSummary = {
  slug: string;
  title: string;
  description: string;
  author: string;
  pubDatetime: string;
  modDatetime?: string | null;
  timezone?: string | null;
  tags: string[];
  featured: boolean;
  coverImage?: string | null;
  canonicalURL?: string | null;
  ogImage?: string | null;
  hideEditPost: boolean;
};

export type PostDetail = PostSummary & {
  contentMarkdown: string;
};

export type ApiPage<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
};

export type PostTag = {
  slug: string;
  name: string;
  postCount: number;
};

const apiBaseUrl = (
  process.env.API_BASE_URL ?? "http://127.0.0.1:8081"
).replace(/\/+$/, "");

export class BlogApiError extends Error {
  constructor(
    readonly status: number,
    path: string
  ) {
    super(`Blog API request failed (${status}): ${path}`);
  }
}

async function getJson<T>(path: string): Promise<T> {
  const response = await fetch(`${apiBaseUrl}${path}`, {
    headers: { Accept: "application/json" },
    cache: "no-store",
  });

  if (!response.ok) {
    throw new BlogApiError(response.status, path);
  }

  return (await response.json()) as T;
}

export function getPosts(page = 0, size = 10): Promise<ApiPage<PostSummary>> {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
  });
  return getJson(`/api/v1/posts?${params.toString()}`);
}

export function searchPosts(
  query: string,
  page = 0,
  size = 10
): Promise<ApiPage<PostSummary>> {
  const params = new URLSearchParams({
    q: query,
    page: String(page),
    size: String(size),
  });
  return getJson(`/api/v1/search?${params.toString()}`);
}

export function getPost(slug: string): Promise<PostDetail> {
  const pathSlug = slug.split("/").map(encodeURIComponent).join("/");
  return getJson(`/api/v1/posts/${pathSlug}`);
}

export function getTags(): Promise<PostTag[]> {
  return getJson("/api/v1/tags");
}

export function getPostsByTag(
  tagSlug: string,
  page = 0,
  size = 10
): Promise<ApiPage<PostSummary>> {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
  });
  return getJson(
    `/api/v1/tags/${encodeURIComponent(tagSlug)}/posts?${params.toString()}`
  );
}

export async function getAllPosts(size = 100): Promise<PostSummary[]> {
  const firstPage = await getPosts(0, size);
  const pages: ApiPage<PostSummary>[] = [firstPage];

  if (firstPage.totalPages > 100) {
    throw new Error("Refusing to load more than 10,000 public posts at once.");
  }

  for (let page = 1; page < firstPage.totalPages; page += 1) {
    pages.push(await getPosts(page, size));
  }

  return pages.flatMap(page => page.content);
}

export function publicPostUrl(slug: string, locale?: string): string {
  const pathSlug = slug
    .split("/")
    .map(segment => encodeURIComponent(segment))
    .join("/");
  return getRelativeLocaleUrl(locale ?? "en", `posts/${pathSlug}`);
}

export function sortPosts(posts: PostSummary[]): PostSummary[] {
  return [...posts].sort((first, second) => {
    const firstDate = Date.parse(first.modDatetime ?? first.pubDatetime);
    const secondDate = Date.parse(second.modDatetime ?? second.pubDatetime);
    return secondDate - firstDate;
  });
}

export type PaginationState = {
  currentPage: number;
  lastPage: number;
  url: { prev?: string; next?: string };
};

export function createPagination(
  currentPage: number,
  lastPage: number,
  pathForPage: (page: number) => string
): PaginationState {
  return {
    currentPage,
    lastPage,
    url: {
      ...(currentPage > 1 ? { prev: pathForPage(currentPage - 1) } : {}),
      ...(currentPage < lastPage ? { next: pathForPage(currentPage + 1) } : {}),
    },
  };
}
