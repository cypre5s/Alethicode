/**
 * Faded Parsons API。
 *
 * 后端契约：
 *   POST /api/ai/tutor/parsons/dispatch     学生主动派发拼装题
 *   POST /api/ai/tutor/parsons/submit       学生提交块顺序
 *   POST /api/ai/tutor/parsons/walkthrough  AC 后提交 walkthrough 文本
 *   GET  /api/ai/tutor/parsons/{sessionId}  恢复某次会话
 */

import { ajax } from './shared'

export default {
  parsonsDispatch(payload) {
    return ajax('ai/tutor/parsons/dispatch', 'post', { data: payload })
  },
  parsonsSubmit(payload) {
    return ajax('ai/tutor/parsons/submit', 'post', { data: payload })
  },
  parsonsWalkthrough(payload) {
    return ajax('ai/tutor/parsons/walkthrough', 'post', { data: payload })
  },
  parsonsLoad(sessionId) {
    return ajax(`ai/tutor/parsons/${encodeURIComponent(sessionId)}`, 'get')
  }
}
