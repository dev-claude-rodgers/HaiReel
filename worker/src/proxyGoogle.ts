import type { Env } from "./types";

const GOOGLE_BASE = "https://maps.googleapis.com";

const ALLOWED_ROUTES: Record<string, string> = {
  "/maps/api/geocode/json": "/maps/api/geocode/json",
  "/maps/api/place/textsearch/json": "/maps/api/place/textsearch/json",
  "/maps/api/place/nearbysearch/json": "/maps/api/place/nearbysearch/json",
};

const ALLOWED_QUERY_PARAMS = new Set([
  "address",
  "latlng",
  "location",
  "radius",
  "query",
  "language",
  "region",
  "bounds",
]);

export function isAllowedRoute(pathname: string): boolean {
  return pathname in ALLOWED_ROUTES;
}

export async function forwardToGoogle(pathname: string, search: URLSearchParams, env: Env): Promise<Response> {
  const target = new URL(GOOGLE_BASE + ALLOWED_ROUTES[pathname]);
  for (const [key, value] of search.entries()) {
    if (ALLOWED_QUERY_PARAMS.has(key)) target.searchParams.set(key, value);
  }
  // クライアント指定の key は無視し、常にサーバー側のキーを使う
  target.searchParams.set("key", env.GOOGLE_API_KEY);

  const googleRes = await fetch(target.toString(), { method: "GET" });
  const body = await googleRes.text();
  return new Response(body, {
    status: googleRes.status,
    headers: { "Content-Type": "application/json; charset=utf-8" },
  });
}
