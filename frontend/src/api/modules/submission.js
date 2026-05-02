import ojApi from '@/pages/oj/api'

function pick(api, names) {
  return names.reduce((acc, name) => {
    acc[name] = (...args) => api[name](...args)
    return acc
  }, {})
}

export const ojSubmissionApi = pick(ojApi, [
  'submitCode',
  'debugCode',
  'getSubmissionList',
  'getSubmission',
  'submissionExists',
  'submissionRejudge',
  'getRecentWrong'
])

export default {
  oj: ojSubmissionApi
}
