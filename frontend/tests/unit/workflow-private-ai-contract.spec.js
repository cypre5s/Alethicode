/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readSource(relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('workflow private AI contracts', () => {
  test('workflow state machine enables realtime workflow websocket and async dispatch contract', () => {
    const source = readSource('../../src/pages/oj/views/problem/workflowStateMachine.js')
    expect(source).toContain('const ENABLE_WORKFLOW_WS = true')
    expect(source).toContain('event: normalizedEvent')
    expect(source).toContain('await this._ensureWorkflowWsReady()')
    expect(source).toContain('tutorWorkflowCreateRun')
    expect(source).toContain('this._lastAgentCall = { event: normalizedEvent, payload, options }')
  })

  test('workflow state machine uses passed flag for guardrail and restores structured cards', () => {
    const source = readSource('../../src/pages/oj/views/problem/workflowStateMachine.js')
    expect(source).toContain('data.guardrail_result && data.guardrail_result.passed === false')
    expect(source).not.toContain('was_modified')
    expect(source).toContain("type: 'post_ac', data: outputs.post_ac")
    expect(source).toContain("type: 'ai_reply', content: entry.content || ''")
    expect(source).toContain("type: 'user', content: entry.content || ''")
  })

  test('problem page routes skip coding and chat input through unified workflow events', () => {
    const problemSource = readSource('../../src/pages/oj/views/problem/Problem.vue')
    expect(problemSource).toContain("this.dispatchWorkflowEvent(event || 'CODING', {")
    expect(problemSource).toContain("this.dispatchWorkflowEvent('CHAT', {")
    expect(problemSource).not.toContain('对话功能开发中')
  })

  test('problem workflow routes execution trace request through unified workflow events', () => {
    const problemSource = readSource('../../src/pages/oj/views/problem/Problem.vue')
    const workflowSource = readSource('../../src/pages/oj/views/problem/workflowStateMachine.js')
    const contractsSource = readSource('../../src/pages/oj/views/problem/agentContracts.js')
    expect(problemSource).toContain('@request-execution-trace="handleRequestExecutionTrace"')
    expect(problemSource).toContain('request_execution_trace: true')
    expect(problemSource).toContain('language: this.language')
    expect(problemSource).toContain('resolveExecutionTraceWorkflowEvent')
    expect(problemSource).not.toContain("const phase = this.workflowContext.current_state === 'ERROR_FEEDBACK' ? 'ERROR_FEEDBACK' : 'CODING'")
    expect(workflowSource).toContain('request_execution_trace: !!payload.request_execution_trace')
    expect(contractsSource).toContain("'execution_trace_explainer'")
  })

  test('workflow state machine should propagate current editor language across all AI workflow event payloads', () => {
    const workflowSource = readSource('../../src/pages/oj/views/problem/workflowStateMachine.js')

    expect(workflowSource).toContain('const language = payload.language || this._resolveProblemLanguage()')
    expect(workflowSource).toContain('const resolvedLanguage = eventData.language || this._resolveProblemLanguage()')
    expect(workflowSource).toContain('language: resolvedLanguage')
    expect(workflowSource).toContain('code: payload.code,')
    expect(workflowSource).toContain('submission_id: payload.submission_id,')
  })

  test('problem page should not manually force CODING phase and frontend should delegate transition errors to backend', () => {
    const problemSource = readSource('../../src/pages/oj/views/problem/Problem.vue')
    const workflowSource = readSource('../../src/pages/oj/views/problem/workflowStateMachine.js')

    expect(problemSource).not.toContain("this.transitionState('CODING')")
    expect(workflowSource).not.toContain('assertWorkflowEventAllowedOrThrow')
    expect(workflowSource).toContain('_resolveWorkflowDispatchError')
  })

  test('workflow state machine should prioritize execution trace explainer across sync, ws, and watchdog recovery paths', () => {
    const workflowSource = readSource('../../src/pages/oj/views/problem/workflowStateMachine.js')
    expect(workflowSource).toContain('_pushExecutionTraceExplainerIfPresent')
    expect(workflowSource).toContain("this.pushAgentMessage({ type: 'execution_trace_explainer', data: outputs.execution_trace_explainer })")
    expect(workflowSource).toContain('if (!this._pushExecutionTraceExplainerIfPresent(data.node_outputs || {})) {')
    expect(workflowSource).toContain('if (nodeOutputs.execution_trace_explainer) {')
  })

  test('visualize capability is wired through workflow events with a single LangGraph chain', () => {
    const contractsSource = readSource('../../src/pages/oj/views/problem/agentContracts.js')
    const workflowSource = readSource('../../src/pages/oj/views/problem/workflowStateMachine.js')
    const panelSource = readSource('../../src/pages/oj/views/problem/UnifiedAgentPanel.vue')
    const problemSource = readSource('../../src/pages/oj/views/problem/Problem.vue')
    const aiTutorApiSource = readSource('../../src/pages/oj/api/aiTutor.js')
    const aiModuleSource = readSource('../../src/api/modules/ai.js')
    const composablesIndex = require('fs').readdirSync(
      require('path').resolve(__dirname, '../../src/composables/problem')
    )

    expect(contractsSource).toContain("'VISUALIZE'")
    expect(contractsSource).toContain("'visualize'")
    expect(workflowSource).toContain("VISUALIZE: 'visualize'")
    expect(workflowSource).toContain("this.pushAgentMessage({ type: 'visualize', data: payload })")
    expect(panelSource).toContain("item.type === 'visualize'")
    expect(panelSource).toContain("$emit('request-visualize')")
    expect(problemSource).toContain('@request-visualize="handleRequestVisualize"')
    expect(problemSource).toContain('resolveInlineVisualizeIntent')
    expect(problemSource).toContain("this.dispatchWorkflowEvent('VISUALIZE'")

    expect(problemSource).not.toContain('useVisualizeApi')
    expect(problemSource).not.toContain('visualizeApi.requestInline')
    expect(aiTutorApiSource).not.toContain('tutorVisualizeInline')
    expect(aiTutorApiSource).not.toContain("ajax('ai/tutor/visualize/inline'")
    expect(aiModuleSource).not.toContain('tutorVisualizeInline')
    expect(composablesIndex).not.toContain('useVisualizeApi.js')
  })

  test('problem workflow uses mirrored agent contracts and routes transfer through unified workflow', () => {
    const contractsSource = readSource('../../src/pages/oj/views/problem/agentContracts.js')
    const workflowSource = readSource('../../src/pages/oj/views/problem/workflowStateMachine.js')
    const problemSource = readSource('../../src/pages/oj/views/problem/Problem.vue')
    expect(contractsSource).toContain('export const PHASES = Object.freeze([')
    expect(contractsSource).toContain('export const WORKFLOW_EVENTS = Object.freeze([')
    expect(contractsSource).toContain('export const CARD_TYPES = Object.freeze([')
    expect(contractsSource).toContain('export const FEEDBACK_LABELS = Object.freeze([')
    expect(workflowSource).toContain("from './agentContracts'")
    expect(problemSource).toContain("this.dispatchWorkflowEvent('TRANSFER', {")
    expect(problemSource).not.toContain('this.callAgent(6, {')
  })

  test('skeleton generation should be a first-class workflow event and not a legacy ideate api', () => {
    const contractsSource = readSource('../../src/pages/oj/views/problem/agentContracts.js')
    const workflowSource = readSource('../../src/pages/oj/views/problem/workflowStateMachine.js')
    const problemSource = readSource('../../src/pages/oj/views/problem/Problem.vue')

    expect(contractsSource).toContain("'SKELETON'")
    expect(workflowSource).toContain("case 'SKELETON':")
    expect(problemSource).toContain("this.dispatchWorkflowEvent('SKELETON'")
    expect(problemSource).not.toContain('api.ideateGetSkeleton({')
  })

  test('workflow UI should pass phase capabilities from problem page into panel and card-level controls', () => {
    const problemSource = readSource('../../src/pages/oj/views/problem/Problem.vue')
    const panelSource = readSource('../../src/pages/oj/views/problem/UnifiedAgentPanel.vue')
    const guideCardSource = readSource('../../src/pages/oj/views/problem/cards/ProblemGuideCard.vue')
    const ideateCardSource = readSource('../../src/pages/oj/views/problem/cards/IdeateAnalysisCard.vue')
    const errorDiagnosisSource = readSource('../../src/pages/oj/views/problem/cards/ErrorDiagnosisCard.vue')
    const codeEditorPanelSource = readSource('../../src/pages/oj/views/problem/CodeEditorPanel.vue')

    expect(problemSource).toContain(':can-chat-input="canUseChatInput"')
    expect(problemSource).toContain(':can-start-ideate="canStartIdeate"')
    expect(problemSource).toContain(':can-request-skeleton="canRequestSkeleton"')
    expect(problemSource).toContain(':can-request-execution-trace="canRequestExecutionTrace"')
    expect(problemSource).toContain(':can-open-ai-chat="canOpenAiChat"')
    expect(problemSource).toContain(':can-request-diagnosis="canRequestDiagnosis"')
    expect(panelSource).toContain(':disabled="!canChatInput || loading || isInputBlocked"')
    expect(panelSource).toContain(':can-start-ideate="canStartIdeate"')
    expect(panelSource).toContain(':can-request-skeleton="canRequestSkeleton"')
    expect(panelSource).toContain(':can-request-execution-trace="canRequestExecutionTrace"')
    expect(guideCardSource).toContain('v-if="data.starter_questions && data.starter_questions.length && canStartIdeate"')
    expect(ideateCardSource).toContain('cleanedSteps.length >= 2 && canRequestSkeleton')
    expect(errorDiagnosisSource).toContain(':can-request-execution-trace="canRequestExecutionTrace"')
    expect(codeEditorPanelSource).toContain('canRequestDiagnosis: {')
    expect(codeEditorPanelSource).toContain('v-if="aiTutorEnabled && consecutiveErrors >= 3 && canOpenAiChat"')
  })

  test('error diagnosis card should hide low-similarity history errors', () => {
    const errorDiagnosisSource = readSource('../../src/pages/oj/views/problem/cards/ErrorDiagnosisCard.vue')
    const errorDiagnosisDetailsSource = readSource('../../src/pages/oj/views/problem/cards/ErrorDiagnosisDetails.vue')

    expect(errorDiagnosisSource).toContain('MIN_SIMILAR_ERROR_SCORE')
    expect(errorDiagnosisSource).toContain('filteredSimilarErrorRefs')
    expect(errorDiagnosisSource).toContain('return s >= MIN_SIMILAR_ERROR_SCORE')
    expect(errorDiagnosisDetailsSource).toContain('hasVisibleSimilarErrors')
    expect(errorDiagnosisDetailsSource).toContain('this.repeatPatternDetected && this.filteredSimilarErrorRefs.length > 0')
    expect(errorDiagnosisDetailsSource).toContain('v-if="hasVisibleSimilarErrors"')
    expect(errorDiagnosisSource).toContain('>= MIN_SIMILAR_ERROR_SCORE')
    expect(errorDiagnosisDetailsSource).not.toContain('v-if="repeatPatternDetected" class="ed-similar-block"')
  })

  test('error diagnosis card should expose a toggle for the first failed test case evidence', () => {
    const errorDiagnosisSource = readSource('../../src/pages/oj/views/problem/cards/ErrorDiagnosisDetails.vue')

    expect(errorDiagnosisSource).toContain('firstFailedTestCase')
    expect(errorDiagnosisSource).toContain('firstFailedExpanded')
    expect(errorDiagnosisSource).toContain('看第一个错误测试点')
    expect(errorDiagnosisSource).toContain('测试输入')
    expect(errorDiagnosisSource).toContain('期望输出')
  })

  test('error diagnosis card should render an inline visualize payload when present', () => {
    const errorDiagnosisSource = readSource('../../src/pages/oj/views/problem/cards/ErrorDiagnosisCard.vue')

    expect(errorDiagnosisSource).toContain('VisualizeRenderer')
    expect(errorDiagnosisSource).toContain('inlineVisualize')
    expect(errorDiagnosisSource).toContain('<VisualizeRenderer')
  })

  test('parsons cascade re-dispatch should use backend-provided next_fading_level', () => {
    const problemSource = readSource('../../src/pages/oj/views/problem/Problem.vue')

    expect(problemSource).toContain('data.next_fading_level')
    expect(problemSource).toContain('data.current_fading_level')
    expect(problemSource).toContain('override_fading_level: nextLevel')
    // 不能再用本地 lastResult.fadingLevel 自己减一
    expect(problemSource).not.toContain("(this.parsonsState.lastResult && this.parsonsState.lastResult.fadingLevel) || 1) - 1")
  })

  test('error diagnosis actions should use a unified footer layout', () => {
    const errorDiagnosisSource = readSource('../../src/pages/oj/views/problem/cards/ErrorDiagnosisCard.vue')
    const errorDiagnosisDetailsSource = readSource('../../src/pages/oj/views/problem/cards/ErrorDiagnosisDetails.vue')

    expect(errorDiagnosisDetailsSource).toContain('edd-action-panel')
    expect(errorDiagnosisDetailsSource).toContain('edd-preference-group')
    expect(errorDiagnosisDetailsSource).toContain('edd-preference-actions')
    expect(errorDiagnosisSource).toContain('ed-card-footer-actions')
    expect(errorDiagnosisSource).toContain('ed-secondary-action')
  })
})
