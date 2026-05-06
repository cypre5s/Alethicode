/**
 * L99 Open Learner Twin：学习者孪生相关接口。
 */

import { ajax } from './shared'

export default {
  getLearningTimeline(params) {
    return ajax('twin/timeline', 'get', { params })
  },
  getTwinKcGalaxy(params) {
    return ajax('twin/kc-galaxy', 'get', { params })
  },
  getTwinPersona() {
    return ajax('twin/persona', 'get')
  },
  overrideTwinPersona(data) {
    return ajax('twin/persona', 'post', { data })
  },
  refreshTwinPersona() {
    return ajax('twin/persona/refresh', 'post')
  },
  feedbackTwinPersona(data) {
    return ajax('twin/persona/feedback', 'post', { data })
  },
  getMuseumPins() {
    return ajax('twin/museum/pins', 'get')
  },
  pinMuseumMemory(data) {
    return ajax('twin/museum/pins', 'post', { data })
  },
  updateMuseumPin(pinId, data) {
    return ajax(`twin/museum/pins/${pinId}`, 'patch', { data })
  },
  unpinMuseumMemory(pinId) {
    return ajax(`twin/museum/pins/${pinId}`, 'delete')
  },
  getTwinHealth() {
    return ajax('twin/health', 'get')
  }
}
