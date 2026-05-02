const fs = require('fs')
const path = require('path')
const { execFileSync } = require('child_process')

const repoRoot = path.resolve(__dirname, '../..')
const oldSrcRoot = path.join(repoRoot, 'frontend/src')
const newSrcRoot = path.join(repoRoot, 'frontend/src')
const reportDir = path.join(repoRoot, 'frontend/tests/replacement/reports')
const reportJson = path.join(reportDir, 'static-audit.json')
const reportMd = path.join(reportDir, 'static-audit.md')

const pureSyntaxPatterns = [
  /^[+-]\s*import\s+\{\s*createRouter,\s*createWebHistory\s*\}\s+from\s+'vue-router'/,
  /^[+-]\s*import\s+Vue\s+from\s+'vue'/,
  /^[+-]\s*import\s+VueRouter\s+from\s+'vue-router'/,
  /^[+-]\s*Vue\.use\(/,
  /^[+-]\s*mode:\s*'history'/,
  /^[+-]\s*history:\s*createWebHistory/,
  /^[+-].*pathMatch\(.*\)\*/,
  /^[+-].*\bslot="/,
  /^[+-].*#(?:title|extra|content|open|close|dropdown|header|default|footer|list)\b/,
  /^[+-].*\bslot-scope=/,
  /^[+-].*beforeDestroy\b/,
  /^[+-].*beforeUnmount\b/,
  /^[+-].*componentUpdated\b/,
  /^[+-].*updated\b/,
  /^[+-].*bind\b/,
  /^[+-].*beforeMount\b/,
  /^[+-].*configureCompat/,
  /^[+-].*@vue\/compat/,
  /^[+-].*\$listeners\b/,
  /^[+-].*\$scopedSlots\b/,
  /^[+-].*legacy:\s*false/,
  /^[+-].*@keyup\.enter(?:\.native)?=/,
  /^[+-].*@click(?:\.native)?\.prevent=/,
  /^[+-].*name:\s*'[^']+'/,
  /^[+-].*ViewUIPlus/,
  /^[+-].*ElementPlus/,
  /^[+-].*view-ui-plus/,
  /^[+-].*element-plus/,
  /^[+-].*<router-view v-slot=/,
  /^[+-].*<component :is="Component"/
]

const semanticPathPatterns = [
  /^src\/i18n\/index\.js$/,
  /^src\/pages\/admin\/api\.js$/,
  /^src\/pages\/oj\/api\.js$/,
  /^src\/pages\/admin\/index\.js$/,
  /^src\/pages\/oj\/index\.js$/,
  /^src\/pages\/admin\/router\.js$/,
  /^src\/pages\/oj\/router\/index\.js$/,
  /^src\/plugins\/highlight\.js$/,
  /^src\/plugins\/katex\.js$/,
  /^src\/store\/index\.js$/,
  /^src\/store\/modules\/problem\.js$/,
  /^src\/utils\/sanitize\.js$/,
  /^src\/utils\/utils\.js$/,
  /^src\/utils\/uiBridge\.js$/,
  /^src\/utils\/settingsToast\.js$/,
  /^src\/pages\/oj\/components\/ECharts\.vue$/,
  /^src\/pages\/oj\/components\/CodeMirror\.vue$/,
  /^src\/pages\/oj\/components\/PedagogyPanel\.vue$/,
  /^src\/pages\/oj\/components\/SubmissionRiver\.vue$/,
  /^src\/pages\/oj\/components\/skillProfile\/.+$/,
  /^src\/pages\/oj\/views\/problem\/workflowStateMachine\.js$/,
  /^src\/pages\/oj\/views\/problem\/UnifiedAgentPanel\.vue$/,
  /^src\/pages\/oj\/views\/problem\/Problem\.vue$/,
  /^src\/pages\/oj\/views\/problem\/CodeAnalysisPanel\.vue$/,
  /^src\/pages\/oj\/views\/problem\/CodeEditorPanel\.vue$/,
  /^src\/pages\/oj\/views\/problem\/cards\/.+$/,
  /^src\/pages\/oj\/views\/classroom\/.+$/,
  /^src\/pages\/admin\/views\/general\/Login\.vue$/
]

