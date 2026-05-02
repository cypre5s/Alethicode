import types from '../types'
import api from '@oj/api'
import storage from '@/utils/storage'
import i18n from '@/i18n'
import { STORAGE_KEY, USER_TYPE, PROBLEM_PERMISSION } from '@/utils/constants'

const state = {
  profile: {}
}

const PROFILE_KEYS_TO_OMIT = new Set(['acm_problems_status', 'oi_problems_status'])

function stripHeavyProfileFields (profile) {
  if (!profile || typeof profile !== 'object') {
    return {}
  }
  const sanitized = {}
  Object.keys(profile).forEach((key) => {
    if (!PROFILE_KEYS_TO_OMIT.has(key)) {
      sanitized[key] = profile[key]
    }
  })
  return sanitized
}

function normalizeProfile (profile) {
  const normalizedProfile = stripHeavyProfileFields(profile)
  if (!Object.keys(normalizedProfile).length) {
    return {}
  }
  if (normalizedProfile.user && typeof normalizedProfile.user === 'object') {
    return normalizedProfile
  }
  if (!normalizedProfile.id) {
    return normalizedProfile
  }
  return Object.assign({}, normalizedProfile, {
    user: {
      id: normalizedProfile.id,
      username: normalizedProfile.username,
      email: normalizedProfile.email,
      admin_type: normalizedProfile.admin_type,
      problem_permission: normalizedProfile.problem_permission
    }
  })
}

const getters = {
  user: state => state.profile.user || {},
  profile: state => state.profile,
  isAuthenticated: (state, getters) => {
    return !!getters.user.id
  },
  isAdminRole: (state, getters) => {
    return getters.user.admin_type === USER_TYPE.ADMIN ||
      getters.user.admin_type === USER_TYPE.TEACHER
  },
  hasProblemPermission: (state, getters) => {
    return getters.user.problem_permission !== PROBLEM_PERMISSION.NONE
  }
}

const mutations = {
  [types.CHANGE_PROFILE] (state, { profile }) {
    const normalized = normalizeProfile(profile)
    state.profile = normalized
    if (normalized.language) {
      i18n.locale = normalized.language
    }
    storage.set(STORAGE_KEY.AUTHED, !!(normalized.user && normalized.user.id))
  }
}

const actions = {
  getProfile ({ commit }) {
    return new Promise((resolve, reject) => {
      api.getUserInfo().then(res => {
        commit(types.CHANGE_PROFILE, {
          profile: res.data.data || {}
        })
        resolve(res)
      }, err => {
        reject(err)
      })
    })
  },
  clearProfile ({ commit }) {
    commit(types.CHANGE_PROFILE, {
      profile: {}
    })
    storage.remove(STORAGE_KEY.AUTHED)
    // 2C4G 优化（2026-04-30）：登出/会话失效时清掉用户私有 Service Worker 缓存，
    // 避免下一个用户读到上个用户在浏览器本地缓存的提交列表 / 个人资料。
    if (typeof window !== 'undefined' && typeof window.caches !== 'undefined') {
      ['api-submission', 'api-profile'].forEach(name => {
        window.caches.delete(name).catch(() => {})
      })
    }
  }
}

export default {
  state,
  getters,
  actions,
  mutations
}
