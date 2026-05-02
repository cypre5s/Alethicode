/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource (relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('problem runtime UI contract', () => {
  describe('UnifiedAgentPanel', () => {
    const panelSource = readSource('../../src/pages/oj/views/problem/UnifiedAgentPanel.vue')

    test('accepts runtimeContext and pendingHumanAction props', () => {
      expect(panelSource).toContain("runtimeContext: { type: Object")
      expect(panelSource).toContain("pendingHumanAction: { type: String")
      expect(panelSource).toContain("workflowQueryClient: { type: Object")
    })

    test('computes isApprovalState from runtimeContext.runtimeState', () => {
      expect(panelSource).toContain('isApprovalState')
      expect(panelSource).toContain("runtimeState === 'WAITING_HUMAN_APPROVAL'")
    })

    test('computes isRestoringState from runtimeContext.runtimeState', () => {
      expect(panelSource).toContain('isRestoringState')
      expect(panelSource).toContain("runtimeState === 'RESTORING'")
    })

    test('computes isFailedState from runtimeContext.runtimeState', () => {
      expect(panelSource).toContain('isFailedState')
      expect(panelSource).toContain("runtimeState === 'FAILED'")
    })

    test('renders approval banner with confirm/reject buttons', () => {
      expect(panelSource).toContain('v-if="isApprovalState"')
      expect(panelSource).toContain('runtime-banner-approval')
      expect(panelSource).toContain("$emit('approve-action')")
      expect(panelSource).toContain("$emit('reject-action')")
    })

    test('renders restoring banner with checkpoint info', () => {
      expect(panelSource).toContain('v-else-if="isRestoringState"')
      expect(panelSource).toContain('runtime-banner-restoring')
      expect(panelSource).toContain('runtimeContext.checkpointId')
    })

    test('renders failed banner with recovery actions', () => {
      expect(panelSource).toContain('v-else-if="isFailedState"')
      expect(panelSource).toContain('runtime-banner-failed')
      expect(panelSource).toContain('runtimeContext.failureBucket')
      expect(panelSource).toContain('runtimeContext.lastError')
      expect(panelSource).toContain("$emit('recover-checkpoint')")
      expect(panelSource).toContain("$emit('restart-workflow')")
    })

    test('disables input during approval and restoring states', () => {
      expect(panelSource).toContain('isInputBlocked')
      expect(panelSource).toContain(':disabled="!canChatInput || loading || isInputBlocked"')
    })

    test('disables quick actions during blocking states', () => {
      expect(panelSource).toContain("'is-disabled': isInputBlocked")
    })

    test('provides human-readable approval descriptions', () => {
      expect(panelSource).toContain('approvalDescription')
      expect(panelSource).toContain('confirm_scaffold')
      expect(panelSource).toContain('confirm_transfer')
    })

    test('renders plan recommendation banner with accept and dismiss actions', () => {
      expect(panelSource).toContain('planRecommendation')
      expect(panelSource).toContain('plan-recommendation-banner')
      expect(panelSource).toContain("$emit('accept-plan-recommendation')")
      expect(panelSource).toContain("$emit('dismiss-plan-recommendation')")
    })

    test('renders current step details inside plan area', () => {
      expect(panelSource).toContain('currentPlanStep')
      expect(panelSource).toContain('current-step-card')
      expect(panelSource).toContain('learning_goal')
      expect(panelSource).toContain('student_task')
      expect(panelSource).toContain('pass_rule')
      expect(panelSource).toContain('support_hint')
    })

    test('does not render the top character strip header anymore', () => {
      expect(panelSource).not.toContain('<CharacterAvatar')
      expect(panelSource).not.toContain('char-avatar-strip')
    })

    test('loads courseware preview through workflow server-state query helper', () => {
      expect(panelSource).toContain("fetchCoursewarePreviewPage")
      expect(panelSource).toContain('this.workflowQueryClient')
    })
  })

  describe('Problem.vue wiring', () => {
    const problemSource = readSource('../../src/pages/oj/views/problem/Problem.vue')

    test('passes runtimeContext and pendingHumanAction to UnifiedAgentPanel', () => {
      expect(problemSource).toContain(':runtime-context="runtimeContext"')
      expect(problemSource).toContain(':pending-human-action="pendingHumanAction"')
      expect(problemSource).toContain(':workflow-query-client="_workflowSessionQueryClient"')
    })

    test('wires approve-action and reject-action events to handleInterrupt', () => {
      expect(problemSource).toContain("@approve-action=\"handleInterrupt('confirm')\"")
      expect(problemSource).toContain("@reject-action=\"handleInterrupt('reject')\"")
    })

    test('wires recover-checkpoint to handleRecoverLatestCheckpoint', () => {
      expect(problemSource).toContain('@recover-checkpoint="handleRecoverLatestCheckpoint"')
      expect(problemSource).toContain('handleRecoverLatestCheckpoint')
    })

    test('wires restart-workflow to handleClearChat', () => {
      expect(problemSource).toContain('@restart-workflow="handleClearChat"')
    })

    test('passes planRecommendation to UnifiedAgentPanel and wires accept/dismiss handlers', () => {
      expect(problemSource).toContain(':plan-recommendation="planRecommendation"')
      expect(problemSource).toContain('@accept-plan-recommendation="handleAcceptPlanRecommendation"')
      expect(problemSource).toContain('@dismiss-plan-recommendation="handleDismissPlanRecommendation"')
    })
  })
})
