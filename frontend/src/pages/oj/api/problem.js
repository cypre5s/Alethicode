/**
 * 题目相关：列表 / 详情 / 标签 / 统计 / 相关例题。
 */

import { ajax } from './shared'

export default {
  getProblemTagList(params = {}) {
    return ajax('problems/tags', 'get', { params })
  },
  getProblemList(offset, limit, searchParams) {
    let params = {
      paging: true,
      offset,
      limit
    }
    Object.keys(searchParams).forEach((element) => {
      if (searchParams[element]) {
        params[element] = searchParams[element]
      }
    })
    return ajax('problems', 'get', {
      params: params
    })
  },
  pickone() {
    return ajax('problems/random', 'get')
  },
  getProblem(problemID, options = {}) {
    let params = { problem_id: problemID }
    if (options.with_kcs) {
      params.with_kcs = true
    }
    return ajax('problems', 'get', {
      params
    })
  },
  getProblemStatistics(problemId, language) {
    const params = { problem_id: problemId }
    if (language) params.language = language
    return ajax('problems/statistics', 'get', { params })
  },
  getRelatedExamples(problemId) {
    return ajax(`problems/${problemId}/related-examples`, 'get')
  }
}
