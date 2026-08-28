import type { Env } from "./types";

export interface AuthResult {
  ok: boolean;
  status: number;
  error?: string;
}

const MAX_CLOCK_SKEW_SECONDS = 5 * 60;

async function hmacSha256Hex(secret: string, message: string): Promise<string> {
  const enc = new TextEncoder();
  const key = await crypto.subtle.importKey(
    "raw",
    enc.encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"]
  );
  const sig = await crypto.subtle.sign("HMAC", key, enc.encode(message));
  return [...new Uint8Array(sig)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

function timingSafeEqual(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

export async function verifyRequest(req: Request, env: Env): Promise<AuthResult> {
  const deviceId = req.headers.get("X-HaiReel-Device-Id");
  const timestamp = req.headers.get("X-HaiReel-Timestamp");
  const entitled = req.headers.get("X-HaiReel-Entitled");
  const signature = req.headers.get("X-HaiReel-Signature");
  const pkg = req.headers.get("X-HaiReel-Package");

  if (!deviceId || !timestamp || !entitled || !signature || !pkg) {
    return { ok: false, status: 401, error: "missing_headers" };
  }

  const ts = Number(timestamp);
  if (!Number.isFinite(ts)) {
    return { ok: false, status: 401, error: "invalid_timestamp" };
  }
  const nowSeconds = Math.floor(Date.now() / 1000);
  if (Math.abs(nowSeconds - ts) > MAX_CLOCK_SKEW_SECONDS) {
    return { ok: false, status: 401, error: "timestamp_out_of_range" };
  }

  const expected = await hmacSha256Hex(env.PROXY_CLIENT_SECRET, `${deviceId}|${timestamp}|${entitled}`);
  if (!timingSafeEqual(expected, signature)) {
    return { ok: false, status: 401, error: "bad_signature" };
  }

  const allowedPackages = env.ALLOWED_PACKAGES.split(",").map((s) => s.trim());
  if (!allowedPackages.includes(pkg)) {
    return { ok: false, status: 403, error: "package_not_allowed" };
  }

  if (entitled !== "1") {
    return { ok: false, status: 403, error: "subscription_required" };
  }

  return { ok: true, status: 200 };
}

export function getDeviceId(req: Request): string {
  return req.headers.get("X-HaiReel-Device-Id") ?? "unknown";
}
