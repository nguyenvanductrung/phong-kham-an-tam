import { writeFile } from "node:fs/promises";

const url = process.env.BENCHMARK_URL;
if (!url) {
  console.error("Đặt BENCHMARK_URL tới một GET endpoint đọc an toàn trước khi chạy.");
  process.exit(2);
}

const concurrency = positiveInt("BENCHMARK_CONCURRENCY", 5);
const durationSeconds = positiveInt("BENCHMARK_DURATION_SECONDS", 60);
const timeoutMs = positiveInt("BENCHMARK_TIMEOUT_MS", 10_000);
const outputPath = process.env.BENCHMARK_OUTPUT ?? "";
const extraHeaders = {};
if (process.env.BENCHMARK_AUTHORIZATION) {
  extraHeaders.authorization = process.env.BENCHMARK_AUTHORIZATION;
}
if (process.env.BENCHMARK_COOKIE) {
  extraHeaders.cookie = process.env.BENCHMARK_COOKIE;
}

const startedAt = Date.now();
const deadline = startedAt + durationSeconds * 1000;
const durations = [];
const statusCounts = {};
let errors = 0;

await Promise.all(Array.from({ length: concurrency }, async () => {
  while (Date.now() < deadline) {
    const requestStarted = performance.now();
    let status = 0;
    let error = "";
    try {
      const response = await fetch(url, {
        method: "GET",
        headers: extraHeaders,
        signal: AbortSignal.timeout(timeoutMs),
        redirect: "manual",
      });
      status = response.status;
      await response.body?.cancel();
    } catch (cause) {
      error = cause instanceof Error ? cause.name : "RequestError";
    }
    durations.push(performance.now() - requestStarted);
    const statusKey = status || "network_error";
    statusCounts[statusKey] = (statusCounts[statusKey] ?? 0) + 1;
    if (error || status < 200 || status >= 400) errors += 1;
  }
}));

durations.sort((a, b) => a - b);
const quantile = (fraction) => durations.length
  ? Number(durations[Math.min(durations.length - 1, Math.ceil(fraction * durations.length) - 1)].toFixed(1))
  : null;
const elapsedSeconds = (Date.now() - startedAt) / 1000;
const report = {
  url: new URL(url).origin + new URL(url).pathname,
  concurrency,
  elapsedSeconds: Number(elapsedSeconds.toFixed(2)),
  requests: durations.length,
  requestsPerSecond: Number((durations.length / elapsedSeconds).toFixed(2)),
  errors,
  statusCounts,
  latencyMs: { p50: quantile(0.5), p95: quantile(0.95), p99: quantile(0.99), max: quantile(1) },
};

console.log(JSON.stringify(report, null, 2));
if (outputPath) {
  await writeFile(outputPath, JSON.stringify(report, null, 2), { encoding: "utf8", flag: "wx" });
  console.log(`Đã lưu mẫu đo vào ${outputPath}`);
}
if (errors) process.exitCode = 1;

function positiveInt(name, fallback) {
  const raw = process.env[name];
  if (!raw) return fallback;
  const value = Number(raw);
  if (!Number.isInteger(value) || value <= 0) {
    console.error(`${name} phải là số nguyên dương.`);
    process.exit(2);
  }
  return value;
}
