import type { APIRoute } from "astro";

export const prerender = false;

const REQUEST_HOP_BY_HOP_HEADERS = new Set([
  "connection",
  "content-length",
  "host",
  "keep-alive",
  "proxy-authenticate",
  "proxy-authorization",
  "te",
  "trailer",
  "transfer-encoding",
  "upgrade",
]);

const RESPONSE_HOP_BY_HOP_HEADERS = new Set([
  "connection",
  "content-length",
  "content-encoding",
  "keep-alive",
  "transfer-encoding",
  "upgrade",
]);

function apiBaseUrl() {
  return (process.env.API_BASE_URL ?? "http://127.0.0.1:8081").replace(/\/+$/, "");
}

function copyRequestHeaders(request: Request) {
  const headers = new Headers();
  for (const [name, value] of request.headers) {
    if (!REQUEST_HOP_BY_HOP_HEADERS.has(name.toLowerCase())) headers.set(name, value);
  }
  return headers;
}

function copyResponseHeaders(response: Response) {
  const headers = new Headers();
  for (const [name, value] of response.headers) {
    const normalizedName = name.toLowerCase();
    if (!RESPONSE_HOP_BY_HOP_HEADERS.has(normalizedName) && normalizedName !== "set-cookie") {
      headers.set(name, value);
    }
  }

  const getSetCookie = (response.headers as Headers & { getSetCookie?: () => string[] }).getSetCookie;
  for (const cookie of getSetCookie?.call(response.headers) ?? []) headers.append("set-cookie", cookie);
  return headers;
}

export const ALL: APIRoute = async ({ request, params, url }) => {
  const apiPath = params.path ? `/api/${params.path}` : "/api";
  if (apiPath !== "/api/v1" && !apiPath.startsWith("/api/v1/")) {
    return new Response("Not found", { status: 404 });
  }

  const targetUrl = `${apiBaseUrl()}${apiPath}${url.search}`;
  const body = request.method === "GET" || request.method === "HEAD" ? undefined : await request.arrayBuffer();

  let upstream: Response;
  try {
    upstream = await fetch(targetUrl, {
      method: request.method,
      headers: copyRequestHeaders(request),
      body,
      redirect: "manual",
    });
  } catch {
    return new Response(
      JSON.stringify({ title: "API unavailable", detail: "The application API is temporarily unavailable." }),
      { status: 502, headers: { "content-type": "application/problem+json" } }
    );
  }

  return new Response(request.method === "HEAD" ? null : upstream.body, {
    status: upstream.status,
    statusText: upstream.statusText,
    headers: copyResponseHeaders(upstream),
  });
};
