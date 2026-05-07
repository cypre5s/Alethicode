/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource(relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('learner profile drawer contracts', () => {
  test('profile API module exposes the four P1 dashboard endpoints with correct verbs', () => {
    const apiSource = readSource('../../src/pages/oj/api/profile.js')
    expect(apiSource).toContain("ajax('ai/tutor/profile/me', 'get')")
    expect(apiSource).toContain("ajax('ai/tutor/profile/me/preferences', 'patch'")
    expect(apiSource).toContain("ajax('ai/tutor/profile/me/refresh', 'post')")
    expect(apiSource).toContain("ajax('ai/tutor/profile/me/summary/override', 'post'")
  })

  test('useProfileApi composable wires the four backend operations and never duplicates global state', () => {
    const composableSource = readSource('../../src/composables/useProfileApi.js')
    expect(composableSource).toContain('api.getMyProfile()')
    expect(composableSource).toContain('api.refreshProfileSummary()')
    expect(composableSource).toContain('api.updateProfilePreferences(enabled)')
    expect(composableSource).toContain('api.overrideProfileSummary(text)')

    expect(composableSource).toContain('export function useProfileApi()')
    // ref 必须在工厂内部创建，避免泄漏到全局状态。
    const refDeclarations = composableSource.match(/const (profile|loading|saving|error) = ref\(/g) || []
    expect(refDeclarations.length).toBe(4)
  })

  test('useProfileApi normalizes snake_case profile payloads from Spring Jackson', () => {
    const composableSource = readSource('../../src/composables/useProfileApi.js')
    const apiSource = readSource('../../src/pages/oj/api/profile.js')
    expect(composableSource).toContain('normalizeProfilePayload')
    expect(composableSource).toContain('personalization_enabled')
    expect(composableSource).toContain('personalizationEnabled: raw.personalization_enabled !== false')
    expect(composableSource).toContain('narrative_summary')
    expect(composableSource).toContain('learning_style')
    expect(composableSource).toContain('is_user_overridden')
    expect(composableSource).toContain('stats_30d')
    expect(apiSource).toContain('data: { personalization_enabled: personalizationEnabled }')
    expect(apiSource).toContain('data: { summary_text: summaryText }')
  })

  test('ProfileDrawer surfaces personalization toggle, summary editing, weak KCs, and stats sections', () => {
    const drawerSource = readSource('../../src/pages/oj/views/problem/profile/ProfileDrawer.vue')
    expect(drawerSource).toContain('我的学习画像')
    expect(drawerSource).toContain('个性化推理')
    expect(drawerSource).toContain('近 30 天学习摘要')
    expect(drawerSource).toContain('教学风格偏好')
    expect(drawerSource).toContain('待巩固知识点')
    expect(drawerSource).toContain('已掌握知识点')
    expect(drawerSource).toContain('近期高频错误')
    expect(drawerSource).toContain('30 天数据')
    expect(drawerSource).toContain("ac_rate_30d: '近 30 天通过率'")
    expect(drawerSource).toContain("problems_ac_30d: '近 30 天通过题数'")
    expect(drawerSource).toContain("problems_attempted_30d: '近 30 天尝试题数'")
    expect(drawerSource).toContain('learningStyleLabel')
    expect(drawerSource).toContain('learningStyleKey')
    expect(drawerSource).not.toContain("{{ profile.learningStyle?.label ?? '-' }} ({{ profile.learningStyle?.key ?? '-' }})")

    expect(drawerSource).toContain('updatePreferences')
    expect(drawerSource).toContain('refresh()')
    expect(drawerSource).toContain('overrideSummary(')
    expect(drawerSource).toContain("import { useProfileApi } from '@/composables/useProfileApi'")
  })

  test('UnifiedAgentPanel mounts ProfileDrawer and exposes a 我的学习画像 entry button', () => {
    const panelSource = readSource('../../src/pages/oj/views/problem/UnifiedAgentPanel.vue')
    expect(panelSource).toContain("import ProfileDrawer from './profile/ProfileDrawer.vue'")
    expect(panelSource).toContain('<ProfileDrawer v-model="profileDrawerVisible"')
    expect(panelSource).toContain("title=\"我的学习画像\"")
    expect(panelSource).toContain('profileDrawerVisible: false')
    expect(panelSource).toContain('@click="profileDrawerVisible = true"')
  })
})