const semanticDiffPatterns = [
  /\buiLoading(Start|Finish)\b/,
  /\bui(Error|Success)\b/,
  /\bViewUIPlus\b/,
  /\bElementPlus\b/,
  /\bECharts\.vue\b/,
  /\bcreateRouter\b/,
  /\bcreateWebHistory\b/,
  /\blegacy:\s*false\b/,
  /\bworkflow\b/,
  /\bclassroom\b/,
  /\bai[_ -]?\b/i
]

function ensureDir(dirPath) {
  if (!fs.existsSync(dirPath)) {
    fs.mkdirSync(dirPath, { recursive: true })
  }
}

function readFile(filePath) {
  return fs.readFileSync(filePath, 'utf8')
}

function parseDiffEntries() {
  let rawOutput = ''
  try {
    rawOutput = execFileSync('diff', ['-rq', oldSrcRoot, newSrcRoot], {
      cwd: repoRoot,
      encoding: 'utf8'
    })
  } catch (error) {
    rawOutput = `${error.stdout || ''}${error.stderr || ''}`
  }

  return rawOutput
    .split('\n')
    .map(line => line.trim())
    .filter(Boolean)
    .map(line => {
      if (line.startsWith('Files ')) {
        const match = line.match(/^Files\s+(.+?)\s+and\s+(.+?)\s+differ$/)
        if (!match) {
          return null
        }
        return {
          type: 'changed',
          oldPath: match[1],
          newPath: match[2],
          relativePath: path.relative(oldSrcRoot, match[1])
        }
      }

      if (line.startsWith('Only in ')) {
        const match = line.match(/^Only in\s+(.+?):\s+(.+)$/)
        if (!match) {
          return null
        }
        const parentDir = match[1]
        const childName = match[2]
        const isNewSide = parentDir.startsWith(newSrcRoot)
        return {
          type: isNewSide ? 'only_in_new' : 'only_in_old',
          oldPath: isNewSide ? null : path.join(parentDir, childName),
          newPath: isNewSide ? path.join(parentDir, childName) : null,
          relativePath: path.relative(isNewSide ? newSrcRoot : oldSrcRoot, path.join(parentDir, childName))
        }
      }

      return null
    })
    .filter(Boolean)
    .sort((a, b) => a.relativePath.localeCompare(b.relativePath))
}

