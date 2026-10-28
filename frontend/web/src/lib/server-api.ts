import { getSession, rotateSession, type UserSession } from "@/lib/session";

const gateway = process.env.GATEWAY_URL ?? "http://127.0.0.1:8080";

export async function serverApi(path: string, init: RequestInit = {}) {
  const stored = await getSession();
  if (!stored) return null;
  let session: UserSession = stored.value;
  if (session.expiresAt < Date.now() + 20_000) {
    try {
      const rotated = await rotateSession(stored.id, session);
      if (!rotated) return null;
      session = rotated;
    } catch { return null; }
  }
  try {
    return await fetch(`${gateway}${path}`, {
      ...init,
      headers: { ...(init.headers ?? {}), authorization: `Bearer ${session.accessToken}` },
      cache: "no-store",
    });
  } catch { return null; }
}

