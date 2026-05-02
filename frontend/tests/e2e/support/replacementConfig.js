const DEFAULT_VIEWPORT = {
  width: 1440,
  height: 900
}

function trimToNull(value) {
  if (typeof value !== 'string') {
    return null
  }
  const trimmed = value.trim()
  return trimmed.length > 0 ? trimmed : null
}

function normalizeBaseUrl(rawValue, fallback) {
  return (rawValue || fallback).replace(/\/+$/, '')
}

function resolveReplacementRuntimeConfig() {
  return {
    oldBaseUrl: normalizeBaseUrl(process.env.OLD_BASE_URL, 'http://127.0.0.1:8084'),
    newBaseUrl: normalizeBaseUrl(process.env.NEW_BASE_URL, 'http://127.0.0.1:8080'),
    username: trimToNull(process.env.E2E_USERNAME) || 'replacement_admin',
    password: trimToNull(process.env.E2E_PASSWORD) || 'ReplacementPass123!',
    email: trimToNull(process.env.E2E_EMAIL) || 'replacement_admin@example.com',
    adminType: trimToNull(process.env.E2E_ADMIN_TYPE) || 'Admin',
    postgresContainer: trimToNull(process.env.E2E_POSTGRES_CONTAINER) || 'java-oj-postgres',
    bootstrapBaseUrl: normalizeBaseUrl(process.env.E2E_BOOTSTRAP_BASE_URL, 'http://127.0.0.1:8081'),
    problemId: trimToNull(process.env.E2E_PROBLEM_ID),
    submissionId: trimToNull(process.env.E2E_SUBMISSION_ID),
    classroomId: trimToNull(process.env.E2E_CLASSROOM_ID),
    collabSessionId: trimToNull(process.env.E2E_COLLAB_SESSION_ID)
  }
}

