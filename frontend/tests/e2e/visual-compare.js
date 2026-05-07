/**
 * Vue2 与 Vue3 前端视觉对比工具。
 *
 * 运行示例：
 * OLD_BASE_URL=http://127.0.0.1:8084 \
 * NEW_BASE_URL=http://127.0.0.1:8085 \
 * E2E_USERNAME=root \
 * E2E_PASSWORD=rootroot \
 * npm run test:visual-compare
 */
const fs = require('fs')
const path = require('path')
const http = require('http')
const https = require('https')
const { PNG } = require('pngjs')
const pixelmatch = require('pixelmatch')
const {
  loginViaApi
} = require('./support/authRegressionHelper')
const {
  createRouteCatalog,
  resolveReplacementRuntimeConfig
} = require('./support/replacementConfig')
const {
  createContextOptions,
  discoverSeedData,
  ensureParentDir,
  gotoStableRoute,
  normalizeText,
  saveHtmlSnapshot
} = require('./support/replacementHelpers')

let chromium
const localPlaywrightLibDir = path.join(process.env.HOME || '', '.local', 'pw-libs', 'root', 'usr', 'lib', 'x86_64-linux-gnu')
if (fs.existsSync(localPlaywrightLibDir)) {
  const currentLd = process.env.LD_LIBRARY_PATH || ''
  if (!currentLd.split(':').includes(localPlaywrightLibDir)) {
    process.env.LD_LIBRARY_PATH = currentLd
      ? `${localPlaywrightLibDir}:${currentLd}`
      : localPlaywrightLibDir
  }
}
try {
  chromium = require('playwright').chromium
} catch (error) {
  console.error('[visual-compare] missing playwright dependency. run: npm install')
  process.exit(1)
}

const runtimeConfig = resolveReplacementRuntimeConfig()
const rootDir = path.resolve(__dirname, 'visual')
const oldDir = path.join(rootDir, 'old')
const newDir = path.join(rootDir, 'new')
const diffDir = path.join(rootDir, 'diff')
const reportJson = path.join(rootDir, 'report.json')
const reportMd = path.join(rootDir, 'report.md')

function ensureDir(dirPath) {
  if (!fs.existsSync(dirPath)) {
    fs.mkdirSync(dirPath, { recursive: true })
  }
}

function requestText(url) {
  return new Promise((resolve, reject) => {
    const client = url.startsWith('https://') ? https : http
    const req = client.get(url, res => {
      let data = ''
      res.setEncoding('utf8')
      res.on('data', chunk => {
        data += chunk
      })
      res.on('end', () => {
        resolve(data)
      })
    })
    req.on('error', reject)
    req.setTimeout(30000, () => {
      req.destroy(new Error(`timeout for ${url}`))
    })
  })
}

function textDiffRatio(a, b) {
  if (!a && !b) {
    return 0
  }
  const maxLen = Math.max(a.length, b.length)
  if (maxLen === 0) {
    return 0
  }
  const minLen = Math.min(a.length, b.length)
  let diffCount = 0
  for (let i = 0; i < minLen; i++) {
    if (a.charCodeAt(i) !== b.charCodeAt(i)) {
      diffCount += 1
    }
  }
  diffCount += (maxLen - minLen)
  return diffCount / maxLen
}

function normalizeHtmlForCompare(html) {
  if (!html) {
    return ''
  }
  return html
    .replace(/<script\b[\s\S]*?<\/script>/gi, '')
    .replace(/<link\b[^>]*>/gi, '')
    .replace(/<meta\b[^>]*>/gi, '')
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/\s+/g, ' ')
    .trim()
}

function saveReportMarkdown(results) {
  const lines = [
    '# Visual Compare Report',
    '',
    `- old: ${runtimeConfig.oldBaseUrl}`,
    `- new: ${runtimeConfig.newBaseUrl}`,
    '',
    '| route | auth | image_mismatch | html_mismatch | note |',
    '| --- | --- | ---: | ---: | --- |',
  ]

  results.forEach(item => {
    const imageRatio = typeof item.mismatchRatio === 'number'
      ? `${(item.mismatchRatio * 100).toFixed(2)}%`
      : item.status
    const htmlRatio = typeof item.htmlMismatchRatio === 'number'
      ? `${(item.htmlMismatchRatio * 100).toFixed(2)}%`
      : '-'
    lines.push(`| ${item.route} | ${item.authMode} | ${imageRatio} | ${htmlRatio} | ${item.note || '-'} |`)
  })

  fs.writeFileSync(reportMd, `${lines.join('\n')}\n`, 'utf8')
}

