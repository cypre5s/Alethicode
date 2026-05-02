/**
 * 提交相关：提交代码 / 调试 / 查询结果 / 重判。
 *
 * 注意 `submitCode` 会自动附加 `classroom_session_id`，保证协作场景下的判题可归属到会话。
 */

import { ajax, tryAttachCollabSessionId } from './shared'

export default {
  submitCode(data) {
    const payload = tryAttachCollabSessionId(data)
    return ajax('submission', 'post', {
      data: payload
    })
  },
  debugCode(data) {
    return ajax('debug', 'post', {
      data
    })
  },
  getSubmissionList(offset, limit, params) {
    params.limit = limit
    params.offset = offset
    return ajax('submissions', 'get', {
      params
    })
  },
  getSubmission(id) {
    return ajax('submission', 'get', {
      params: {
        id
      }
    })
  },
  submissionExists(problemID) {
    return ajax('submission-exists', 'get', {
      params: {
        problem_id: problemID
      }
    })
  },
  submissionRejudge(id) {
    return ajax('admin/submission/rejudge', 'get', {
      params: {
        id
      }
    })
  }
}
