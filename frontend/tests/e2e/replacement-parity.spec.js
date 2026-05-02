const { test, expect } = require('@playwright/test')
const { loginViaApi } = require('./support/authRegressionHelper')
const { createRouteCatalog, resolveReplacementRuntimeConfig } = require('./support/replacementConfig')
const {
  buildUrl,
  createContextOptions,
  discoverSeedData,
  gotoStableRoute,
  normalizeText
} = require('./support/replacementHelpers')

const runtimeConfig = resolveReplacementRuntimeConfig()
const hasAuthCredentials = Boolean(runtimeConfig.username && runtimeConfig.password)

async function openParityPages(browser) {
  const oldContext = await browser.newContext(createContextOptions())
  const newContext = await browser.newContext(createContextOptions())
  const oldPage = await oldContext.newPage()
  const newPage = await newContext.newPage()
  return {
    oldContext,
    newContext,
    oldPage,
    newPage
  }
}

async function closeParityPages(pages) {
  await pages.oldContext.close()
  await pages.newContext.close()
}

function assertParityForRoute(route, oldResult, newResult) {
  expect.soft(oldResult.finalPath, `[${route.name}] final path parity`).toBe(newResult.finalPath)
  expect.soft(oldResult.readyMatched, `[${route.name}] ready selector parity`).toBe(newResult.readyMatched)
  expect.soft(oldResult.title, `[${route.name}] title parity`).toBe(newResult.title)
  expect.soft(oldResult.bodyExcerpt.length > 40, `[${route.name}] old page should have visible content`).toBeTruthy()
  expect.soft(newResult.bodyExcerpt.length > 40, `[${route.name}] new page should have visible content`).toBeTruthy()
  if (route.expectedText) {
    const expectedText = normalizeText(route.expectedText)
    expect.soft(oldResult.bodyText.includes(expectedText), `[${route.name}] old page text should include ${expectedText}`).toBeTruthy()
    expect.soft(newResult.bodyText.includes(expectedText), `[${route.name}] new page text should include ${expectedText}`).toBeTruthy()
  }
}

async function loginPair(oldPage, newPage) {
  const authConfigOld = {
    baseUrl: runtimeConfig.oldBaseUrl,
    username: runtimeConfig.username,
    password: runtimeConfig.password,
    email: runtimeConfig.email,
    adminType: runtimeConfig.adminType,
    postgresContainer: runtimeConfig.postgresContainer,
    bootstrapBaseUrl: runtimeConfig.bootstrapBaseUrl
  }
  const authConfigNew = {
    baseUrl: runtimeConfig.newBaseUrl,
    username: runtimeConfig.username,
    password: runtimeConfig.password,
    email: runtimeConfig.email,
    adminType: runtimeConfig.adminType,
    postgresContainer: runtimeConfig.postgresContainer,
    bootstrapBaseUrl: runtimeConfig.bootstrapBaseUrl
  }
  await loginViaApi(oldPage, authConfigOld)
  await loginViaApi(newPage, authConfigNew)
}

async function setupWebSocketRoute(page, route) {
  if (route.setupKey === 'open-agent-panel') {
    const agentFab = page.locator('.agent-panel-fab').first()
    if (await agentFab.isVisible().catch(() => false)) {
      await agentFab.click({ force: true })
      const guideAction = page.locator('.welcome-action-chip, .quick-actions a, button')
        .filter({ hasText: /题目导读|获取题目导读|problem guide|reading/i })
        .first()
      if (await guideAction.isVisible().catch(() => false)) {
        await guideAction.click({ force: true })
      }
    }
    return
  }

  if (route.setupKey === 'open-monitor-tab') {
    const monitorAction = page.locator('button, a').filter({ hasText: /数据统计|monitor/i }).first()
    if (await monitorAction.isVisible().catch(() => false)) {
      await monitorAction.click()
    }
  }
}

async function assertWebSocketRoute(page, baseUrl, route) {
  const openedUrls = []
  page.on('websocket', ws => {
    openedUrls.push(ws.url())
  })
  await gotoStableRoute(page, baseUrl, route)
  await setupWebSocketRoute(page, route)
  await expect.poll(() => openedUrls.find(url => route.matchPattern.test(url)) || '', {
    timeout: 15000
  }).not.toBe('')
}

