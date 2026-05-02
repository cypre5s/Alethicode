/* eslint-env jest */

/**
 * `manualPretextLayout.js` 源契约测试。
 *
 * 项目使用 jest 23 不会执行 babel.config.js 转换 ES module，无法直接 require。
 * 故采用源代码 pattern matching 来验证封装层的关键契约：
 *   - 仅暴露 measureBubble + measureCommandItem 两个函数
 *   - 命中 pretext.prepareWithSegments / measureLineStats / walkLineRanges
 *   - 失败时回退到 canvas.measureText
 *   - 空输入早返回 0/0
 */

const fs = require('fs')
const path = require('path')

const SOURCE = fs.readFileSync(
  path.resolve(__dirname, '../../src/pages/oj/views/manual/manualPretextLayout.js'),
  'utf8'
)

describe('manualPretextLayout.js · 公共 API 契约', () => {
  test('exports measureBubble and measureCommandItem (no other public functions)', () => {
    expect(SOURCE).toMatch(/export function measureBubble\s*\(/)
    expect(SOURCE).toMatch(/export function measureCommandItem\s*\(/)
    const exports = [...SOURCE.matchAll(/export\s+(?:function|const)\s+(\w+)/g)].map(m => m[1])
    expect(exports.sort()).toEqual(['measureBubble', 'measureCommandItem'])
  })

  test('imports pretext as namespace object to preserve tree-shaking room', () => {
    expect(SOURCE).toMatch(/import\s+\*\s+as\s+pretext\s+from\s+['"]@chenglou\/pretext['"]/)
  })

  test('depends only on @chenglou/pretext (依赖收敛 per plan §9)', () => {
    const imports = [...SOURCE.matchAll(/import [\s\S]+? from ['"]([^'"]+)['"]/g)].map(m => m[1])
    expect(imports).toEqual(['@chenglou/pretext'])
  })
})

describe('manualPretextLayout.js · pretext 调用契约', () => {
  test('measureBubble uses prepareWithSegments + measureLineStats path', () => {
    expect(SOURCE).toMatch(/pretext\.prepareWithSegments/)
    expect(SOURCE).toMatch(/pretext\.measureLineStats/)
  })

  test('measureCommandItem uses prepareWithSegments + walkLineRanges path', () => {
    expect(SOURCE).toMatch(/pretext\.walkLineRanges/)
  })

  test('walkLineRanges result is checked as Array before reading length', () => {
    expect(SOURCE).toMatch(/Array\.isArray\(ranges\)/)
  })
})

describe('manualPretextLayout.js · 早返回与守卫', () => {
  test('measureBubble early-returns 0/0 on falsy input', () => {
    expect(SOURCE).toMatch(/measureBubble\s*\([\s\S]*?if\s*\(!text\)[\s\S]*?return\s*\{\s*width:\s*0,\s*lineCount:\s*0\s*\}/)
  })

  test('measureCommandItem early-returns when text is falsy', () => {
    expect(SOURCE).toMatch(/measureCommandItem\s*\([\s\S]*?if\s*\(!text\)[\s\S]*?return\s*\{[^}]*lineCount:\s*0[^}]*\}/)
  })

  test('measureCommandItem clamps maxWidth >= 1 in the fallback path', () => {
    expect(SOURCE).toMatch(/Math\.max\(1,\s*maxWidth\)/)
  })
})

describe('manualPretextLayout.js · canvas fallback', () => {
  test('declares fallbackMeasure that uses canvas.measureText', () => {
    expect(SOURCE).toMatch(/function fallbackMeasure\s*\(text,\s*font\)/)
    expect(SOURCE).toMatch(/measureText\(ln\)\.width/)
  })

  test('caches a single canvas across calls (no leak)', () => {
    expect(SOURCE).toMatch(/_canvasCache\s*=\s*null/)
    expect(SOURCE).toMatch(/_canvasCache\s*=\s*document\.createElement\(['"]canvas['"]\)/)
  })

  test('falls back to length-based estimate when document is undefined (SSR safety)', () => {
    expect(SOURCE).toMatch(/typeof document\s*===\s*['"]undefined['"]/)
  })

  test('counts \\n delimiters for multi-line bubble width detection', () => {
    expect(SOURCE).toMatch(/String\(text\s*\|\|\s*''\)\.split\(['"]\\n['"]\)/)
  })

  test('returns longest line width across multi-line input', () => {
    expect(SOURCE).toMatch(/if\s*\(m\s*>\s*longest\)\s*longest\s*=\s*m/)
  })
})

describe('manualPretextLayout.js · 异常容错', () => {
  test('callPretextSafe try/catch wraps every pretext call to avoid breaking the UI', () => {
    expect(SOURCE).toMatch(/function callPretextSafe\s*\(/)
    expect(SOURCE).toMatch(/try\s*\{\s*return fn\(\)/)
    expect(SOURCE).toMatch(/catch\s*\(err\)\s*\{[\s\S]*?return fallback\(\)/)
  })

  test('catch path emits a console.warn instead of swallowing silently', () => {
    expect(SOURCE).toMatch(/console\.warn\(['"]\[manualPretextLayout\]/)
  })
})

describe('manualPretextLayout.js · 数值规范', () => {
  test('measureBubble ceilings the longestLineWidth for whole-pixel layout', () => {
    expect(SOURCE).toMatch(/width:\s*Math\.ceil\(stats\.longestLineWidth\)/)
    expect(SOURCE).toMatch(/width:\s*Math\.ceil\(f\.width\)/)
  })

  test('measureCommandItem returns willOverflow boolean', () => {
    expect(SOURCE).toMatch(/willOverflow:\s*ranges\.length\s*>\s*1/)
    expect(SOURCE).toMatch(/willOverflow:\s*f\.width\s*>\s*maxWidth/)
  })
})
