/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource(relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('visualize renderer contracts', () => {
  test('VisualizeRenderer routes by format and exposes intent / source role', () => {
    const source = readSource('../../src/pages/oj/views/problem/cards/visualize/VisualizeRenderer.vue')

    expect(source).toContain("v-else-if=\"format === 'mermaid'\"")
    expect(source).toContain("v-else-if=\"format === 'chart'\"")
    expect(source).toContain("v-else-if=\"format === 'svg'\"")

    expect(source).toContain("import MermaidRenderer from './MermaidRenderer.vue'")
    expect(source).toContain("import ChartRenderer from './ChartRenderer.vue'")
    expect(source).toContain("import SvgRenderer from './SvgRenderer.vue'")

    expect(source).toContain('intent ()')
    expect(source).toContain('format ()')
    expect(source).toContain('payload ()')
    expect(source).toContain('altText ()')
    expect(source).toContain('sourceRole ()')
  })

  test('MermaidRenderer hard-codes strict security level so LLM payload cannot mount XSS', () => {
    const source = readSource('../../src/pages/oj/views/problem/cards/visualize/MermaidRenderer.vue')

    expect(source).toContain("securityLevel: 'strict'")
    expect(source).toContain('suppressErrorRendering: true')
    expect(source).toContain('await mermaid.parse(source)')
    expect(source).toContain('renderMermaid')
    expect(source).toContain("error = 'Mermaid 渲染失败'")
    expect(source).toContain('zoomVisible')
    expect(source).toContain('openZoom')
    expect(source).toContain('svgDataUri')
    expect(source).toContain('<Teleport to="body">')
    expect(source).toContain('流程图放大预览')
  })

  test('ChartRenderer parses payload as JSON and destroys instance to avoid leaks', () => {
    const source = readSource('../../src/pages/oj/views/problem/cards/visualize/ChartRenderer.vue')

    expect(source).toContain('JSON.parse(text)')
    expect(source).toContain('destroyChart')
    expect(source).toContain('beforeUnmount')
    expect(source).toContain('chartInstance.destroy()')
  })

  test('SvgRenderer relies on backend sanitizer and never injects scripts itself', () => {
    const source = readSource('../../src/pages/oj/views/problem/cards/visualize/SvgRenderer.vue')

    // Frontend trust boundary: payload must be string from backend SvgSanitizer.
    expect(source).toContain("payload: {")
    expect(source).toContain("type: String,")

    // No dynamic script injection anywhere in the renderer.
    expect(source).not.toContain('eval(')
    expect(source).not.toContain('new Function')
    expect(source).not.toContain('document.write')

    // No additional unfiltered HTML sinks beyond v-html=svgText (which is sanitized upstream).
    const vHtmlMatches = source.match(/v-html\s*=/g) || []
    expect(vHtmlMatches.length).toBe(1)
  })
})
