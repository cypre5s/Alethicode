import { ref, computed, getCurrentInstance } from 'vue'
import { buildProblemErrorKey } from '@/utils/constants'
import storage from '@/utils/storage'
import api from '@oj/api'
import { runPreflightDetectors, selectPriorityHit } from '@oj/views/problem/preflightDetectors'
import { recordEvent as recordBetaEvent } from '@/utils/betaTelemetry'

export function useSubmission () {
  const instance = getCurrentInstance()
  const vm = () => instance.proxy

  const statusVisible = ref(false)
  const captchaRequired = ref(false)
  const submissionExists = ref(false)
  const captchaCode = ref('')
  const captchaSrc = ref('')
  const submitting = ref(false)
  const debugging = ref(false)
  const debugInput = ref('')
  const debugOutput = ref('')
  const debugError = ref(false)
  const objectiveChoiceAnswer = ref('')
  const objectiveBlankAnswers = ref({})
  const objectiveSubmitting = ref(false)
  const submissionId = ref('')
  const submitted = ref(false)
  const result = ref({ result: 9 })
  const preflightState = ref('idle')
  const preflightDialog = ref({
    visible: false, question: '', hint: '', highlightReason: '', alertTitle: '',
    lineNumber: 0, codeSnippet: '', detectorName: '', misconceptionId: null, kcMastery: 0, triggerCount: 0
  })

  let _attemptNumber = 0
  let _lastSubmitTime = null
  let _lastSubmittedCode = ''
  let _hasSubmitted = false
  let _preflightTriggerCounts = {}
  let refreshStatus = null

  const isObjectiveProblem = computed(() => objectiveQuestionType.value === 'choice' || objectiveQuestionType.value === 'fill_blank')

  const objectiveQuestionType = computed(() => {
    var p = vm()
    var op = p.objectivePayload
    if (op && op.question_type) return op.question_type
    return ''
  })

  const objectiveJudgeInfo = computed(() => {
    const stat = (result.value && result.value.statistic_info) || {}
    const o = stat.objective
    if (!o) return null
    return {
      passed: !!o.passed,
      question_type: o.question_type || '',
      score: typeof stat.score === 'number' ? stat.score : (o.passed ? 100 : 0),
      filled_blanks: typeof o.filled_blanks === 'number' ? o.filled_blanks : 0,
      total_blanks: typeof o.total_blanks === 'number' ? o.total_blanks : 0
    }
  })

  const objectiveSubmissionId = computed(() => submissionId.value || '')

  function checkSubmissionStatus () {
    if (refreshStatus) clearTimeout(refreshStatus)
    if (!submissionId.value) {
      submitting.value = false
      submitted.value = false
      return
    }
    const checkStatus = () => {
      let id = submissionId.value
      api.getSubmission(id).then(res => {
        const submissionData = res && res.data ? res.data.data : null
        if (!submissionData || !submissionData.statistic_info) {
          refreshStatus = setTimeout(checkStatus, 2000)
          return
        }
        result.value = submissionData
        if (Object.keys(submissionData.statistic_info).length !== 0) {
          submitting.value = false
          submitted.value = false
          clearTimeout(refreshStatus)

          const resultCode = result.value.result
          const errorKey = buildProblemErrorKey(vm().problemID)

          vm().onSubmissionResult(result.value, submissionId.value)

          if (resultCode === 0) {
            storage.set(errorKey, 0)
            vm().onSubmitResultForFrustration(0)
            if (!isObjectiveProblem.value && typeof vm().showSuccessAnimation === 'function') {
              vm().showSuccessAnimation(result.value)
            }
          } else {
            storage.set(errorKey, vm().workflowContext.consecutiveErrors)
            vm().onSubmitResultForFrustration(resultCode)
          }
        } else {
          refreshStatus = setTimeout(checkStatus, 2000)
        }
      }).catch(() => {
        if (!submissionId.value) {
          submitting.value = false
          clearTimeout(refreshStatus)
          return
        }
        refreshStatus = setTimeout(checkStatus, 2000)
      })
    }
    refreshStatus = setTimeout(checkStatus, 2000)
  }

  function syncCodeFromEditor () {
    var editorRef = typeof vm().getEditorRef === 'function' ? vm().getEditorRef() : null
    if (editorRef && typeof editorRef.getDocument === 'function') {
      var doc = editorRef.getDocument()
      if (typeof doc === 'string') {
        vm().code = doc
      }
    }
  }

  function debugCode () {
    syncCodeFromEditor()
    if (vm().code.trim() === '') {
      vm().$error('代码不能为空')
      return
    }
    try {
      recordBetaEvent('feature_click', {
        name: 'debug_code',
        problem_id: vm().problem && vm().problem.id != null ? vm().problem.id : null,
        language: vm().language || ''
      })
    } catch (err) {
      void err
    }
    debugging.value = true
    debugError.value = false
    debugOutput.value = ''
    let data = {
      problem_id: vm().problem.id,
      language: vm().language,
      code: vm().code,
      input: debugInput.value || ''
    }
    api.debugCode(data).then(res => {
      debugging.value = false
      const r = res.data.data
      if (r.error) {
        debugError.value = true
        debugOutput.value = r.error
      } else {
        debugError.value = false
        debugOutput.value = r.output || '程序执行完成，无输出'
      }
    }).catch(err => {
      debugging.value = false
      debugError.value = true
      let errorMsg = '调试失败：\n\n'
      if (err.response) {
        if (err.response.status === 404) {
          errorMsg += '调试接口未找到 (404)\n请检查后端服务是否正常运行，或联系管理员配置调试功能。'
        } else if (err.response.data && err.response.data.data) {
          errorMsg += err.response.data.data
        } else {
          errorMsg += `服务器错误 (${err.response.status})`
        }
      } else if (err.request) {
        errorMsg += '无法连接到服务器\n请检查网络连接。'
      } else if (err.message) {
        errorMsg += err.message
      } else {
        errorMsg += '未知错误'
      }
      debugOutput.value = errorMsg
    })
  }

  function submitCode () {
    syncCodeFromEditor()
    if (vm().code.trim() === '') {
      vm().$error(vm().$t('m.Code_can_not_be_empty'))
      return
    }
    try {
      recordBetaEvent('feature_click', {
        name: 'submit_code',
        problem_id: vm().problem && vm().problem.id != null ? vm().problem.id : null,
        language: vm().language || ''
      })
    } catch (err) {
      void err
    }
    if (preflightState.value === 'scanning') return

    vm().trackRapidResubmit()

    _attemptNumber = (_attemptNumber || 0) + 1
    const now = Date.now()
    const timeSinceLastSubmit = _lastSubmitTime ? now - _lastSubmitTime : 0
    _lastSubmitTime = now

    if (vm().$refs && vm().$refs.codeEditorPanel && typeof vm().$refs.codeEditorPanel.getEditStats === 'function') {
      const stats = vm().$refs.codeEditorPanel.getEditStats()
      if (typeof vm()._queueEvent === 'function') {
        vm()._queueEvent({ event_type: 'code_edit_summary', problem_id: vm().problem && vm().problem.id, extra_data: stats })
      }
      vm().$refs.codeEditorPanel.resetEditStats()
    }
    if (typeof vm()._queueEvent === 'function') {
      vm()._queueEvent({
        event_type: 'submission_attempt',
        problem_id: vm().problem && vm().problem.id,
        extra_data: {
          attempt_number: _attemptNumber,
          time_since_last_submit_ms: timeSinceLastSubmit,
          time_since_problem_opened_ms: vm()._pageOpenTime ? now - vm()._pageOpenTime : 0
        }
      })
    }

    if (_lastSubmittedCode === vm().code) {
      doRealSubmit()
      return
    }

    const hits = runPreflightDetectors(vm().code)
    if (hits.length === 0) { doRealSubmit(); return }

    const triggerCounts = _preflightTriggerCounts || {}
    const best = selectPriorityHit(hits, triggerCounts)
    if (!best) { doRealSubmit(); return }

    preflightState.value = 'scanning'
    submitting.value = true

    const otherHits = hits
      .filter(h => h.detector_name !== best.detector_name)
      .map(h => ({ detector_name: h.detector_name, line_number: h.line_number, code_snippet: h.code_snippet }))

    api.preflightCheck({
      problem_id: vm().problem.id,
      student_code: vm().code,
      detector_name: best.detector_name,
      line_number: best.line_number,
      code_snippet: best.code_snippet,
      other_hits: otherHits.length > 0 ? otherHits : undefined
    }).then(res => {
      const data = res.data.data
      if (!data || !data.should_trigger) {
        preflightState.value = 'idle'
        doRealSubmit()
        return
      }
      preflightState.value = 'dialog'
      submitting.value = false
      preflightDialog.value = {
        visible: true, question: data.question || '', hint: data.hint || '',
        highlightReason: data.highlight_reason || '', alertTitle: data.alert_title || '提交前检查',
        lineNumber: best.line_number, codeSnippet: best.code_snippet, detectorName: best.detector_name,
        misconceptionId: data.misconception_id || null, kcMastery: data.kc_mastery || 0, triggerCount: data.trigger_count || 0
      }
    }).catch(() => { preflightState.value = 'idle'; doRealSubmit() })
  }

  function handlePreflightGoEdit () {
    preflightState.value = 'idle'
    submitting.value = false
    preflightDialog.value = { ...preflightDialog.value, visible: false }
    if (typeof vm()._queueEvent === 'function') {
      vm()._queueEvent({
        event_type: 'preflight_go_edit',
        problem_id: vm().problem && vm().problem.id,
        extra_data: { detector_name: preflightDialog.value.detectorName, line_number: preflightDialog.value.lineNumber }
      })
    }
  }

  function handlePreflightForceSubmit () {
    preflightDialog.value = { ...preflightDialog.value, visible: false }
    _lastSubmittedCode = vm().code
    preflightState.value = 'submitted'
    if (typeof vm()._queueEvent === 'function') {
      vm()._queueEvent({
        event_type: 'preflight_force_submit',
        problem_id: vm().problem && vm().problem.id,
        extra_data: {
          detector_name: preflightDialog.value.detectorName, line_number: preflightDialog.value.lineNumber,
          misconception_id: preflightDialog.value.misconceptionId, code_snippet: preflightDialog.value.codeSnippet
        }
      })
    }
    const counts = _preflightTriggerCounts || {}
    counts[preflightDialog.value.detectorName] = (counts[preflightDialog.value.detectorName] || 0) + 1
    _preflightTriggerCounts = counts
    doRealSubmit()
  }

  function doRealSubmit () {
    _hasSubmitted = true
    submissionId.value = ''
    result.value = { result: 9 }
    submitting.value = true
    let data = {
      problem_id: vm().problem.id,
      language: vm().language,
      code: vm().code
    }
    if (_lastSubmittedCode === vm().code && preflightDialog.value.detectorName) {
      data.preflight_detector = preflightDialog.value.detectorName
      data.preflight_misconception_id = preflightDialog.value.misconceptionId
      data.preflight_overridden = true
      data.preflight_question = preflightDialog.value.question
      data.preflight_line_number = preflightDialog.value.lineNumber
      data.preflight_code_snippet = preflightDialog.value.codeSnippet
    }
    if (captchaRequired.value) {
      data.captcha = captchaCode.value
    }
    preflightState.value = 'idle'
    statusVisible.value = true
    api.submitCode(data).then(res => {
      submissionId.value = res.data.data && res.data.data.submission_id
      submitting.value = false
      submissionExists.value = true
      if (!submissionId.value) {
        submitted.value = false
        statusVisible.value = false
        vm().$success(vm().$t('m.Submit_code_successfully'))
        return
      }
      submitted.value = true
      checkSubmissionStatus()
    }, res => {
      getCaptchaSrc()
      if (res.data.data.startsWith('Captcha is required')) {
        captchaRequired.value = true
      }
      submitting.value = false
      statusVisible.value = false
    })
  }

  function getCaptchaSrc () {
    api.getCaptcha().then(res => { captchaSrc.value = res.data.data })
  }

  function resetObjectiveAnswer () {
    objectiveChoiceAnswer.value = ''
    const blanks = vm().objectiveBlanks || []
    const next = {}
    ;(blanks || []).forEach((_, idx) => { next[idx] = '' })
    objectiveBlankAnswers.value = next
  }

  function submitObjectiveAnswer () {
    if (objectiveQuestionType.value === 'choice' && !objectiveChoiceAnswer.value) {
      vm().$warning('请选择一个选项后再提交')
      return
    }
    if (objectiveQuestionType.value === 'fill_blank') {
      const blankCount = (vm().objectiveBlanks || []).length
      for (let i = 0; i < blankCount; i++) {
        const value = (objectiveBlankAnswers.value[i] || '').trim()
        if (!value) { vm().$warning(`请填写空${i + 1}后再提交`); return }
      }
    }
    objectiveSubmitting.value = true
    submissionId.value = ''
    result.value = { result: 9 }
    statusVisible.value = true
    const payload = {
      problem_id: vm().problem.id,
      language: vm().language || ((vm().problem.languages || [])[0] || ''),
      code: vm().code || ''
    }
    if (objectiveQuestionType.value === 'choice') {
      payload.objective_answer = objectiveChoiceAnswer.value
    } else if (objectiveQuestionType.value === 'fill_blank') {
      const answers = {}
      const blankCount = (vm().objectiveBlanks || []).length
      for (let i = 0; i < blankCount; i++) { answers[String(i)] = (objectiveBlankAnswers.value[i] || '').trim() }
      payload.objective_blanks = answers
    }
    api.submitCode(payload).then(res => {
      submissionId.value = res.data.data && res.data.data.submission_id
      objectiveSubmitting.value = false
      submissionExists.value = true
      if (!submissionId.value) { vm().$error('提交成功但未返回 submission_id'); return }
      submitted.value = true
      checkSubmissionStatus()
    }).catch(() => { objectiveSubmitting.value = false; statusVisible.value = false })
  }

  function goObjectiveSubmissionDetails () {
    if (!objectiveSubmissionId.value) return
    vm().$router.push({ path: `/status/${objectiveSubmissionId.value}` })
  }

  return {
    statusVisible, captchaRequired, submissionExists, captchaCode, captchaSrc,
    submitting, debugging, debugInput, debugOutput, debugError,
    objectiveChoiceAnswer, objectiveBlankAnswers, objectiveSubmitting,
    submissionId, submitted, result, preflightState, preflightDialog,
    isObjectiveProblem, objectiveQuestionType, objectiveJudgeInfo, objectiveSubmissionId,
    checkSubmissionStatus, syncCodeFromEditor, debugCode, submitCode,
    handlePreflightGoEdit, handlePreflightForceSubmit, doRealSubmit,
    resetObjectiveAnswer, submitObjectiveAnswer, goObjectiveSubmissionDetails, getCaptchaSrc
  }
}
