import { randomBytes } from "node:crypto";
import { NextResponse } from "next/server";
import { newOpaqueId, saveSession, sessionCookieName, type UserSession } from "@/lib/session";
import { isSameOriginRequest } from "@/lib/same-origin";

export const dynamic = "force-dynamic";
const gateway = process.env.GATEWAY_URL ?? "http://127.0.0.1:8080";

export async function POST(request: Request) {
  if (!isSameOriginRequest(request)) return NextResponse.json({ errorCode: "CSRF_REJECTED", detail: "Yêu cầu không hợp lệ." }, { status: 403 });
  const body = await request.json().catch(() => null);
  if (!body || typeof body.email !== "string" || typeof body.password !== "string")
    return NextResponse.json({ errorCode: "VALIDATION_FAILED", detail: "Vui lòng nhập email và mật khẩu." }, { status: 400 });

  let upstream: Response;
  try {
    upstream = await fetch(`${gateway}/api/v1/auth/login`, {
      method: "POST", headers: { "content-type": "application/json" },
      body: JSON.stringify({ email: body.email, password: body.password }), cache: "no-store",
    });
  } catch {
    return NextResponse.json({ errorCode: "SERVICE_UNAVAILABLE", detail: "Hệ thống đăng nhập chưa sẵn sàng." }, { status: 503 });
  }
  if (!upstream.ok) {
    const problem = await upstream.json().catch(() => ({ detail: "Email hoặc mật khẩu không đúng." }));
    return NextResponse.json(problem, { status: upstream.status, headers: { "cache-control": "no-store" } });
  }
  const tokens = await upstream.json();
  const id = newOpaqueId();
  const session: UserSession = {
    userId: tokens.userId,
    fullName: tokens.fullName,
    roles: tokens.roles,
    accessToken: tokens.accessToken,
    refreshToken: tokens.refreshToken,
    expiresAt: Date.now() + tokens.expiresIn * 1000,
    csrfToken: randomBytes(32).toString("base64url"),
  };
  try {
    await saveSession(id, session);
  } catch {
    return NextResponse.json(
      { errorCode: "SESSION_STORE_UNAVAILABLE", detail: "Không thể tạo phiên đăng nhập. Vui lòng thử lại." },
      { status: 503, headers: { "cache-control": "no-store" } },
    );
  }
  const response = NextResponse.json({ userId: session.userId, fullName: session.fullName, roles: session.roles, csrfToken: session.csrfToken });
  response.cookies.set(sessionCookieName, id, {
    httpOnly: true, secure: process.env.NODE_ENV === "production", sameSite: "strict", path: "/", maxAge: 60 * 60 * 24 * 7,
  });
  response.headers.set("cache-control", "no-store");
  return response;
}

