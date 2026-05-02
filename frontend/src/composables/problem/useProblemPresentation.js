import { ref, getCurrentInstance, onBeforeUnmount } from 'vue'
import api from '@oj/api'

export function useProblemPresentation () {
  const instance = getCurrentInstance()
  const vm = () => instance.proxy

  const showSuccessOverlay = ref(false)
  const successResult = ref(null)
  const confettiAnimId = ref(null)
  const riverVisible = ref(false)
  const riverData = ref(null)
  const riverLoading = ref(false)
  const riverLoaded = ref(false)

  let _successEscHandler = null

  function showSuccessAnimation (result) {
    successResult.value = result
    showSuccessOverlay.value = true
    _successEscHandler = (e) => {
      if (e.key === 'Escape') closeSuccess()
    }
    document.addEventListener('keydown', _successEscHandler)
    const prefersReduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    if (!prefersReduced) {
      setTimeout(() => launchConfetti(), 0)
    }
  }

  function closeSuccess () {
    showSuccessOverlay.value = false
    stopConfetti()
    if (_successEscHandler) {
      document.removeEventListener('keydown', _successEscHandler)
      _successEscHandler = null
    }
  }

  function viewSubmissionDetails () {
    closeSuccess()
    if (vm().submissionId) {
      vm().$router.push('/status/' + vm().submissionId)
    }
  }

  function toggleRiver () {
    riverVisible.value = !riverVisible.value
    if (riverVisible.value && !riverLoaded.value) {
      loadRiver()
    }
  }

  function loadRiver () {
    if (!vm().problem || !vm().problem.id) return
    riverLoading.value = true
    api.getSubmissionRiver(vm().problem.id).then(res => {
      const raw = (res.data && res.data.data) || null
      riverData.value = normalizeRiverPayload(raw)
      riverLoading.value = false
      riverLoaded.value = true
    }).catch(() => {
      riverData.value = null
      riverLoading.value = false
    })
  }

  function normalizeRiverPayload (payload) {
    if (!payload || typeof payload !== 'object') return null
    if (Array.isArray(payload.submissions)) return payload
    const timeline = Array.isArray(payload.timeline) ? payload.timeline : []
    const submissions = timeline.map((item, index) => ({
      id: item.submission_id || ('sub_' + index),
      attempt_number: index + 1,
      result: item.result,
      result_label: riverResultLabel(item.result),
      language: item.language || '',
      created_at: item.create_time || '',
      line_count: safeLineCount(item.code_preview || item.code || ''),
      code_preview: item.code_preview || item.code || ''
    }))
    const semanticDiffs = []
    for (let i = 1; i < submissions.length; i++) {
      const prevCode = submissions[i - 1].code_preview || ''
      const currCode = submissions[i].code_preview || ''
      const prevLines = prevCode ? prevCode.split('\n') : []
      const currLines = currCode ? currCode.split('\n') : []
      const added = Math.max(0, currLines.length - prevLines.length)
      const deleted = Math.max(0, prevLines.length - currLines.length)
      const denom = Math.max(1, prevLines.length)
      const ratio = Math.min(1, Math.abs(currLines.length - prevLines.length) / denom)
      semanticDiffs.push({
        summary: semanticSummary(submissions[i - 1], submissions[i]),
        structural_changes: [], lines_added: added, lines_deleted: deleted,
        diff_ratio: ratio, agent_seen_between: false
      })
    }
    return {
      insufficient_data: submissions.length < 2, submissions,
      stats: { total_submissions: submissions.length, total_duration_seconds: 0, agent_interactions: 0 },
      strategy_phases: [], breakthrough_index: null, semantic_diffs: semanticDiffs,
      misconception_events: [], narrative: ''
    }
  }

  function riverResultLabel (result) {
    const c = Number(result)
    if (c === 0) return 'AC'
    if (c === -1) return 'WA'
    if (c === 1 || c === 2) return 'TLE'
    if (c === 3) return 'MLE'
    if (c === 4) return 'RE'
    if (c === 5) return 'SE'
    if (c === 6) return 'Pending'
    if (c === 7) return 'Judging'
    if (c === 8) return 'PAC'
    if (c === -2) return 'CE'
    return String(result == null ? 'Unknown' : result)
  }

  function safeLineCount (codeText) {
    const text = typeof codeText === 'string' ? codeText : ''
    if (!text) return 0
    return text.split('\n').length
  }

  function semanticSummary (prev, curr) {
    const prevLabel = prev && prev.result_label ? prev.result_label : 'Unknown'
    const currLabel = curr && curr.result_label ? curr.result_label : 'Unknown'
    if (prevLabel === currLabel) return `保持 ${currLabel}，继续优化实现`
    return `${prevLabel} -> ${currLabel}`
  }

  function launchConfetti () {
    const canvas = vm().$refs.confettiCanvas
    if (!canvas) return
    canvas.width = window.innerWidth
    canvas.height = window.innerHeight
    const ctx = canvas.getContext('2d')
    const COLORS = ['#2563eb', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#ec4899']
    const particles = []
    for (let i = 0; i < 120; i++) {
      particles.push({
        x: Math.random() * canvas.width, y: canvas.height * Math.random() * -1,
        r: Math.random() * 6 + 3, vy: Math.random() * 4 + 2, vx: (Math.random() - 0.5) * 3,
        rot: Math.random() * 360, vr: Math.random() * 8 - 4,
        color: COLORS[Math.floor(Math.random() * COLORS.length)], alpha: 1
      })
    }
    function draw () {
      ctx.clearRect(0, 0, canvas.width, canvas.height)
      particles.forEach(p => {
        p.y += p.vy; p.x += p.vx; p.rot += p.vr
        if (p.y > canvas.height) p.alpha -= 0.04
        ctx.save()
        ctx.translate(p.x, p.y)
        ctx.rotate(p.rot * Math.PI / 180)
        ctx.globalAlpha = Math.max(0, p.alpha)
        ctx.fillStyle = p.color
        ctx.fillRect(-p.r, -p.r / 2, p.r * 2, p.r)
        ctx.restore()
      })
      if (particles.some(p => p.alpha > 0)) {
        confettiAnimId.value = requestAnimationFrame(draw)
      }
    }
    draw()
  }

  function stopConfetti () {
    if (confettiAnimId.value) {
      cancelAnimationFrame(confettiAnimId.value)
      confettiAnimId.value = null
    }
  }

  function downloadDataset () {
    const query = [
      `problem_id=${encodeURIComponent(vm().problemID)}`,
      'file=dataset'
    ]
    window.open(`/api/problems/dataset/download/?${query.join('&')}`, '_blank')
  }

  onBeforeUnmount(() => {
    stopConfetti()
    if (_successEscHandler) {
      document.removeEventListener('keydown', _successEscHandler)
      _successEscHandler = null
    }
  })

  return {
    showSuccessOverlay, successResult, confettiAnimId,
    riverVisible, riverData, riverLoading, riverLoaded,
    showSuccessAnimation, closeSuccess, viewSubmissionDetails,
    toggleRiver, loadRiver, normalizeRiverPayload,
    riverResultLabel, safeLineCount, semanticSummary,
    launchConfetti, stopConfetti, downloadDataset
  }
}