function getExportCount(filePath) {
  const text = readFile(filePath)
  const match = text.match(/export default\s*\{([\s\S]*?)\n\}/)
  if (!match) {
    return 0
  }
  return [...match[1].matchAll(/\n\s*([A-Za-z0-9_$]+)\s*[:,(]/g)].length
}

function getRouteCount(filePath) {
  const text = readFile(filePath)
  return (text.match(/path:\s*['"]/g) || []).length
}

function classifyChangedFile(entry) {
  if (entry.type === 'only_in_new') {
    return {
      category: 'semantic_adapter_or_runtime_bridge',
      reason: 'frontend 新增适配文件，用于承接 Vue3 运行时或第三方库替换'
    }
  }

  if (entry.type === 'only_in_old') {
    return {
      category: 'runtime_behavior_change_or_manual_review',
      reason: 'frontend 缺少旧文件，需要人工确认是否已被合并或替代'
    }
  }

  let diffOutput = ''
  try {
    diffOutput = execFileSync('diff', ['-u', entry.oldPath, entry.newPath], {
      cwd: repoRoot,
      encoding: 'utf8'
    })
  } catch (error) {
    diffOutput = `${error.stdout || ''}${error.stderr || ''}`
  }

  const changedLines = diffOutput
    .split('\n')
    .filter(line => (line.startsWith('+') || line.startsWith('-')) && !line.startsWith('+++') && !line.startsWith('---'))

  const isPureSyntax = changedLines.length > 0 && changedLines.every(line => pureSyntaxPatterns.some(pattern => pattern.test(line)))
  if (isPureSyntax) {
    return {
      category: 'pure_vue3_syntax_migration',
      reason: 'diff 限定在插槽、生命周期、事件修饰符、router API 等 Vue3 语法迁移'
    }
  }

  const isSemanticAdapter = semanticPathPatterns.some(pattern => pattern.test(entry.relativePath)) ||
    changedLines.some(line => semanticDiffPatterns.some(pattern => pattern.test(line)))

  if (isSemanticAdapter) {
    return {
      category: 'semantic_adapter_or_runtime_bridge',
      reason: 'diff 包含依赖替换、运行时桥接或状态/路由/插件适配，目标是保持行为一致'
    }
  }

  return {
    category: 'runtime_behavior_change_or_manual_review',
    reason: 'diff 含有非纯语法改动，必须依赖运行时验收与视觉回归确认是否等价'
  }
}

function buildMarkdown(summary, entries) {
  const lines = [
    '# Static Replacement Audit',
    '',
    `- 生成时间：${summary.generatedAt}`,
    `- OJ 路由数：old=${summary.routeCounts.ojOld} / new=${summary.routeCounts.ojNew}`,
    `- Admin 路由数：old=${summary.routeCounts.adminOld} / new=${summary.routeCounts.adminNew}`,
    `- OJ API 导出数：old=${summary.apiCounts.ojOld} / new=${summary.apiCounts.ojNew}`,
    `- Admin API 导出数：old=${summary.apiCounts.adminOld} / new=${summary.apiCounts.adminNew}`,
    `- 差异文件总数：${summary.diffCount}`,
    '',
    '## 分类统计',
    '',
    '| category | count |',
    '| --- | ---: |',
    `| pure_vue3_syntax_migration | ${summary.categoryCounts.pure_vue3_syntax_migration || 0} |`,
    `| semantic_adapter_or_runtime_bridge | ${summary.categoryCounts.semantic_adapter_or_runtime_bridge || 0} |`,
    `| runtime_behavior_change_or_manual_review | ${summary.categoryCounts.runtime_behavior_change_or_manual_review || 0} |`,
    '',
    '## 差异明细',
    '',
    '| path | change_type | category | reason |',
    '| --- | --- | --- | --- |'
  ]

  entries.forEach(entry => {
    lines.push(`| ${entry.relativePath} | ${entry.type} | ${entry.category} | ${entry.reason} |`)
  })

  return `${lines.join('\n')}\n`
}

function main() {
  ensureDir(reportDir)

  const entries = parseDiffEntries().map(entry => Object.assign({}, entry, classifyChangedFile(entry)))
  const summary = {
    generatedAt: new Date().toISOString(),
    diffCount: entries.length,
    routeCounts: {
      ojOld: getRouteCount(path.join(repoRoot, 'frontend/src/pages/oj/router/routes.js')),
      ojNew: getRouteCount(path.join(repoRoot, 'frontend/src/pages/oj/router/routes.js')),
      adminOld: getRouteCount(path.join(repoRoot, 'frontend/src/pages/admin/router.js')),
      adminNew: getRouteCount(path.join(repoRoot, 'frontend/src/pages/admin/router.js'))
    },
    apiCounts: {
      ojOld: getExportCount(path.join(repoRoot, 'frontend/src/pages/oj/api.js')),
      ojNew: getExportCount(path.join(repoRoot, 'frontend/src/pages/oj/api.js')),
      adminOld: getExportCount(path.join(repoRoot, 'frontend/src/pages/admin/api.js')),
      adminNew: getExportCount(path.join(repoRoot, 'frontend/src/pages/admin/api.js'))
    },
    categoryCounts: entries.reduce((acc, entry) => {
      acc[entry.category] = (acc[entry.category] || 0) + 1
      return acc
    }, {})
  }

  fs.writeFileSync(reportJson, `${JSON.stringify({ summary, entries }, null, 2)}\n`, 'utf8')
  fs.writeFileSync(reportMd, buildMarkdown(summary, entries), 'utf8')

  console.log(`[static-audit] wrote ${reportJson}`)
  console.log(`[static-audit] wrote ${reportMd}`)
  console.log(`[static-audit] diff files=${summary.diffCount}`)
}

main()
