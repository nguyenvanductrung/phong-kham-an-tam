import { randomBytes } from "node:crypto";
import { cookies } from "next/headers";
import { redis } from "@/lib/redis";

export type UserSession = {
  userId: string;
  fullName: string;
  roles: string[];
  accessToken: string;
  refreshToken: string;
  expiresAt: number;
  csrfToken: string;
};

const COOKIE = process.env.SESSION_COOKIE_NAME ?? "antam_session";
const TTL_SECONDS = 60 * 60 * 24 * 7;
const gateway = process.env.GATEWAY_URL ?? "http://127.0.0.1:8080";
const RELEASE_LOCK = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";

export class RefreshUnavailable extends Error {}

export function newOpaqueId() {
  return randomBytes(32).toString("base64url");
}

export async function getSession(): Promise<{ id: string; value: UserSession } | null> {
  const jar = await cookies();
  const id = jar.get(COOKIE)?.value;
  if (!id || !/^[A-Za-z0-9_-]{40,50}$/.test(id)) return null;
  const stored = await redis.get(`session:${id}`);
  if (!stored) return null;
  try {
    const value = JSON.parse(stored) as UserSession;
    if (!value.accessToken || !value.refreshToken || !value.csrfToken) return null;
    return { id, value };
  } catch {
    await redis.del(`session:${id}`);
    return null;
  }
}

export async function saveSession(id: string, value: UserSession) {
  await redis.set(`session:${id}`, JSON.stringify(value), "EX", TTL_SECONDS);
}

export async function deleteSession(id: string) {
  await redis.del(`session:${id}`);
}

export async function rotateSession(id: string, current: UserSession): Promise<UserSession | null> {
  const sessionKey = `session:${id}`;
  const lockKey = `session-refresh:${id}`;
  const lockId = randomBytes(16).toString("hex");
  const deadline = Date.now() + 10_000;

  while (Date.now() < deadline) {
    const acquired = await redis.set(lockKey, lockId, "PX", 30_000, "NX");
    if (acquired === "OK") {
      try {
        const latestRaw = await redis.get(sessionKey);
        if (!latestRaw) return null;
        const latest = JSON.parse(latestRaw) as UserSession;
        if (latest.refreshToken !== current.refreshToken) return latest;

        let response: Response;
        try {
          response = await fetch(`${gateway}/api/v1/auth/refresh`, {
            method: "POST",
            headers: { "content-type": "application/json" },
            body: JSON.stringify({ refreshToken: latest.refreshToken }),
            cache: "no-store",
          });
        } catch {
          throw new RefreshUnavailable();
        }
        if (response.status >= 500) throw new RefreshUnavailable();
        if (!response.ok) return null;
        const tokens = await response.json() as { accessToken: string; refreshToken: string; expiresIn: number };
        const updated: UserSession = {
          ...latest,
          accessToken: tokens.accessToken,
          refreshToken: tokens.refreshToken,
          expiresAt: Date.now() + tokens.expiresIn * 1000,
        };
        await redis.set(sessionKey, JSON.stringify(updated), "EX", TTL_SECONDS);
        return updated;
      } finally {
        await redis.eval(RELEASE_LOCK, 1, lockKey, lockId).catch(() => undefined);
      }
    }

    await new Promise((resolve) => setTimeout(resolve, 100));
    const latestRaw = await redis.get(sessionKey);
    if (!latestRaw) return null;
    const latest = JSON.parse(latestRaw) as UserSession;
    if (latest.refreshToken !== current.refreshToken) return latest;
  }
  throw new RefreshUnavailable();
}

export const sessionCookieName = COOKIE;

