const { expect } = require('@playwright/test')
const { execFileSync } = require('child_process')

const DEFAULT_E2E_USERNAME = 'replacement_admin'
const DEFAULT_E2E_PASSWORD = 'ReplacementPass123!'
const DEFAULT_E2E_EMAIL = 'replacement_admin@example.com'
const DEFAULT_E2E_ADMIN_TYPE = 'Admin'
const DEFAULT_POSTGRES_CONTAINER = 'java-oj-postgres'
const DEFAULT_BOOTSTRAP_BASE_URL = 'http://127.0.0.1:8081'
const ensuredUsers = new Set()

function trimToNull(value) {
  if (typeof value !== 'string') {
    return null
  }
  const trimmed = value.trim()
  return trimmed.length > 0 ? trimmed : null
}

function normalizeBaseUrl(rawBaseUrl, fallback = 'http://127.0.0.1:18080') {
  return (rawBaseUrl || fallback).replace(/\/+$/, '')
}

function resolveRealBackendConfig() {
  const baseUrl = normalizeBaseUrl(process.env.BASE_URL)
  const username = trimToNull(process.env.E2E_USERNAME) || DEFAULT_E2E_USERNAME
  const password = trimToNull(process.env.E2E_PASSWORD) || DEFAULT_E2E_PASSWORD
  const email = trimToNull(process.env.E2E_EMAIL) || DEFAULT_E2E_EMAIL
  const adminType = trimToNull(process.env.E2E_ADMIN_TYPE) || DEFAULT_E2E_ADMIN_TYPE
  const postgresContainer = trimToNull(process.env.E2E_POSTGRES_CONTAINER) || DEFAULT_POSTGRES_CONTAINER
  const bootstrapBaseUrl = normalizeBaseUrl(process.env.E2E_BOOTSTRAP_BASE_URL, DEFAULT_BOOTSTRAP_BASE_URL)

  return {
    baseUrl,
    username,
    password,
    email,
    adminType,
    postgresContainer,
    bootstrapBaseUrl
  }
}

async function fetchCsrfToken(page, baseUrl) {
  const csrfResp = await page.request.get(`${baseUrl}/api/csrf`)
  if (!csrfResp.ok()) {
    throw new Error(`Failed to fetch CSRF token: ${csrfResp.status()}`)
  }

  const cookies = await page.context().cookies(baseUrl)
  const csrfCookie = cookies.find(c => c.name === 'csrftoken')
  if (!csrfCookie || !csrfCookie.value) {
    throw new Error('Missing csrftoken cookie after /api/csrf')
  }
  return csrfCookie.value
}

async function assertAuthenticatedProfile(page, config) {
  const profileResp = await page.request.get(`${config.baseUrl}/api/profile`)
  expect(profileResp.ok()).toBeTruthy()

  const payload = await profileResp.json()
  expect(payload.error).toBeNull()
  expect(payload.data).toBeTruthy()
  const username = extractProfileUsername(payload)
  expect(username).toBeTruthy()
  expect(String(username).toLowerCase()).toBe(config.username.toLowerCase())
}

async function fetchCaptchaCode(page, baseUrl) {
  const captchaResp = await page.request.get(`${baseUrl}/api/captcha`)
  if (!captchaResp.ok()) {
    throw new Error(`Failed to fetch captcha: ${captchaResp.status()}`)
  }

  const payload = await captchaResp.json()
  const captcha = payload && payload.data && payload.data.captcha
  if (!captcha) {
    throw new Error('Captcha payload missing data.captcha')
  }
  return String(captcha)
}

