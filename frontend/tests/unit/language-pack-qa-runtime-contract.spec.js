/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource(relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('language pack QA runtime contract', () => {
  describe('QA page runtime event consumption (Phase 3)', () => {
    const qaSource = readSource('../../src/pages/oj/views/languagepack/LanguagePackQaPage.vue')

    test('imports runtimeContract utilities', () => {
      expect(qaSource).toContain('normalizeRuntimeEvent')
      expect(qaSource).toContain('assertAllowedForQaPage')
      expect(qaSource).toContain('SERVER_EVENTS')
      expect(qaSource).toContain("from '@/utils/runtimeContract'")
    })

    test('imports QA WebSocket path builder', () => {
      expect(qaSource).toContain('buildQaWebSocketPath')
      expect(qaSource).toContain("from '@/utils/websocketUrl'")
    })

    test('data includes qaRuntimeContext with required fields', () => {
      expect(qaSource).toContain('qaRuntimeContext:')
      const fields = ['sessionId', 'taskId', 'runtimeState', 'serverEvent', 'failureBucket', 'lastError', 'updatedAt']
      fields.forEach(field => {
        expect(qaSource).toContain(`${field}:`)
      })
    })

    test('data includes qaPendingQuestion for retry', () => {
      expect(qaSource).toContain('qaPendingQuestion:')
    })

    test('data includes QA WebSocket connection state', () => {
      expect(qaSource).toContain('_qaWsConnection:')
      expect(qaSource).toContain('_qaWsReconnectTimer:')
    })

    test('sendQuestion dispatches async when WebSocket is ready', () => {
      expect(qaSource).toContain('_ensureQaWsReady')
      expect(qaSource).toContain("{ async: true }")
    })

    test('sendQuestion falls back to sync when WebSocket is not ready', () => {
      expect(qaSource).toContain('_sendQuestionSync')
    })

    test('sendQuestion sets runtime state to QUEUED on dispatched', () => {
      expect(qaSource).toContain("runtimeState: 'QUEUED'")
    })

    test('_handleQaRuntimeEvent validates state against QA page allowed set', () => {
      expect(qaSource).toContain('_handleQaRuntimeEvent')
      expect(qaSource).toContain('assertAllowedForQaPage(normalized.runtimeState)')
    })

    test('_handleQaRuntimeEvent rejects events from different sessions', () => {
      expect(qaSource).toContain('normalized.sessionId !== String(this.activeSessionId)')
    })

    test('_handleQaRuntimeEvent handles TASK_STARTED by setting state to RUNNING', () => {
      expect(qaSource).toContain('case SERVER_EVENTS.TASK_STARTED:')
      expect(qaSource).toContain("runtimeState = 'RUNNING'")
    })

    test('_handleQaRuntimeEvent handles TASK_COMPLETED by refreshing messages', () => {
      expect(qaSource).toContain('case SERVER_EVENTS.TASK_COMPLETED:')
      expect(qaSource).toContain('this._onQaCompleted()')
    })

    test('_handleQaRuntimeEvent handles TASK_FAILED by recording error', () => {
      expect(qaSource).toContain('case SERVER_EVENTS.TASK_FAILED:')
    })

    test('_handleQaRuntimeEvent handles TASK_EXPIRED', () => {
      expect(qaSource).toContain('case SERVER_EVENTS.TASK_EXPIRED:')
    })

    test('activateSession disconnects old WebSocket and resets runtime context', () => {
      expect(qaSource).toMatch(/activateSession[\s\S]*?this\._disconnectQaWs\(\)/)
      expect(qaSource).toMatch(/activateSession[\s\S]*?this\._resetQaRuntimeContext\(\)/)
    })

    test('switchPack disconnects WebSocket and resets runtime context', () => {
      expect(qaSource).toMatch(/switchPack[\s\S]*?this\._disconnectQaWs\(\)/)
      expect(qaSource).toMatch(/switchPack[\s\S]*?this\._resetQaRuntimeContext\(\)/)
    })

    test('beforeUnmount disconnects QA WebSocket', () => {
      expect(qaSource).toMatch(/beforeUnmount[\s\S]*?this\._disconnectQaWs\(\)/)
    })

    test('WebSocket onmessage only processes runtime_event type', () => {
      expect(qaSource).toContain("msg.type === 'runtime_event'")
      expect(qaSource).toContain('this._handleQaRuntimeEvent(msg)')
    })
  })

  describe('QA page status UI (Phase 4)', () => {
    const qaSource = readSource('../../src/pages/oj/views/languagepack/LanguagePackQaPage.vue')

    test('shows QUEUED status banner', () => {
      expect(qaSource).toContain("runtimeState === 'QUEUED'")
      expect(qaSource).toContain('is-queued')
    })

    test('shows RUNNING status banner', () => {
      expect(qaSource).toContain("runtimeState === 'RUNNING'")
      expect(qaSource).toContain('is-running')
    })

    test('shows FAILED status banner with retry button', () => {
      expect(qaSource).toContain("runtimeState === 'FAILED'")
      expect(qaSource).toContain('is-failed')
      expect(qaSource).toContain('retryLastQuestion')
    })

    test('disables send button and textarea during RUNNING', () => {
      expect(qaSource).toContain('qaInputDisabled')
      expect(qaSource).toContain(':disabled="qaInputDisabled"')
    })

    test('disables pack select during RUNNING', () => {
      expect(qaSource).toContain("loadings.packs || !packs.length || qaInputDisabled")
    })

    test('disables new session button during RUNNING', () => {
      expect(qaSource).toContain("!currentPackIsQaReady || isBusy || qaInputDisabled")
    })

    test('retryLastQuestion uses pending question to retry', () => {
      expect(qaSource).toContain('retryLastQuestion')
      expect(qaSource).toContain('this.qaPendingQuestion')
    })

    test('does not include checkpoint/approval/restore UI', () => {
      expect(qaSource).not.toContain('WAITING_HUMAN_APPROVAL')
      expect(qaSource).not.toContain('RESTORING')
      expect(qaSource).not.toContain('approve-action')
      expect(qaSource).not.toContain('checkpoint')
    })
  })

  describe('websocketUrl QA path builder', () => {
    const wsSource = readSource('../../src/utils/websocketUrl.js')

    test('exports buildQaWebSocketPath', () => {
      expect(wsSource).toMatch(/export function buildQaWebSocketPath\s*\(/)
      expect(wsSource).toContain('/ws/qa/')
    })
  })

  describe('API async support', () => {
    const apiSource = readSource('../../src/pages/oj/api/languagePack.js')

    test('sendLanguagePackQaMessage accepts async option', () => {
      expect(apiSource).toContain('options.async')
      expect(apiSource).toContain('async: true')
    })
  })
})
