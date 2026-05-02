/**
 * 技能画像：标签掌握度 / 最近错题 / 雷达图 / 练习热力图 / 推荐题。
 */

import { ajax } from './shared'

export default {
  getTagProgress(userId, params = {}) {
    return ajax('problems/tag-progress', 'get', {
      params: {
        user_id: userId,
        ...params
      }
    })
  },
  getRecentWrong(userId, limit = 5) {
    return ajax(`submissions/recent-wrong?user_id=${userId}&limit=${limit}`, 'get')
  },
  getSkillRadar(userId, params) {
    return ajax(`ai/skill/radar?user_id=${userId}`, 'get', { params })
  },
  getPracticeHeatmap(userId, params) {
    return ajax(`ai/skill/heatmap?user_id=${userId}`, 'get', { params })
  },
  getProblemRecommendations(userId, strategy = 'balanced', count = 10) {
    return ajax(`ai/skill/recommend?user_id=${userId}&strategy=${strategy}&count=${count}`, 'get')
  }
}
