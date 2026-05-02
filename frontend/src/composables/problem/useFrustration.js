import { ref, getCurrentInstance, onBeforeUnmount } from 'vue'
import api from '@oj/api'

export function useFrustration () {
  const instance = getCurrentInstance()
  const vm = () => instance.proxy

  const frustrationCardVisible = ref(false)
  const frustrationLoading = ref(false)
  const encouragementText = ref('')
  const recoveryProblems = ref([])
  const codeChurnValue = ref(0)

  let _editorChangeSubscription = null
  let _deleteOperations = 0
  let _totalOperations = 0
  let _totalInserted = 0
  let _totalDeleted = 0
  let _lastSubmitTime = 0
  let _rapidResubmitCount = 0
  let _dwellWithoutEdit = 0
  let _dwellTimer = null
  let _helpButStillFail = false
  let _aiTutorUsedSinceLastAC = false
  let _frustrationScore = 0
  let _frustrationCooldown = false
  let _frustrationData = null

  onBeforeUnmount(() => {
    if (_editorChangeSubscription && typeof _editorChangeSubscription.dispose === 'function') {
      _editorChangeSubscription.dispose()
      _editorChangeSubscription = null
    }
    if (_dwellTimer) {
      clearInterval(_dwellTimer)
      _dwellTimer = null
    }
  })

  function attachCodeMirrorChangeHandler () {
    var self = vm()
    var bindCm6Change = function (coreRef) {
      _editorChangeSubscription = coreRef.onChangeSubscribe(function (update) {
        update.changes.iterChanges(function (fromA, toA, fromB, toB, inserted) {
          var removedLength = toA - fromA
          var insertedLength = inserted.length
          _totalOperations++
          if (removedLength > 0) {
            _deleteOperations++
            _totalDeleted += removedLength
          }
          _totalInserted += insertedLength
        })
      })
    }
    var bindCodeMirrorChange = function (cm) {
      cm.on('change', function (instance, change) {
        _totalOperations++
        if (change.origin === '+delete') {
          _deleteOperations++
        }
        var removedText = change.removed ? change.removed.join('\n') : ''
        var insertedText = change.text ? change.text.join('\n') : ''
        _totalDeleted += removedText.length
        _totalInserted += insertedText.length
      })
    }
    var tryAttach = function () {
      var panel = self.$refs.codeEditorPanel
      var editorComp = panel ? panel.$refs.editor : self.$refs.editor
      if (!editorComp) {
        setTimeout(tryAttach, 500)
        return
      }
      if (_editorChangeSubscription && typeof _editorChangeSubscription.dispose === 'function') {
        _editorChangeSubscription.dispose()
        _editorChangeSubscription = null
      }
      var coreRef = editorComp.core || (editorComp.getCore ? editorComp.getCore() : null) || (editorComp.$refs && editorComp.$refs.editorCore)
      if (coreRef && typeof coreRef.onChangeSubscribe === 'function') {
        bindCm6Change(coreRef)
        return
      }
      var cm = editorComp.editor
      if (!cm) {
        setTimeout(tryAttach, 500)
        return
      }
      if (typeof cm.on === 'function') {
        bindCodeMirrorChange(cm)
      }
    }
    tryAttach()
  }

  function startDwellTimer () {
    if (_dwellTimer) clearInterval(_dwellTimer)
    _dwellTimer = setInterval(() => {
      _dwellWithoutEdit = Math.floor((Date.now() - vm().lastEditTime) / 1000)
      _frustrationScore = computeFrustrationScore()
      evaluateFrustration()
    }, 10000)
  }

  function computeFrustrationScore () {
    var clamp = function (v) { return Math.min(1, Math.max(0, v)) }
    var nErrors = clamp(vm().workflowContext.consecutiveErrors / 5)
    var nDeleteRatio = _totalOperations > 0 ? clamp(_deleteOperations / _totalOperations) : 0
    var nRapidResubmit = clamp(_rapidResubmitCount / 3)
    var nCodeChurn = (_totalInserted + _totalDeleted) > 0
      ? clamp((_totalDeleted / (_totalInserted + _totalDeleted)) / 0.8) : 0
    codeChurnValue.value = (_totalInserted + _totalDeleted) > 0
      ? _totalDeleted / (_totalInserted + _totalDeleted) : 0
    var nDwell = clamp(_dwellWithoutEdit / 300)
    var nHelpFail = _helpButStillFail ? 1 : 0

    return (
      0.35 * nErrors +
      0.20 * nDeleteRatio +
      0.15 * nRapidResubmit +
      0.15 * nCodeChurn +
      0.10 * nDwell +
      0.05 * nHelpFail
    )
  }

  function evaluateFrustration () {
    var THETA_LOW = 0.4
    var THETA_HIGH = 0.65
    var score = _frustrationScore
    if (_frustrationCooldown) return

    var cooldownKey = 'frustration_cooldown_' + vm().problemID
    var lastTrigger = localStorage.getItem(cooldownKey)
    if (lastTrigger && (Date.now() - parseInt(lastTrigger)) < 600000) return

    if (score >= THETA_HIGH) {
      triggerHighFrustration()
      localStorage.setItem(cooldownKey, String(Date.now()))
      _frustrationCooldown = true
      sendFrustrationAlert('critical')
    } else if (score >= THETA_LOW) {
      // 中等挫败仅保留教师预警，不再向学生侧弹出安抚卡，避免反复打断。
      sendFrustrationAlert('warning')
    }
  }

  function triggerHighFrustration () {
    api.analyzeFrustration({
      problem_id: vm().problem.id,
      behavior_features: {
        consecutiveErrors: vm().workflowContext.consecutiveErrors,
        deleteRatio: _totalOperations > 0 ? _deleteOperations / _totalOperations : 0,
        rapidResubmitCount: _rapidResubmitCount,
        codeChurn: (_totalInserted + _totalDeleted) > 0
          ? _totalDeleted / (_totalInserted + _totalDeleted) : 0,
        dwellWithoutEdit: _dwellWithoutEdit,
        helpButStillFail: _helpButStillFail
      }
    }).then(res => {
      var data = res.data.data
      _frustrationData = data
      encouragementText.value = data.encouragement || '你一直在努力，这已经很了不起了。'
      recoveryProblems.value = data.recovery_problems || []

      if (vm().pushAgentMessage) {
        if (!vm().agentPanelVisible) vm().agentPanelVisible = true
        vm().pushAgentMessage({
          type: 'encouragement',
          data: {
            level: 'severe',
            encouragement: encouragementText.value,
            recovery_problems: recoveryProblems.value,
            root_cause: data.root_cause
          }
        })
      } else {
        frustrationCardVisible.value = true
      }

      recordFrustrationEvent('intervention_shown', {
        frustration_score: _frustrationScore,
        root_cause: data.root_cause,
        encouragement_text: encouragementText.value
      })
    }).catch(() => {
      var fallbackText = '编程本就是不断试错的过程，坚持下去你一定能解决它。'
      if (vm().pushAgentMessage) {
        if (!vm().agentPanelVisible) vm().agentPanelVisible = true
        vm().pushAgentMessage({
          type: 'encouragement',
          data: { level: 'severe', encouragement: fallbackText, recovery_problems: [] }
        })
      } else {
        frustrationCardVisible.value = true
        frustrationLoading.value = false
        encouragementText.value = fallbackText
      }
    })
  }

  function dismissFrustrationCard () {
    frustrationCardVisible.value = false
    recordFrustrationEvent('intervention_dismissed', { frustration_score: _frustrationScore })
  }

  function navigateToRecoveryProblem (problem) {
    frustrationCardVisible.value = false
    var key = problem.problem_display_id || problem.problem_key
    vm().$router.push('/problem/' + key)
  }

  function recordFrustrationEvent (eventType, extraData) {
    api.recordFrustrationEvent({
      problem_id: vm().problem.id,
      event_type: eventType,
      extra_data: extraData || {}
    }).catch(() => {})
  }

  function sendFrustrationAlert (level) {
    var q = vm().$route && vm().$route.query ? vm().$route.query : {}
    if (!q.classroom_id) return
    api.sendFrustrationAlert({
      classroom_id: q.classroom_id,
      problem_id: vm().problem.id,
      problem_title: vm().problem.title,
      frustration_level: level,
      root_cause: _frustrationData ? _frustrationData.root_cause : '',
      frustration_score: _frustrationScore
    }).catch(() => {})
  }

  function onSubmitResultForFrustration (resultCode) {
    if (resultCode === 0) {
      if (_frustrationData) {
        recordFrustrationEvent('intervention_effective', {
          frustration_score: _frustrationScore,
          root_cause: _frustrationData.root_cause
        })
        _frustrationData = null
      }
      _helpButStillFail = false
      _aiTutorUsedSinceLastAC = false
      _rapidResubmitCount = 0
    } else {
      if (_aiTutorUsedSinceLastAC) {
        _helpButStillFail = true
      }
    }
  }

  function trackRapidResubmit () {
    var now = Date.now()
    if (_lastSubmitTime && (now - _lastSubmitTime) < 30000) {
      _rapidResubmitCount++
    }
    _lastSubmitTime = now
  }

  return {
    frustrationCardVisible,
    frustrationLoading,
    encouragementText,
    recoveryProblems,
    codeChurnValue,
    attachCodeMirrorChangeHandler,
    startDwellTimer,
    computeFrustrationScore,
    evaluateFrustration,
    triggerHighFrustration,
    dismissFrustrationCard,
    navigateToRecoveryProblem,
    recordFrustrationEvent,
    sendFrustrationAlert,
    onSubmitResultForFrustration,
    trackRapidResubmit
  }
}
