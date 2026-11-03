import { readFile } from "node:fs/promises";

const baseUrl = process.env.PERF_WEB_URL ?? "http://localhost:3000";
const password = process.env.DEMO_LOGIN_PASSWORD ?? await readDemoPassword();
const email = process.env.PERF_LOGIN_EMAIL ?? "receptionist@antam.local";
const origin = new URL(baseUrl).origin;
const jsonHeaders = { "content-type": "application/json", origin };
let cookie = "";
let csrfToken = "";

const health = await fetch(`${baseUrl}/api/health`, { signal: AbortSignal.timeout(5000) });
if (!health.ok) throw new Error(`Web health failed with ${health.status}.`);

const login = await fetch(`${baseUrl}/api/auth/login`, {
  method: "POST",
  headers: jsonHeaders,
  body: JSON.stringify({ email, password }),
  signal: AbortSignal.timeout(10_000),
});
if (!login.ok) throw new Error(`Demo login failed with ${login.status}. Check the local stack and .env.`);

const loginInfo = await login.json();
csrfToken = loginInfo.csrfToken;
const sessionCookie = login.headers.getSetCookie().find((value) =>
  value.startsWith(`${process.env.SESSION_COOKIE_NAME ?? "antam_session"}=`),
);
if (!csrfToken || !sessionCookie) throw new Error("Login did not create an authenticated browser session.");
cookie = sessionCookie.split(";", 1)[0];

try {
  const session = await fetch(`${baseUrl}/api/auth/session`, { headers: { cookie }, signal: AbortSignal.timeout(5000) });
  const sessionInfo = await session.json();
  if (!session.ok || !sessionInfo.authenticated) throw new Error(`Session validation failed with ${session.status}.`);

  const patients = await fetch(`${baseUrl}/api/bff/api/v1/patients?page=0&size=2`, {
    headers: { cookie }, signal: AbortSignal.timeout(10_000),
  });
  if (!patients.ok) throw new Error(`Patient read through BFF failed with ${patients.status}.`);
  const patientPage = await patients.json();
  if (!Array.isArray(patientPage.items) || !Number.isInteger(patientPage.size)) {
    throw new Error("Patient endpoint returned an unexpected page shape.");
  }

  console.log(JSON.stringify({
    webHealth: health.status,
    login: login.status,
    session: session.status,
    patientReadThroughBff: patients.status,
    returnedPageSize: patientPage.items.length,
  }, null, 2));
} finally {
  await fetch(`${baseUrl}/api/auth/logout`, {
    method: "POST",
    headers: { cookie, "x-csrf-token": csrfToken, origin },
    signal: AbortSignal.timeout(5000),
  }).catch(() => undefined);
}

async function readDemoPassword() {
  const env = await readFile(new URL("../../.env", import.meta.url), "utf8");
  const passwordLine = env.split(/\r?\n/).find((line) => line.startsWith("DEMO_LOGIN_PASSWORD="));
  if (!passwordLine) throw new Error("Set DEMO_LOGIN_PASSWORD or run scripts/bootstrap.ps1 first.");
  return passwordLine.slice("DEMO_LOGIN_PASSWORD=".length).trim();
}
