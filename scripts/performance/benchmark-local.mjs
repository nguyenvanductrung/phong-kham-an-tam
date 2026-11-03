import { readFile } from "node:fs/promises";
import { spawn } from "node:child_process";
import { fileURLToPath } from "node:url";

const baseUrl = process.env.PERF_WEB_URL ?? "http://localhost:3000";
const email = process.env.PERF_LOGIN_EMAIL ?? "receptionist@antam.local";
const password = process.env.DEMO_LOGIN_PASSWORD ?? await readDemoPassword();
const origin = new URL(baseUrl).origin;
const headers = { "content-type": "application/json", origin };
let cookie = "";
let csrfToken = "";

const login = await fetch(`${baseUrl}/api/auth/login`, {
  method: "POST",
  headers,
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

let exitCode = 1;
try {
  exitCode = await runBenchmark(cookie);
} finally {
  await fetch(`${baseUrl}/api/auth/logout`, {
    method: "POST",
    headers: { cookie, "x-csrf-token": csrfToken, origin },
    signal: AbortSignal.timeout(5000),
  }).catch(() => undefined);
}
process.exitCode = exitCode;

function runBenchmark(sessionCookie) {
  return new Promise((resolve, reject) => {
    const child = spawn(process.execPath, [fileURLToPath(new URL("./benchmark.mjs", import.meta.url))], {
      stdio: "inherit",
      env: {
        ...process.env,
        BENCHMARK_URL: process.env.BENCHMARK_URL ?? `${baseUrl}/api/bff/api/v1/patients?page=0&size=20`,
        BENCHMARK_COOKIE: sessionCookie,
      },
    });
    child.once("error", reject);
    child.once("exit", (code, signal) => resolve(signal ? 1 : (code ?? 1)));
  });
}

async function readDemoPassword() {
  const env = await readFile(new URL("../../.env", import.meta.url), "utf8");
  const passwordLine = env.split(/\r?\n/).find((line) => line.startsWith("DEMO_LOGIN_PASSWORD="));
  if (!passwordLine) throw new Error("Set DEMO_LOGIN_PASSWORD or run scripts/bootstrap.ps1 first.");
  return passwordLine.slice("DEMO_LOGIN_PASSWORD=".length).trim();
}
