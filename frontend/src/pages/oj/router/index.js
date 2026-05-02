import { createRouter, createWebHistory } from 'vue-router'
import routes from './routes'
import store from '../../../store'
import { notify } from '@/utils/notifications'

const router = createRouter({
  history: createWebHistory('/'),
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) {
      return savedPosition
    } else {
      return { x: 0, y: 0 }
    }
  },
  routes
})

const PROFILE_TTL_MS = 60_000
let authSyncing = null
let lastProfileFetchedAt = 0

function syncAuthState(force = false) {
  if (authSyncing) {
    return authSyncing
  }
  const now = Date.now()
  if (!force && store.getters.isAuthenticated && (now - lastProfileFetchedAt) < PROFILE_TTL_MS) {
    return Promise.resolve()
  }
  authSyncing = store.dispatch('getProfile')
    .then((res) => {
      lastProfileFetchedAt = Date.now()
      return res
    })
    .catch(() => {
      lastProfileFetchedAt = 0
      return store.dispatch('clearProfile')
    })
    .finally(() => {
      authSyncing = null
    })
  return authSyncing
}

const PUBLIC_PAGES = new Set([
  'login', 'register', 'apply-reset-password', 'reset-password', 'logout',
  'manual', 'maintenance'
])

router.beforeEach(async (to) => {
  const isPublicPage = PUBLIC_PAGES.has(to.name)
  const cachedAuthed = store.getters.isAuthenticated
  const profileFresh = cachedAuthed && (Date.now() - lastProfileFetchedAt) < PROFILE_TTL_MS

  if (profileFresh && !isPublicPage) {
    return true
  }
  if (isPublicPage && !cachedAuthed) {
    return true
  }

  notify.loadingStart()
  try {
    await syncAuthState()
    const authed = store.getters.isAuthenticated

    if (!authed && !isPublicPage) {
      return {
        name: 'login',
        query: { redirect: to.fullPath }
      }
    }

    if (authed && (to.name === 'login' || to.name === 'register')) {
      const redirect = typeof to.query.redirect === 'string' ? to.query.redirect : '/'
      return redirect
    }

    if (to.meta && to.meta.requiresAdmin && !store.getters.isAdminRole) {
      return { name: 'home' }
    }
    return true
  } catch (error) {
    throw error
  } finally {
    notify.loadingFinish()
  }
})

router.afterEach(() => {
  notify.loadingFinish()
})

router.onError(() => {
  notify.loadingFinish()
})

export default router
