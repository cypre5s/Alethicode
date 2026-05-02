/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource(relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('admin AI observability dashboard contract', () => {
  test('system admin menu should expose observability and system monitor entries', () => {
    const source = readSource('../../src/pages/admin/components/SideMenu.vue')

    expect(source).toContain('<el-menu-item index="/secrets/observability">AI 助教工作台</el-menu-item>')
    expect(source).toContain('<el-menu-item index="/secrets/system-monitor">系统监控</el-menu-item>')
  })

  test('admin router should register observability routes and deny teacher access', () => {
    const source = readSource('../../src/pages/admin/router.js')

    expect(source).toContain("path: '/secrets/observability'")
    expect(source).toContain("name: 'secrets-observability'")
    expect(source).toContain('component: ObservabilityDashboard')
    expect(source).toContain("path: '/secrets/system-monitor'")
    expect(source).toContain("name: 'secrets-system-monitor'")
    expect(source).toContain('component: SystemMonitor')
    expect(source).toContain("\n  'secrets-observability',")
    expect(source).toContain("\n  'secrets-system-monitor',")
  })

  test('observability dashboard should focus on overview content only', () => {
    const source = readSource('../../src/pages/admin/views/general/ObservabilityDashboard.vue')

    expect(source).toContain('title="AI 助教工作台"')
    expect(source).toContain('核心指标')
    expect(source).toContain('Agent 维度表现')
    expect(source).toContain('质量失败桶')
    expect(source).not.toContain('label="系统监控" name="grafana"')
  })

  test('observability dashboard should wire to overview and evaluations APIs', () => {
    const source = readSource('../../src/pages/admin/views/general/ObservabilityDashboard.vue')

    expect(source).toContain('api.getAgentsOverview(')
    expect(source).toContain('api.getEvaluationsDashboard(')
    expect(source).toContain('translateAgentLabel')
    expect(source).toContain('translateFailureBucket')
  })

  test('system monitor page should preserve the Grafana iframe fallback', () => {
    const source = readSource('../../src/pages/admin/views/general/SystemMonitor.vue')

    expect(source).toContain(':src="grafanaPath"')
    expect(source).toContain('api.getObservabilityConfig()')
    expect(source).toContain('normalizeGrafanaUrl (url)')
    expect(source).toContain('this.grafanaPath = this.normalizeGrafanaUrl(data.grafana_url)')
    expect(source).toContain("window.open(resolved.href, '_blank', 'noopener')")
  })

  test('admin api module should expose the 4 observability endpoints', () => {
    const source = readSource('../../src/pages/admin/api.js')

    expect(source).toContain("ajax('admin/ai/agents/overview', 'get'")
    expect(source).toContain("ajax('admin/ai/evaluations/dashboard', 'get'")
    expect(source).toContain("ajax('admin/ai/behavior-analytics', 'get'")
    expect(source).toContain("ajax('admin/super/observability-config', 'get'")
  })

  test('overview should include merged quality metrics and centered table labels', () => {
    const source = readSource('../../src/pages/admin/views/general/ObservabilityDashboard.vue')

    expect(source).toContain('最新平均分')
    expect(source).toContain('最近评测样本')
    expect(source).toContain('header-align="center"')
    expect(source).toContain('ref="evalTrendChart"')
  })

  test('overview should localize runtime enums and format trends by hour', () => {
    const source = readSource('../../src/pages/admin/views/general/ObservabilityDashboard.vue')

    expect(source).toContain("KNOWLEDGE_REVIEW: '知识点回顾'")
    expect(source).toContain("SCHEMA_VIOLATION: '卡片结构不符合规范'")
    expect(source).toContain('qualityTrendSubtitle')
    expect(source).toContain('formatHourLabel')
    expect(source).toContain("return `${month}-${day} ${hour}:00`")
  })
})