test.describe('Frontend replacement parity', () => {
  test('public route parity matrix', async ({ browser }) => {
    test.setTimeout(180000)

    const pages = await openParityPages(browser)
    try {
      const seedData = await discoverSeedData(pages.oldPage, runtimeConfig.oldBaseUrl, runtimeConfig)
      const { publicRoutes } = createRouteCatalog(runtimeConfig, seedData)

      for (const route of publicRoutes) {
        const oldResult = await gotoStableRoute(pages.oldPage, runtimeConfig.oldBaseUrl, route)
        const newResult = await gotoStableRoute(pages.newPage, runtimeConfig.newBaseUrl, route)
        assertParityForRoute(route, oldResult, newResult)
      }
    } finally {
      await closeParityPages(pages)
    }
  })

  test('authenticated route parity matrix', async ({ browser }) => {
    test.setTimeout(180000)
    test.skip(!hasAuthCredentials, 'Set E2E_USERNAME and E2E_PASSWORD to validate authenticated parity')

    const pages = await openParityPages(browser)
    try {
      await loginPair(pages.oldPage, pages.newPage)
      const seedData = await discoverSeedData(pages.oldPage, runtimeConfig.oldBaseUrl, runtimeConfig)
      const { userRoutes } = createRouteCatalog(runtimeConfig, seedData)

      for (const route of userRoutes) {
        const oldResult = await gotoStableRoute(pages.oldPage, runtimeConfig.oldBaseUrl, route)
        const newResult = await gotoStableRoute(pages.newPage, runtimeConfig.newBaseUrl, route)
        expect.soft(oldResult.finalPath.includes('/login'), `[${route.name}] old frontend should stay authenticated`).toBeFalsy()
        expect.soft(newResult.finalPath.includes('/login'), `[${route.name}] new frontend should stay authenticated`).toBeFalsy()
        assertParityForRoute(route, oldResult, newResult)
      }
    } finally {
      await closeParityPages(pages)
    }
  })

  test('admin route parity matrix', async ({ browser }) => {
    test.setTimeout(180000)
    test.skip(!hasAuthCredentials, 'Set E2E_USERNAME and E2E_PASSWORD to validate admin parity')

    const pages = await openParityPages(browser)
    try {
      await loginPair(pages.oldPage, pages.newPage)
      const seedData = await discoverSeedData(pages.oldPage, runtimeConfig.oldBaseUrl, runtimeConfig)
      const { adminRoutes } = createRouteCatalog(runtimeConfig, seedData)

      for (const route of adminRoutes) {
        const oldResult = await gotoStableRoute(pages.oldPage, runtimeConfig.oldBaseUrl, route)
        const newResult = await gotoStableRoute(pages.newPage, runtimeConfig.newBaseUrl, route)
        expect.soft(oldResult.finalPath.includes('/admin/login'), `[${route.name}] old admin should not fall back to login`).toBeFalsy()
        expect.soft(newResult.finalPath.includes('/admin/login'), `[${route.name}] new admin should not fall back to login`).toBeFalsy()
        assertParityForRoute(route, oldResult, newResult)
      }
    } finally {
      await closeParityPages(pages)
    }
  })

  test('websocket parity matrix', async ({ browser }) => {
    test.setTimeout(120000)
    test.skip(!hasAuthCredentials, 'Set E2E_USERNAME and E2E_PASSWORD to validate websocket parity')

    const pages = await openParityPages(browser)
    try {
      await loginPair(pages.oldPage, pages.newPage)
      const seedData = await discoverSeedData(pages.oldPage, runtimeConfig.oldBaseUrl, runtimeConfig)
      const { websocketRoutes } = createRouteCatalog(runtimeConfig, seedData)

      for (const route of websocketRoutes) {
        await assertWebSocketRoute(pages.oldPage, runtimeConfig.oldBaseUrl, route)
        await assertWebSocketRoute(pages.newPage, runtimeConfig.newBaseUrl, route)
      }
    } finally {
      await closeParityPages(pages)
    }
  })

  test('deployment entry should point to frontend assets', async () => {
    const frontendDockerfile = await test.step('read dockerfile', async () => {
      const fs = require('fs')
      return fs.readFileSync('/home/cypress/Alethicode/deploy/frontend.Dockerfile', 'utf8')
    })
    const startScript = await test.step('read start script', async () => {
      const fs = require('fs')
      return fs.readFileSync('/home/cypress/Alethicode/start.sh', 'utf8')
    })

    expect(frontendDockerfile.includes('COPY frontend/package*.json ./')).toBeTruthy()
    expect(frontendDockerfile.includes('COPY frontend ./')).toBeTruthy()
    expect(/cd "\$ROOT_DIR\/frontend"/.test(startScript)).toBeTruthy()
    expect(/cd "\$ROOT_DIR\/frontend"(?!_new)/.test(startScript)).toBeFalsy()
    expect(buildUrl(runtimeConfig.newBaseUrl, '/')).toContain(runtimeConfig.newBaseUrl)
  })
})
