import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: process.env.CI ? "github" : "list",
  use: { baseURL: "http://localhost:13000", trace: "retain-on-failure" },
  projects: [
    { name: "desktop", use: { ...devices["Desktop Chrome"] } },
    { name: "mobile", use: { ...devices["iPhone 13"], defaultBrowserType: "chromium" } },
  ],
  webServer: [
    {
      command: "mvn -B -ntp spring-boot:run",
      cwd: "../backend",
      url: "http://localhost:18080/actuator/health",
      reuseExistingServer: !process.env.CI,
      timeout: 180000,
      env: {
        DATABASE_URL: "jdbc:h2:mem:e2e;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        MODEL_ENABLED: "false",
        PORT: "18080",
        ALLOWED_ORIGINS: "http://localhost:13000",
      },
    },
    {
      command: process.env.CI ? "npm run start -- --port 13000" : "npm run dev -- --port 13000",
      url: "http://localhost:13000",
      reuseExistingServer: !process.env.CI,
      timeout: 120000,
      env: { API_BASE_URL: "http://localhost:18080" },
    },
  ],
});
