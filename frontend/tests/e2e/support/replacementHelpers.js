const path = require('path')
const { DEFAULT_VIEWPORT } = require('./replacementConfig')

const DISABLE_MOTION_CSS = `
  *, *::before, *::after {
    animation: none !important;
    transition: none !important;
    caret-color: transparent !important;
  }
`

function createContextOptions() {
  return {
    viewport: DEFAULT_VIEWPORT,
    locale: 'zh-CN',
    timezoneId: 'Asia/Shanghai'
  }
}

function ensureParentDir(filePath) {
  const fs = require('fs')
  const dirPath = path.dirname(filePath)
  if (!fs.existsSync(dirPath)) {
    fs.mkdirSync(dirPath, { recursive: true })
  }
}

function buildUrl(baseUrl, routePath) {
  return `${String(baseUrl).replace(/\/+$/, '')}${routePath}`
}

function normalizePathname(urlValue) {
  const url = new URL(urlValue)
  const normalized = url.pathname.replace(/\/+$/, '')
  return normalized || '/'
}

function normalizeText(value) {
  if (!value) {
    return ''
  }
  return String(value).replace(/\s+/g, ' ').trim()
}

async function disablePageMotion(page) {
  await page.addStyleTag({ content: DISABLE_MOTION_CSS })
}

async function gotoStableRoute(page, baseUrl, route) {
  const targetUrl = buildUrl(baseUrl, route.path)
  await page.goto(targetUrl, {
    waitUntil: 'domcontentloaded',
    timeout: 45000
  })
  await disablePageMotion(page)
  await page.waitForTimeout(1200)

  let readyMatched = true
  if (route.readySelector) {
    try {
      await page.waitForSelector(route.readySelector, { timeout: 15000 })
    } catch (error) {
      readyMatched = false
    }
  }

  const title = await page.title()
  const bodyText = normalizeText(await page.locator('body').innerText().catch(() => ''))

  return {
    url: page.url(),
    finalPath: normalizePathname(page.url()),
    readyMatched,
    title: normalizeText(title),
    bodyText,
    bodyExcerpt: bodyText.slice(0, 800)
  }
}

async function saveHtmlSnapshot(page, outputFile) {
  const fs = require('fs')
  ensureParentDir(outputFile)
  fs.writeFileSync(outputFile, await page.content(), 'utf8')
}

function toResultList(data) {
  if (!data) {
    return []
  }
  if (Array.isArray(data)) {
    return data
  }
  if (Array.isArray(data.results)) {
    return data.results
  }
  if (Array.isArray(data.data)) {
    return data.data
  }
  return []
}

function pickFirstValue(objectValue, candidates) {
  if (!objectValue || typeof objectValue !== 'object') {
    return null
  }
  for (const key of candidates) {
    if (objectValue[key] !== undefined && objectValue[key] !== null && String(objectValue[key]).trim() !== '') {
      return String(objectValue[key])
    }
  }
  return null
}

async function discoverSeedData(page, baseUrl, runtimeConfig) {
  const seedData = {
    problemId: runtimeConfig.problemId || null,
    submissionId: runtimeConfig.submissionId || null,
    classroomId: runtimeConfig.classroomId || null,
    collabSessionId: runtimeConfig.collabSessionId || null
  }

  if (!seedData.problemId) {
    const problemData = await page.request.get(buildUrl(baseUrl, '/api/problems'), {
      params: { limit: 1, offset: 0 }
    }).then(resp => resp.ok() ? resp.json() : null).catch(() => null)
    const problemList = toResultList(problemData && !problemData.error ? problemData.data : null)
    if (problemList.length > 0) {
      seedData.problemId = pickFirstValue(problemList[0], ['_id', 'display_id', 'id', 'problem_id'])
    }
  }

  if (!seedData.submissionId) {
    const submissionData = await page.request.get(buildUrl(baseUrl, '/api/submissions'), {
      params: { limit: 1, offset: 0 }
    }).then(resp => resp.ok() ? resp.json() : null).catch(() => null)
    const submissionList = toResultList(submissionData && !submissionData.error ? submissionData.data : null)
    if (submissionList.length > 0) {
      seedData.submissionId = pickFirstValue(submissionList[0], ['id', 'submission_id'])
    }
  }

  if (!seedData.classroomId) {
    const classroomData = await page.request.get(buildUrl(baseUrl, '/api/classroom/'), {
      params: { limit: 1, offset: 0 }
    }).then(resp => resp.ok() ? resp.json() : null).catch(() => null)
    const classroomList = toResultList(classroomData && !classroomData.error ? classroomData.data : null)
    if (classroomList.length > 0) {
      seedData.classroomId = pickFirstValue(classroomList[0], ['id', 'classroom_id'])
    }
  }

  if (seedData.classroomId && !seedData.collabSessionId) {
    const sessionData = await page.request.get(buildUrl(baseUrl, `/api/classroom/${seedData.classroomId}/sessions/`), {
      params: { limit: 1, offset: 0 }
    }).then(resp => resp.ok() ? resp.json() : null).catch(() => null)
    const sessionList = toResultList(sessionData && !sessionData.error ? sessionData.data : null)
    if (sessionList.length > 0) {
      seedData.collabSessionId = pickFirstValue(sessionList[0], ['id', 'session_id'])
    }
  }

  return seedData
}

module.exports = {
  DISABLE_MOTION_CSS,
  buildUrl,
  createContextOptions,
  discoverSeedData,
  disablePageMotion,
  ensureParentDir,
  gotoStableRoute,
  normalizePathname,
  normalizeText,
  saveHtmlSnapshot
}
