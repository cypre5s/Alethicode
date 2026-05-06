import { createRouter, createWebHistory } from 'vue-router'
import store, { types } from '@/store'
import { getHttpClient } from '@/api/httpClient'
// 引入 view 组件
import {
  Announcement, Home, JudgeServer, Login,
  Problem, ProblemList, User, PruneTestCase, ProblemImportOrExport,
  AIVariantReview, KCManagement,
  DomainLensAdmin,
  LanguagePackInit,
  SecretsAiConfig, ObservabilityDashboard, SystemMonitor, SecretsSystemPaths, SecretsInfra,
  BetaFeedback, UsageStats
} from './views'

const router = createRouter({
  history: createWebHistory('/admin/'),
  scrollBehavior: () => ({ y: 0 }),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: Login
    },
    {
      path: '/',
      component: Home,
      children: [
        {
          path: '',
          redirect: { name: 'problem-list' }
        },
        {
          path: '/announcement',
          name: 'announcement',
          component: Announcement
        },
        {
          path: '/user',
          name: 'user',
          component: User
        },
        {
          path: '/judge-server',
          name: 'judge-server',
          component: JudgeServer
        },
        {
          path: '/prune-test-case',
          name: 'prune-test-case',
          component: PruneTestCase
        },
        {
          path: '/problems',
          name: 'problem-list',
          component: ProblemList
        },
        {
          path: '/problem/create',
          name: 'create-problem',
          component: Problem
        },
        {
          path: '/problem/edit/:problemId',
          name: 'edit-problem',
          component: Problem
        },
        {
          path: '/problem/batch_ops',
          name: 'problem_batch_ops',
          component: ProblemImportOrExport
        },
        {
          path: '/ai-variant-review',
          name: 'ai-variant-review',
          component: AIVariantReview
        },
        {
          path: '/domain-lens',
          name: 'domain-lens-admin',
          component: DomainLensAdmin
        },
        {
          path: '/kc-management',
          name: 'kc-management',
          component: KCManagement
        },
        {
          path: '/language-pack-init',
          name: 'language-pack-init',
          component: LanguagePackInit
        },
        {
          path: '/secrets/ai',
          name: 'secrets-ai',
          component: SecretsAiConfig
        },
        {
          path: '/secrets/observability',
          name: 'secrets-observability',
          component: ObservabilityDashboard
        },
        {
          path: '/secrets/system-monitor',
          name: 'secrets-system-monitor',
          component: SystemMonitor
        },
        {
          path: '/secrets/paths',
          name: 'secrets-paths',
          component: SecretsSystemPaths
        },
        {
          path: '/secrets/infra',
          name: 'secrets-infra',
          component: SecretsInfra
        },
        {
          path: '/beta-feedback',
          name: 'beta-feedback',
          component: BetaFeedback
        },
        {
          path: '/usage-stats',
          name: 'usage-stats',
          component: UsageStats
        },
      ]
    },
    { path: '/:pathMatch(.*)*', redirect: '/login' }
  ]
})

const TEACHER_DENIED_ROUTE_NAMES = new Set([
  'user',
  'announcement',
  'judge-server',
  'prune-test-case',
  'secrets-ai',
  'secrets-observability',
  'secrets-system-monitor',
  'secrets-paths',
  'secrets-infra',
  'beta-feedback',
  'usage-stats'
])

function resolveAdminType(profile) {
  if (!profile || typeof profile !== 'object') {
    return ''
  }
  if (profile.user && typeof profile.user === 'object') {
    return profile.user.admin_type || ''
  }
  return profile.admin_type || ''
}

router.beforeEach(async (to, from, next) => {
  if (to.name === 'login') {
    next()
    return
  }

  let profile = store.getters.profile
  let adminType = resolveAdminType(profile)
  if (!adminType) {
    try {
      const res = await getHttpClient()({
        url: 'profile',
        method: 'get'
      })
      profile = res && res.data ? (res.data.data || {}) : {}
      store.commit(types.CHANGE_PROFILE, { profile })
      adminType = resolveAdminType(profile)
    } catch {
      next({ name: 'login' })
      return
    }
  }

  if (adminType === 'Teacher' && TEACHER_DENIED_ROUTE_NAMES.has(to.name)) {
    next({ name: 'problem-list' })
    return
  }

  next()
})

export default router
