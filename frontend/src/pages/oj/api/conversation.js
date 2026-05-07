/**
 * Unified Chat 会话接口，对齐 TutorWorkflowController 的会话路径。
 */

import { ajax } from './shared'

export default {
  getConversation(sessionId) {
    return ajax(`ai/tutor-workflow-sessions/${sessionId}/conversation`, 'get')
  },
  switchConversationMode(sessionId, mode) {
    return ajax(`ai/tutor-workflow-sessions/${sessionId}/mode`, 'post', {
      data: { mode }
    })
  }
}
