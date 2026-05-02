/**
 * @chenglou/pretext 的封装层。
 *
 * 仅用于两个对"文字贴合精度"敏感的场景：
 *   - 奶蛙气泡盒尺寸
 *   - cmd+K 命令面板搜索结果项的剩余宽度折行决策
 *
 * 其余文字布局走浏览器 native flow（text-wrap: balance / pretty）。
 */

import * as pretext from '@chenglou/pretext'

let _canvasCache = null

function ensureCanvas () {
  if (typeof document === 'undefined') return null
  if (!_canvasCache) {
    _canvasCache = document.createElement('canvas')
  }
  return _canvasCache.getContext('2d')
}

function fallbackMeasure (text, font) {
  const ctx = ensureCanvas()
  if (!ctx) return { lineCount: 1, width: text.length * 12, longestLine: text.length * 12 }
  ctx.font = font
  const lines = String(text || '').split('\n')
  let longest = 0
  for (const ln of lines) {
    const m = ctx.measureText(ln).width
    if (m > longest) longest = m
  }
  return {
    lineCount: lines.length,
    width: longest,
    longestLine: longest
  }
}

function callPretextSafe (fn, fallback) {
  try {
    return fn()
  } catch (err) {
    console.warn('[manualPretextLayout] pretext call failed, fallback used:', err)
    return fallback()
  }
}

/**
 * 计算奶蛙气泡盒在给定字体下的最佳尺寸。
 * @param {string} text 多行字符串
 * @param {string} font css font shorthand，例如 '14px/1.4 var(--font-sans)'
 * @returns {{ width: number, lineCount: number }}
 */
export function measureBubble (text, font) {
  if (!text) {
    return { width: 0, lineCount: 0 }
  }
  return callPretextSafe(
    () => {
      const segments = pretext.prepareWithSegments
        ? pretext.prepareWithSegments(String(text))
        : { text: String(text) }
      const stats = pretext.measureLineStats
        ? pretext.measureLineStats(segments, { font })
        : null
      if (stats && typeof stats.longestLineWidth === 'number') {
        return {
          width: Math.ceil(stats.longestLineWidth),
          lineCount: stats.lineCount || String(text).split('\n').length
        }
      }
      const f = fallbackMeasure(text, font)
      return { width: Math.ceil(f.width), lineCount: f.lineCount }
    },
    () => {
      const f = fallbackMeasure(text, font)
      return { width: Math.ceil(f.width), lineCount: f.lineCount }
    }
  )
}

/**
 * 计算命令面板搜索项在剩余宽度下会折成几行；用于决定省略号 / 换行展示。
 * @param {string} text 单行文本
 * @param {string} font
 * @param {number} maxWidth 容器剩余可用宽度（px）
 * @returns {{ lineCount: number, willOverflow: boolean }}
 */
export function measureCommandItem (text, font, maxWidth) {
  if (!text) return { lineCount: 0, willOverflow: false }
  return callPretextSafe(
    () => {
      const segments = pretext.prepareWithSegments
        ? pretext.prepareWithSegments(String(text))
        : { text: String(text) }
      const ranges = pretext.walkLineRanges
        ? pretext.walkLineRanges(segments, { font, maxWidth })
        : null
      if (ranges && Array.isArray(ranges)) {
        return {
          lineCount: ranges.length,
          willOverflow: ranges.length > 1
        }
      }
      const f = fallbackMeasure(text, font)
      return {
        lineCount: Math.max(1, Math.ceil(f.width / Math.max(1, maxWidth))),
        willOverflow: f.width > maxWidth
      }
    },
    () => {
      const f = fallbackMeasure(text, font)
      return {
        lineCount: Math.max(1, Math.ceil(f.width / Math.max(1, maxWidth))),
        willOverflow: f.width > maxWidth
      }
    }
  )
}