function escapeSqlLiteral(value) {
  return String(value).replace(/'/g, "''")
}

function promoteAcceptanceUser(config) {
  const usernameLiteral = escapeSqlLiteral(config.username)
  const adminTypeLiteral = escapeSqlLiteral(config.adminType)
  const sql = `
    update "user"
    set admin_type = '${adminTypeLiteral}',
        problem_permission = 'All',
        is_disabled = false
    where lower(username) = lower('${usernameLiteral}');
  `
  execFileSync('docker', [
    'exec',
    config.postgresContainer,
    'psql',
    '-U',
    'onlinejudge',
    '-d',
    'alethicode',
    '-c',
    sql
  ], {
    stdio: 'pipe'
  })
}

async function ensureAcceptanceUser(page, config) {
  const bootstrapBaseUrl = normalizeBaseUrl(config.bootstrapBaseUrl, DEFAULT_BOOTSTRAP_BASE_URL)
  const cacheKey = `${bootstrapBaseUrl}|${config.username}|${config.adminType}`
  if (ensuredUsers.has(cacheKey)) {
    return
  }

  const csrfToken = await fetchCsrfToken(page, bootstrapBaseUrl)
  const captcha = await fetchCaptchaCode(page, bootstrapBaseUrl)
  const registerResp = await page.request.post(`${bootstrapBaseUrl}/api/register`, {
    headers: {
      'X-CSRFToken': csrfToken
    },
    data: {
      username: config.username,
      email: config.email,
      password: config.password,
      captcha
    }
  })

  if (!registerResp.ok()) {
    throw new Error(`Register API returned ${registerResp.status()}`)
  }

  const payload = await registerResp.json()
  if (payload.error) {
    const reason = String(payload.data || payload.error)
    const alreadyExists = /username already exists|email already exists/i.test(reason)
    if (!alreadyExists) {
      throw new Error(`Register failed: ${reason}`)
    }
  }

  promoteAcceptanceUser(config)
  ensuredUsers.add(cacheKey)
}

async function loginViaApi(page, config) {
  await ensureAcceptanceUser(page, config)
  const csrfToken = await fetchCsrfToken(page, config.baseUrl)

  const loginResp = await page.request.post(`${config.baseUrl}/api/login`, {
    headers: {
      'X-CSRFToken': csrfToken
    },
    data: {
      username: config.username,
      password: config.password
    }
  })

  if (!loginResp.ok()) {
    throw new Error(`Login API returned ${loginResp.status()}`)
  }

  const payload = await loginResp.json()
  if (payload.error) {
    throw new Error(`Login failed: ${String(payload.data || payload.error)}`)
  }
  await assertAuthenticatedProfile(page, config)
}

async function loginViaLoginModal(page, config) {
  await ensureAcceptanceUser(page, config)
  const loginPage = page.locator('.login-page')
  if (await isVisible(loginPage.first())) {
    await loginViaStandaloneLoginPage(page, config)
    return
  }

  const loginBtn = page.locator('#header .btn-menu button').first()
  if (await isVisible(loginBtn)) {
    await loginBtn.click()

    const modalCard = page.locator('.el-overlay:visible .login-color-card').first()
    await expect(modalCard).toBeVisible()

    const inputs = modalCard.locator('input')
    await inputs.nth(0).fill(config.username)
    await inputs.nth(1).fill(config.password)

    const submitBtn = modalCard.locator('button.btn').first()
    await submitBtn.click()

    await expect(page.locator('.el-overlay:visible')).toHaveCount(0, { timeout: 20000 })
    return
  }

  await page.goto(`${config.baseUrl}/login?redirect=/`)
  await loginViaStandaloneLoginPage(page, config)
}

async function loginViaStandaloneLoginPage(page, config) {
  await expect(page.locator('.login-page')).toBeVisible()

  const usernameInput = page.locator('.login-page input.f-input').nth(0)
  const passwordInput = page.locator('.login-page input.f-input').nth(1)
  await expect(usernameInput).toBeVisible()
  await expect(passwordInput).toBeVisible()
  await usernameInput.fill(config.username)
  await passwordInput.fill(config.password)

  const submitBtn = page.locator('.login-page button.login-btn').first()
  await expect(submitBtn).toBeVisible()
  await submitBtn.click()

  await expect.poll(async () => {
    const username = await fetchProfileUsername(page, config.baseUrl)
    return username ? String(username).toLowerCase() : ''
  }, { timeout: 20000 }).toBe(config.username.toLowerCase())
}

function extractProfileUsername(payload) {
  if (!payload || !payload.data) {
    return null
  }
  if (payload.data.user && typeof payload.data.user === 'object') {
    return payload.data.user.username || null
  }
  return payload.data.username || null
}

async function isVisible(locator) {
  try {
    return await locator.isVisible()
  } catch (error) {
    return false
  }
}

async function fetchProfileUsername(page, baseUrl) {
  const profileResp = await page.request.get(`${baseUrl}/api/profile`)
  if (!profileResp.ok()) {
    return null
  }
  const payload = await profileResp.json()
  if (payload.error || !payload.data) {
    return null
  }
  return extractProfileUsername(payload)
}

module.exports = {
  resolveRealBackendConfig,
  assertAuthenticatedProfile,
  loginViaApi,
  loginViaLoginModal
}
