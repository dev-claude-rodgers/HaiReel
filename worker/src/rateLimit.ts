import type { Env } from "./types";

const SECONDS_PER_DAY = 86400;

function todayKey(deviceId: string): string {
  const dateStr = new Date().toISOString().slice(0, 10); // yyyy-mm-dd (UTC)
  return `rl:${deviceId}:${dateStr}`;
}

/** デバイスの日次リクエスト数がクォータ以内なら加算して true、超過していれば false を返す */
export async function checkAndIncrement(env: Env, deviceId: string): Promise<boolean> {
  const quota = Number(env.DAILY_QUOTA_PER_DEVICE) || 300;
  const key = todayKey(deviceId);
  const current = Number((await env.RATE_LIMIT_KV.get(key)) ?? "0");

  if (current >= quota) return false;

  await env.RATE_LIMIT_KV.put(key, String(current + 1), { expirationTtl: SECONDS_PER_DAY });
  return true;
}
