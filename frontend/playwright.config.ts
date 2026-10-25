import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  forbidOnly: Boolean(process.env.CI),
  failOnFlakyTests: Boolean(process.env.CI),
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: process.env.CI ? "github" : "list",
  use: {
    baseURL: process.env.E2E_BASE_URL ?? "http://localhost:13000",
    trace: "retain-on-failure",
  },
  projects: [
    { name: "desktop", use: { ...devices["Desktop Chrome"] } },
    { name: "mobile", use: { ...devices["iPhone 13"], defaultBrowserType: "chromium" } },
    { name: "firefox", use: { ...devices["Desktop Firefox"] } },
    { name: "webkit", use: { ...devices["Desktop Safari"] } },
    { name: "mobile-safari", use: { ...devices["iPhone 13"] } },
  ],
  webServer:
    process.env.E2E_EXTERNAL_SERVICES === "true"
      ? undefined
      : [
          {
            command: "mvn -B -ntp spring-boot:run",
            cwd: "../backend",
            url: "http://localhost:18080/actuator/health",
            reuseExistingServer: !process.env.CI,
            timeout: 180000,
            env: {
              DATABASE_URL:
                "jdbc:h2:mem:e2e;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
              MODEL_ENABLED: "false",
              PORT: "18080",
              ALLOWED_ORIGINS: "http://localhost:13000",
            },
          },
          {
            command: process.env.CI ? "npm run start" : "npm run dev -- --port 13000",
            url: "http://localhost:13000",
            reuseExistingServer: !process.env.CI,
            timeout: 120000,
            env: { API_BASE_URL: "http://localhost:18080", PORT: "13000", HOSTNAME: "127.0.0.1" },
          },
        ],
});
