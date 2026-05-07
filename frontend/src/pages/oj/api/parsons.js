/**
 * Faded Parsons 接口覆盖派发、提交、复盘和会话恢复。
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
