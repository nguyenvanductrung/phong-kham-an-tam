import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? "github" : "list",
  use: {
    baseURL: process.env.PLAYWRIGHT_BASE_URL ?? "http://127.0.0.1:3000",
    trace: "retain-on-failure",
    ...(process.env.PLAYWRIGHT_CHROMIUM_CHANNEL
      ? { launchOptions: { channel: process.env.PLAYWRIGHT_CHROMIUM_CHANNEL as "chrome" | "msedge" } }
      : {}),
    ...devices["Desktop Chrome"],
  },
});

