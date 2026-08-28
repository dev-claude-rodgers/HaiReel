import type { Env } from "./types";
import { verifyRequest, getDeviceId } from "./auth";
import { checkAndIncrement } from "./rateLimit";
import { isAllowedRoute, forwardToGoogle } from "./proxyGoogle";

function jsonError(status: number, error: string): Response {
  return new Response(JSON.stringify({ error }), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8" },
  });
}

export default {
  async fetch(req: Request, env: Env): Promise<Response> {
    const url = new URL(req.url);

    if (req.method !== "GET") return jsonError(405, "method_not_allowed");
    if (!isAllowedRoute(url.pathname)) return jsonError(404, "not_found");

    const auth = await verifyRequest(req, env);
    if (!auth.ok) return jsonError(auth.status, auth.error ?? "unauthorized");

    const deviceId = getDeviceId(req);
    const withinQuota = await checkAndIncrement(env, deviceId);
    if (!withinQuota) return jsonError(429, "rate_limited");

    return forwardToGoogle(url.pathname, url.searchParams, env);
  },
};
