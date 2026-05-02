import ojApi from '@/pages/oj/api'
import adminApi from '@/pages/admin/api'

function pick(api, names) {
  return names.reduce((acc, name) => {
    acc[name] = (...args) => api[name](...args)
    return acc
  }, {})
}

export const ojProblemApi = pick(ojApi, [
  'getProblemTagList',
  'getProblemList',
  'pickone',
  'getProblem',
  'getProblemStatistics',
  'getTagProgress',
  'getProblemRecommendations'
])

export const adminProblemApi = pick(adminApi, [
  'getProblemTagList',
  'createProblem',
  'editProblem',
  'deleteProblem',
  'getProblem',
  'getInlineTestCases',
  'uploadInlineTestCases',
  'getProblemList',
  'exportProblems'
])

export default {
  oj: ojProblemApi,
  admin: adminProblemApi
}
