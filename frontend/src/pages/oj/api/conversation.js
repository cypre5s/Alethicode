/**
 * Unified Chat (P3) Conversation API – ModeBar + last cards source of truth.
 *
 * Backend: TutorWorkflowController, mounted at /api/ai/tutor-workflow-sessions/{id}/...
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