function compareImages(oldFile, newFile, diffFile) {
  const oldPng = PNG.sync.read(fs.readFileSync(oldFile))
  const newPng = PNG.sync.read(fs.readFileSync(newFile))
  if (oldPng.width !== newPng.width || oldPng.height !== newPng.height) {
    return {
      mismatchRatio: 1,
      mismatchPixels: oldPng.width * oldPng.height,
      note: `dimension mismatch old=${oldPng.width}x${oldPng.height}, new=${newPng.width}x${newPng.height}`
    }
  }

  const diffPng = new PNG({ width: oldPng.width, height: oldPng.height })
  const mismatchPixels = pixelmatch(
    oldPng.data,
    newPng.data,
    diffPng.data,
    oldPng.width,
    oldPng.height,
    { threshold: 0.1 }
  )
  ensureParentDir(diffFile)
  fs.writeFileSync(diffFile, PNG.sync.write(diffPng))

  return {
    mismatchPixels,
    mismatchRatio: mismatchPixels / (oldPng.width * oldPng.height),
    note: ''
  }
}

async function createPage(browser, baseUrl, authMode) {
  const context = await browser.newContext(createContextOptions())
  const page = await context.newPage()

  if (authMode !== 'public') {
    if (!runtimeConfig.username || !runtimeConfig.password) {
      return {
        context,
        page,
        skipped: true,
        note: 'missing_auth_credentials'
      }
    }

    await loginViaApi(page, {
      baseUrl,
      username: runtimeConfig.username,
      password: runtimeConfig.password,
      email: runtimeConfig.email,
      adminType: runtimeConfig.adminType,
      postgresContainer: runtimeConfig.postgresContainer,
      bootstrapBaseUrl: runtimeConfig.bootstrapBaseUrl
    })
  }

  return {
    context,
    page,
    skipped: false,
    note: ''
  }
}

async function captureRoute(page, baseUrl, route, imageFile, htmlFile) {
  const state = await gotoStableRoute(page, baseUrl, route)
  await saveHtmlSnapshot(page, htmlFile)
  ensureParentDir(imageFile)
  await page.screenshot({ path: imageFile, fullPage: false })
  return state
}

async function closePagePool(pool) {
  const contexts = []
  Object.keys(pool).forEach(side => {
    Object.values(pool[side]).forEach(entry => {
      if (entry && entry.context) {
        contexts.push(entry.context)
      }
    })
  })
  for (const context of contexts) {
    await context.close()
  }
}

