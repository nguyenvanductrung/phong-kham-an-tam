import { NextResponse } from "next/server";
import { deleteSession, getSession, sessionCookieName } from "@/lib/session";
import { isSameOriginRequest } from "@/lib/same-origin";

export const dynamic = "force-dynamic";
const gateway = process.env.GATEWAY_URL ?? "http://127.0.0.1:8080";

export async function POST(request: Request) {
  const stored = await getSession();
  if (!stored) return clearCookie(NextResponse.json({ ok: true }));
  if (!isSameOriginRequest(request) || request.headers.get("x-csrf-token") !== stored.value.csrfToken)
    return NextResponse.json({ errorCode: "CSRF_REJECTED", detail: "Yêu cầu không hợp lệ." }, { status: 403 });
  await fetch(`${gateway}/api/v1/auth/logout`, {
    method: "POST", headers: { "content-type": "application/json" },
    body: JSON.stringify({ refreshToken: stored.value.refreshToken }), cache: "no-store",
  }).catch(() => undefined);
  await deleteSession(stored.id);
  return clearCookie(NextResponse.json({ ok: true }));
}

function clearCookie(response: NextResponse) {
  response.cookies.set(sessionCookieName, "", { httpOnly: true, secure: process.env.NODE_ENV === "production", sameSite: "strict", path: "/", maxAge: 0 });
  response.headers.set("cache-control", "no-store");
  return response;
}

