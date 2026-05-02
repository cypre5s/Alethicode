import { createStore } from 'vuex'
import user from './modules/user'
import problem from './modules/problem'
import api from '@oj/api'
import types from './types'
import { FRONTEND_ENV } from '@/utils/runtimeEnv'

const debug = FRONTEND_ENV.isDevelopment

const rootState = {
  website: {},
  modalStatus: {
    mode: 'login', // or 'register',
    visible: false
  }
}

const rootGetters = {
  'website' (state) {
    return state.website
  },
  'modalStatus' (state) {
    return state.modalStatus
  }
}

const rootMutations = {
  [types.UPDATE_WEBSITE_CONF] (state, payload) {
    state.website = payload.websiteConfig
  },
  [types.CHANGE_MODAL_STATUS] (state, {mode, visible}) {
    if (mode !== undefined) {
      state.modalStatus.mode = mode
    }
    if (visible !== undefined) {
      state.modalStatus.visible = visible
    }
  }
}

const rootActions = {
  getWebsiteConfig ({commit}) {
    api.getWebsiteConf().then(res => {
      commit(types.UPDATE_WEBSITE_CONF, {
        websiteConfig: res.data.data
      })
    })
  },
  changeModalStatus ({commit}, payload) {
    commit(types.CHANGE_MODAL_STATUS, payload)
  },
  changeDomTitle ({state}, payload) {
    const websiteShortcut = state.website.website_name_shortcut || 'Alethicode'
    const routeTitle = payload && Object.prototype.hasOwnProperty.call(payload, 'title')
      ? payload.title
      : ''
    window.document.title = routeTitle ? `${websiteShortcut} | ${routeTitle}` : websiteShortcut
  }
}

export default createStore({
  modules: {
    user,
    problem
  },
  state: rootState,
  getters: rootGetters,
  mutations: rootMutations,
  actions: rootActions,
  strict: debug
})

export { types }
