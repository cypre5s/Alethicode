/**
 * XSS 清洗测试
 * 校验 sanitize 去除危险内容并保留安全 HTML
 */

const fs = require('fs')
const path = require('path')
const babel = require('@babel/core')

// 模拟 DOMPurify（jsdom 无原生支持）
jest.mock('dompurify', () => ({
  addHook: jest.fn(),
  sanitize: (html, opts) => {
    if (!html) return ''
    const allowed = opts.ALLOWED_TAGS || []
    // 简化 mock：移除 script 及事件处理器
    let result = html
      .replace(/<script\b[^<]*(?:(?!<\/script>)<[^<]*)*<\/script>/gi, '')
      .replace(/\s*on\w+\s*=\s*"[^"]*"/gi, '')
      .replace(/\s*on\w+\s*=\s*'[^']*'/gi, '')
    // 移除非白名单标签
    result = result.replace(/<\/?(\w+)(\s[^>]*)?\/?>/g, (match, tag) => {
      return allowed.includes(tag.toLowerCase()) ? match : ''
    })
    return result
  }
}))

function loadSanitizeModule() {
  const filePath = path.resolve(__dirname, '../../src/utils/sanitize.js')
  const source = fs.readFileSync(filePath, 'utf8')
  const transformed = babel.transformSync(source, {
    filename: filePath,
    presets: [require.resolve('@babel/preset-env')]
  })
  const module = { exports: {} }
  // eslint-disable-next-line no-new-func
  const fn = new Function('module', 'exports', 'require', transformed.code)
  fn(module, module.exports, require)
  return module.exports
}

const { sanitize } = loadSanitizeModule()

describe('sanitize', () => {
  test('returns empty string for falsy input', () => {
    expect(sanitize(null)).toBe('')
    expect(sanitize(undefined)).toBe('')
    expect(sanitize('')).toBe('')
  })

  test('preserves safe HTML tags', () => {
    const input = '<p>Hello <strong>World</strong></p>'
    const result = sanitize(input)
    expect(result).toContain('<p>')
    expect(result).toContain('<strong>')
  })

  test('strips script tags', () => {
    const input = '<p>Safe</p><script>alert("xss")</script>'
    const result = sanitize(input)
    expect(result).not.toContain('<script>')
    expect(result).not.toContain('alert')
    expect(result).toContain('Safe')
  })

  test('strips event handlers', () => {
    const input = '<img src="x" onerror="alert(1)">'
    const result = sanitize(input)
    expect(result).not.toContain('onerror')
    expect(result).not.toContain('alert')
  })

  test('preserves code blocks', () => {
    const input = '<pre><code>print("hello")</code></pre>'
    const result = sanitize(input)
    expect(result).toContain('<pre>')
    expect(result).toContain('<code>')
  })

  test('preserves table elements', () => {
    const input = '<table><tr><td>Data</td></tr></table>'
    const result = sanitize(input)
    expect(result).toContain('<table>')
    expect(result).toContain('<td>')
  })

  test('strips iframe tags', () => {
    const input = '<iframe src="evil.com"></iframe>'
    const result = sanitize(input)
    expect(result).not.toContain('<iframe')
  })

  test('strips form tags', () => {
    const input = '<form action="evil.com"><input type="text"></form>'
    const result = sanitize(input)
    expect(result).not.toContain('<form')
  })
})