function createRouteCatalog(runtimeConfig, seedData = {}) {
  const problemId = seedData.problemId || runtimeConfig.problemId || '1'
  const submissionId = runtimeConfig.submissionId
  const classroomId = seedData.classroomId || runtimeConfig.classroomId
  const collabSessionId = seedData.collabSessionId || runtimeConfig.collabSessionId

  const publicRoutes = [
    {
      name: 'oj-login',
      area: 'oj',
      authMode: 'public',
      path: '/login',
      readySelector: '.login-page',
      expectedText: 'Sign in'
    },
    {
      name: 'oj-register',
      area: 'oj',
      authMode: 'public',
      path: '/register',
      readySelector: '.register-page',
      expectedText: 'Sign up'
    },
    {
      name: 'oj-root-redirect',
      area: 'oj',
      authMode: 'public',
      path: '/',
      readySelector: '.login-page',
      expectedText: 'Sign in'
    },
    {
      name: 'oj-problem-list',
      area: 'oj',
      authMode: 'public',
      path: '/problem',
      readySelector: '.login-page',
      expectedText: 'Sign in'
    },
    {
      name: 'oj-problem-detail',
      area: 'oj',
      authMode: 'public',
      path: `/problem/${problemId}`,
      readySelector: '.login-page',
      expectedText: 'Sign in'
    },
    {
      name: 'oj-submission-list',
      area: 'oj',
      authMode: 'public',
      path: '/status',
      readySelector: '.login-page',
      expectedText: 'Sign in'
    },
    {
      name: 'oj-reset-password-apply',
      area: 'oj',
      authMode: 'public',
      path: '/apply-reset-password',
      readySelector: '.recover-page'
    },
    {
      name: 'oj-404',
      area: 'oj',
      authMode: 'public',
      path: '/this-route-should-not-exist',
      readySelector: '.login-page',
      expectedText: 'Sign in'
    },
    {
      name: 'admin-login',
      area: 'admin',
      authMode: 'public',
      path: '/admin/login',
      readySelector: '.login-wrapper'
    }
  ]

  const userRoutes = [
    {
      name: 'oj-user-home',
      area: 'oj',
      authMode: 'user',
      path: '/user-home',
      readySelector: '.dash-layout'
    },
    {
      name: 'oj-learner-notebook',
      area: 'oj',
      authMode: 'user',
      path: '/learner-notebook',
      readySelector: '.notebook-page',
      expectedText: '个性化错题本'
    },
    {
      name: 'oj-setting-profile',
      area: 'oj',
      authMode: 'user',
      path: '/setting/profile',
      readySelector: '.settings-page-wrap'
    },
    {
      name: 'oj-setting-security',
      area: 'oj',
      authMode: 'user',
      path: '/setting/security',
      readySelector: '.settings-page-wrap'
    },
    {
      name: 'oj-classroom-list',
      area: 'oj',
      authMode: 'user',
      path: '/classroom',
      readySelector: '.classroom-view'
    },
    {
      name: 'oj-classroom-join',
      area: 'oj',
      authMode: 'user',
      path: '/classroom/join',
      readySelector: '.join-classroom-container'
    }
  ]

  if (submissionId) {
    userRoutes.push({
      name: 'oj-submission-detail',
      area: 'oj',
      authMode: 'user',
      path: `/status/${submissionId}/`,
      readySelector: '.submission-details-container'
    })
  }

  if (classroomId) {
    userRoutes.push({
      name: 'oj-classroom-detail',
      area: 'oj',
      authMode: 'user',
      path: `/classroom/${classroomId}`,
      readySelector: '.classroom-detail-container'
    })
  }

  if (classroomId && collabSessionId) {
    userRoutes.push({
      name: 'oj-classroom-collab',
      area: 'oj',
      authMode: 'user',
      path: `/classroom/${classroomId}/collab/${collabSessionId}`,
      readySelector: '.collaborative-coding'
    })
  }

  const adminRoutes = [
    {
      name: 'admin-dashboard',
      area: 'admin',
      authMode: 'admin',
      path: '/admin/',
      readySelector: '.dashboard-wrapper'
    },
    {
      name: 'admin-announcement',
      area: 'admin',
      authMode: 'admin',
      path: '/admin/announcement',
      readySelector: '.announcement.view'
    },
    {
      name: 'admin-user',
      area: 'admin',
      authMode: 'admin',
      path: '/admin/user',
      readySelector: '.view'
    },
    {
      name: 'admin-judge-server',
      area: 'admin',
      authMode: 'admin',
      path: '/admin/judge-server',
      readySelector: '.view'
    },
    {
      name: 'admin-problem-list',
      area: 'admin',
      authMode: 'admin',
      path: '/admin/problems',
      readySelector: '.view'
    },
    {
      name: 'admin-problem-batch-ops',
      area: 'admin',
      authMode: 'admin',
      path: '/admin/problem/batch_ops',
      readySelector: '.el-table'
    },
    {
      name: 'admin-ai-variant-review',
      area: 'admin',
      authMode: 'admin',
      path: '/admin/ai-variant-review',
      readySelector: '.el-table, .view'
    },
    {
      name: 'admin-kc-management',
      area: 'admin',
      authMode: 'admin',
      path: '/admin/kc-management',
      readySelector: '.el-table, .view'
    },
  ]

  const websocketRoutes = [
    {
      name: 'oj-ai-workflow-ws',
      area: 'oj',
      authMode: 'user',
      path: `/problem/${problemId}`,
      readySelector: '.problem-container',
      matchPattern: /\/ws\/workflow\/[^/?#]+$/,
      setupKey: 'open-agent-panel'
    }
  ]

  if (classroomId) {
    websocketRoutes.push({
      name: 'oj-classroom-monitor-ws',
      area: 'oj',
      authMode: 'user',
      path: `/classroom/${classroomId}`,
      readySelector: '.classroom-detail-container',
      matchPattern: new RegExp(`/ws/classroom/monitor/${classroomId}/?$`),
      setupKey: 'open-monitor-tab'
    })
  }

  if (classroomId && collabSessionId) {
    websocketRoutes.push({
      name: 'oj-classroom-collab-ws',
      area: 'oj',
      authMode: 'user',
      path: `/classroom/${classroomId}/collab/${collabSessionId}`,
      readySelector: '.collaborative-coding',
      matchPattern: new RegExp(`/ws/classroom/collab/${collabSessionId}/?$`)
    })
  }

  return {
    publicRoutes,
    userRoutes,
    adminRoutes,
    websocketRoutes
  }
}

module.exports = {
  DEFAULT_VIEWPORT,
  resolveReplacementRuntimeConfig,
  createRouteCatalog
}
