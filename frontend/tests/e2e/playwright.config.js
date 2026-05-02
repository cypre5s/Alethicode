// Alethicode E2E 测试 Playwright 配置
// 运行：npx playwright test --config tests/e2e/playwright.config.js
const fs = require('fs')
const path = require('path')

const localPlaywrightLibDir = path.join(process.env.HOME || '', '.local', 'pw-libs', 'root', 'usr', 'lib', 'x86_64-linux-gnu')
if (fs.existsSync(localPlaywrightLibDir)) {
  const currentLdLibraryPath = process.env.LD_LIBRARY_PATH || ''
  const pathEntries = currentLdLibraryPath.split(':').filter(Boolean)
  if (!pathEntries.includes(localPlaywrightLibDir)) {
    process.env.LD_LIBRARY_PATH = currentLdLibraryPath
      ? `${localPlaywrightLibDir}:${currentLdLibraryPath}`
      : localPlaywrightLibDir
  }
}

/** @type {import('@playwright/test').PlaywrightTestConfig} */
module.exports = {
  testDir: '.',
  timeout: 60000,
  retries: 1,
  use: {
    baseURL: process.env.BASE_URL || 'http://127.0.0.1:80',
    headless: true,
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { browserName: 'chromium' },
    },
  ],
  reporter: [
    ['list'],
    ['json', { outputFile: 'tests/e2e/results.json' }],
  ],
}
