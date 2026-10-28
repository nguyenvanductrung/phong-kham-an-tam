import { NextResponse } from "next/server";
import { deleteSession, getSession, rotateSession, RefreshUnavailable } from "@/lib/session";
import { isSameOriginRequest } from "@/lib/same-origin";

export const dynamic = "force-dynamic";
const gateway = process.env.GATEWAY_URL ?? "http://127.0.0.1:8080";
type RouteContext = { params: Promise<{ path: string[] }> };

async function forward(request: Request, context: RouteContext) {
  const stored = await getSession();
  if (!stored) return problem(401, "SESSION_EXPIRED", "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.");
  const method = request.method.toUpperCase();
  const mutation = !["GET", "HEAD", "OPTIONS"].includes(method);
  if (mutation && (!isSameOriginRequest(request) || request.headers.get("x-csrf-token") !== stored.value.csrfToken))
    return problem(403, "CSRF_REJECTED", "Yêu cầu không hợp lệ hoặc phiên bảo mật đã thay đổi.");

  const { path } = await context.params;
  if (!path.length || path.some((part) => part === "." || part === ".." || part.includes("\\"))) return problem(400, "INVALID_PATH", "Đường dẫn không hợp lệ.");
  const target = `${gateway}/${path.map(encodeURIComponent).join("/")}${new URL(request.url).search}`;
  const headers = new Headers();
  for (const name of ["accept", "content-type", "idempotency-key", "x-correlation-id"]) {
    const value = request.headers.get(name);
    if (value) headers.set(name, value);
  }
  let session = stored.value;
  if (session.expiresAt < Date.now() + 20_000) {
    let rotated;
    try { rotated = await rotateSession(stored.id, session); }
    catch (error) { if (error instanceof RefreshUnavailable) return problem(503, "AUTH_SERVICE_UNAVAILABLE", "Dịch vụ đăng nhập tạm thời chưa sẵn sàng."); throw error; }
    if (!rotated) return expire(stored.id);
    session = rotated;
  }
  const body = mutation ? await request.arrayBuffer() : undefined;
  let upstream: Response;
  try { upstream = await send(target, method, headers, session.accessToken, body); }
  catch { return problem(503, "UPSTREAM_UNAVAILABLE", "Không thể kết nối hệ thống. Vui lòng thử lại."); }
  if (upstream.status === 401) {
    let rotated;
    try { rotated = await rotateSession(stored.id, session); }
    catch (error) { if (error instanceof RefreshUnavailable) return problem(503, "AUTH_SERVICE_UNAVAILABLE", "Dịch vụ đăng nhập tạm thời chưa sẵn sàng."); throw error; }
    if (!rotated) return expire(stored.id);
    session = rotated;
    try { upstream = await send(target, method, headers, session.accessToken, body); }
    catch { return problem(503, "UPSTREAM_UNAVAILABLE", "Không thể kết nối hệ thống. Vui lòng thử lại."); }
  }
  const responseHeaders = new Headers({ "cache-control": "no-store" });
  for (const name of ["content-type", "content-language", "x-correlation-id"]) {
    const value = upstream.headers.get(name);
    if (value) responseHeaders.set(name, value);
  }
  return new Response(upstream.body, { status: upstream.status, headers: responseHeaders });
}

async function send(url: string, method: string, headers: Headers, token: string, body?: ArrayBuffer) {
  const forwardHeaders = new Headers(headers);
  forwardHeaders.set("authorization", `Bearer ${token}`);
  return fetch(url, { method, headers: forwardHeaders, body, cache: "no-store", redirect: "manual" });
}

async function expire(id: string) {
  await deleteSession(id);
  const response = problem(401, "SESSION_EXPIRED", "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.");
  response.headers.append("set-cookie", `${process.env.SESSION_COOKIE_NAME ?? "antam_session"}=; Path=/; HttpOnly; SameSite=Strict; Max-Age=0${process.env.NODE_ENV === "production" ? "; Secure" : ""}`);
  return response;
}
function problem(status: number, errorCode: string, detail: string) {
  return NextResponse.json({ type: "about:blank", title: status === 401 ? "Unauthorized" : "Request failed", status, errorCode, detail }, { status, headers: { "cache-control": "no-store" } });
}

export const GET = forward;
export const POST = forward;
export const PUT = forward;
export const PATCH = forward;
export const DELETE = forward;