async function main() {
  ensureDir(rootDir)
  ensureDir(oldDir)
  ensureDir(newDir)
  ensureDir(diffDir)

  const results = []
  let browser
  let useHtmlFallback = false
  let launchErrorMessage = ''

  try {
    browser = await chromium.launch({ headless: true })
  } catch (error) {
    useHtmlFallback = true
    launchErrorMessage = error.message
    console.warn('[visual-compare] browser launch failed, fallback to HTML compare mode')
  }

  if (!useHtmlFallback) {
    const pagePool = {
      old: {
        public: await createPage(browser, runtimeConfig.oldBaseUrl, 'public'),
        user: await createPage(browser, runtimeConfig.oldBaseUrl, 'user'),
        admin: await createPage(browser, runtimeConfig.oldBaseUrl, 'admin')
      },
      new: {
        public: await createPage(browser, runtimeConfig.newBaseUrl, 'public'),
        user: await createPage(browser, runtimeConfig.newBaseUrl, 'user'),
        admin: await createPage(browser, runtimeConfig.newBaseUrl, 'admin')
      }
    }

    const seedPage = !pagePool.old.user.skipped ? pagePool.old.user.page : pagePool.old.public.page
    const seedData = await discoverSeedData(seedPage, runtimeConfig.oldBaseUrl, runtimeConfig)
    const routeCatalog = createRouteCatalog(runtimeConfig, seedData)
    const routes = []
      .concat(routeCatalog.publicRoutes)
      .concat(!pagePool.old.user.skipped ? routeCatalog.userRoutes : [])
      .concat(!pagePool.old.admin.skipped ? routeCatalog.adminRoutes : [])

    for (const route of routes) {
      const oldFileBase = path.join(oldDir, route.name)
      const newFileBase = path.join(newDir, route.name)
      const oldShot = `${oldFileBase}.png`
      const newShot = `${newFileBase}.png`
      const oldHtml = `${oldFileBase}.html`
      const newHtml = `${newFileBase}.html`
      const diffShot = path.join(diffDir, `${route.name}.png`)
      const oldEntry = pagePool.old[route.authMode]
      const newEntry = pagePool.new[route.authMode]

      const row = {
        route: route.path,
        routeName: route.name,
        area: route.area,
        authMode: route.authMode,
        oldShot: path.relative(rootDir, oldShot),
        newShot: path.relative(rootDir, newShot),
        diffShot: path.relative(rootDir, diffShot),
        oldHtml: path.relative(rootDir, oldHtml),
        newHtml: path.relative(rootDir, newHtml),
        mismatchPixels: null,
        mismatchRatio: null,
        htmlMismatchRatio: null,
        status: 'ok',
        note: ''
      }

      if (!oldEntry || !newEntry || oldEntry.skipped || newEntry.skipped) {
        row.status = 'skipped'
        row.note = 'missing_auth_credentials'
        row.diffShot = null
        results.push(row)
        continue
      }

      try {
        const oldState = await captureRoute(oldEntry.page, runtimeConfig.oldBaseUrl, route, oldShot, oldHtml)
        const newState = await captureRoute(newEntry.page, runtimeConfig.newBaseUrl, route, newShot, newHtml)
        Object.assign(row, compareImages(oldShot, newShot, diffShot))

        const oldNormalizedHtml = normalizeHtmlForCompare(fs.readFileSync(oldHtml, 'utf8'))
        const newNormalizedHtml = normalizeHtmlForCompare(fs.readFileSync(newHtml, 'utf8'))
        row.htmlMismatchRatio = textDiffRatio(oldNormalizedHtml, newNormalizedHtml)
        row.oldTitle = oldState.title
        row.newTitle = newState.title
        row.oldFinalPath = oldState.finalPath
        row.newFinalPath = newState.finalPath
        row.note = row.note || `title_old=${oldState.title}; title_new=${newState.title}`
        console.log(`[visual-compare] ${route.path} mismatch=${(row.mismatchRatio * 100).toFixed(2)}% html=${(row.htmlMismatchRatio * 100).toFixed(2)}%`)
      } catch (error) {
        row.status = 'capture_failed'
        row.note = error.message
        row.mismatchRatio = null
        row.diffShot = null
        console.error(`[visual-compare] capture failed ${route.path}: ${error.message}`)
      }

      results.push(row)
    }

    await closePagePool(pagePool)
    await browser.close()
  } else {
    const routeCatalog = createRouteCatalog(runtimeConfig, {})
    const routes = routeCatalog.publicRoutes

    for (const route of routes) {
      const oldHtmlFile = path.join(oldDir, `${route.name}.html`)
      const newHtmlFile = path.join(newDir, `${route.name}.html`)
      const row = {
        route: route.path,
        routeName: route.name,
        area: route.area,
        authMode: route.authMode,
        oldShot: null,
        newShot: null,
        diffShot: null,
        oldHtml: path.relative(rootDir, oldHtmlFile),
        newHtml: path.relative(rootDir, newHtmlFile),
        mismatchPixels: null,
        mismatchRatio: null,
        htmlMismatchRatio: null,
        status: 'html_fallback',
        note: `playwright_unavailable=${launchErrorMessage.split('\n')[0]}`
      }
      try {
        const [oldHtml, newHtml] = await Promise.all([
          requestText(`${runtimeConfig.oldBaseUrl}${route.path}`),
          requestText(`${runtimeConfig.newBaseUrl}${route.path}`)
        ])
        ensureParentDir(oldHtmlFile)
        ensureParentDir(newHtmlFile)
        fs.writeFileSync(oldHtmlFile, oldHtml, 'utf8')
        fs.writeFileSync(newHtmlFile, newHtml, 'utf8')
        row.htmlMismatchRatio = textDiffRatio(
          normalizeHtmlForCompare(oldHtml),
          normalizeHtmlForCompare(newHtml)
        )
        console.log(`[visual-compare:fallback] ${route.path} htmlMismatch=${(row.htmlMismatchRatio * 100).toFixed(2)}%`)
      } catch (error) {
        row.status = 'capture_failed'
        row.note = error.message
        console.error(`[visual-compare:fallback] compare failed ${route.path}: ${error.message}`)
      }
      results.push(row)
    }
  }

  fs.writeFileSync(reportJson, `${JSON.stringify({
    oldBaseUrl: runtimeConfig.oldBaseUrl,
    newBaseUrl: runtimeConfig.newBaseUrl,
    generatedAt: new Date().toISOString(),
    results
  }, null, 2)}\n`, 'utf8')
  saveReportMarkdown(results)

  const failedCount = results.filter(item => item.status === 'capture_failed').length
  if (failedCount > 0) {
    process.exit(2)
  }
}

main().catch(error => {
  console.error(`[visual-compare] fatal error: ${error.message}`)
  process.exit(1)
})
