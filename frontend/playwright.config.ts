import { defineConfig, devices } from '@playwright/test'
export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  reporter: 'list',
  use: {
    baseURL: process.env.SAR_E2E_URL || 'http://127.0.0.1:5173',
    trace: 'retain-on-failure',
    launchOptions: process.env.SAR_BROWSER_EXECUTABLE ? { executablePath: process.env.SAR_BROWSER_EXECUTABLE } : {},
  },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'], viewport: { width: 1366, height: 900 } } },
    { name: 'mobile', use: { ...devices['Desktop Chrome'], viewport: { width: 390, height: 844 } } },
  ],
})
