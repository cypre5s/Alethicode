const { test, expect } = require('@playwright/test')
const {
  resolveRealBackendConfig,
  loginViaApi
} = require('./support/authRegressionHelper')

const REAL_BACKEND_E2E = process.env.REAL_BACKEND_E2E === '1'
const DEFAULT_PROBLEM_ID = Number(process.env.E2E_PROBLEM_ID || 379)
const PLAN_POLL_TIMEOUT_MS = 30000

async function fetchCsrfToken(page, baseUrl) {
  const cookies = await page.context().cookies(baseUrl)
  const existingCookie = cookies.find(cookie => cookie.name === 'csrftoken')
  if (existingCookie && existingCookie.value) {
    return existingCookie.value
  }

  const response = await page.request.get(`${baseUrl}/api/csrf`)
  expect(response.ok()).toBeTruthy()

  const refreshedCookies = await page.context().cookies(baseUrl)
  const csrfCookie = refreshedCookies.find(cookie => cookie.name === 'csrftoken')
  expect(csrfCookie && csrfCookie.value).toBeTruthy()
  return csrfCookie.value
}

async function postJson(page, baseUrl, path, data) {
  const csrfToken = await fetchCsrfToken(page, baseUrl)
  const response = await page.request.post(`${baseUrl}${path}`, {
    headers: {
      'X-CSRFToken': csrfToken
    },
    data
  })
  const payload = await response.json()
  return { response, payload }
}

async function getSessionSnapshot(page, baseUrl, sessionId) {
  const response = await page.request.get(`${baseUrl}/api/ai/tutor-workflow-sessions/${sessionId}`)
  expect(response.ok()).toBeTruthy()
  return response.json()
}

async function waitForPlan(page, baseUrl, sessionId, selector) {
  let plan = null
  await expect.poll(async () => {
    const snapshot = await getSessionSnapshot(page, baseUrl, sessionId)
    plan = snapshot.data && snapshot.data.plan
    return selector(plan)
  }, { timeout: PLAN_POLL_TIMEOUT_MS }).toBeTruthy()
  return plan
}

async function openTutorRuntimeSocket(page, sessionId) {
  await page.evaluate(targetSessionId => {
    if (window.__tutorRuntimeSocket) {
      window.__tutorRuntimeSocket.close()
    }
    window.__tutorRuntimeEvents = []
    const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
    const socket = new WebSocket(`${protocol}://${window.location.host}/ws/tutor-workflow-sessions/${targetSessionId}`)
    socket.onmessage = event => {
      try {
        const payload = JSON.parse(event.data)
        window.__tutorRuntimeEvents.push(payload)
      } catch (error) {
        window.__tutorRuntimeEvents.push({ parse_error: true, raw: event.data })
      }
    }
    window.__tutorRuntimeSocket = socket
  }, sessionId)

  await expect.poll(async () => {
    return page.evaluate(() => window.__tutorRuntimeSocket && window.__tutorRuntimeSocket.readyState)
  }, { timeout: 10000 }).toBe(1)
}

async function waitForRuntimeEvent(page, fromIndex, matcher) {
  let matched = null
  await expect.poll(async () => {
    const events = await page.evaluate(() => window.__tutorRuntimeEvents || [])
    const candidates = events.slice(fromIndex)
    matched = candidates.find(matcher) || null
    return Boolean(matched)
  }, { timeout: PLAN_POLL_TIMEOUT_MS }).toBeTruthy()
  return matched
}

test.describe('Tutor Workflow Plan Loop (Real Backend)', () => {
  test.skip(!REAL_BACKEND_E2E, 'Set REAL_BACKEND_E2E=1 to run tutor workflow integration on a real backend')

  test('frontend proxy can drive plan lifecycle end-to-end', async ({ page }) => {
    const config = resolveRealBackendConfig()
    const problemId = DEFAULT_PROBLEM_ID

    await loginViaApi(page, config)
    await page.goto(`${config.baseUrl}/problem/${problemId}`)
    await page.waitForLoadState('networkidle')
    await expect(page.locator('body')).toContainText(/提交|AI 学习助手|题目描述/)

    const created = await postJson(page, config.baseUrl, '/api/ai/tutor-workflow-sessions', {
      problem_id: problemId,
      language: 'Python3'
    })
    expect(created.response.status()).toBe(201)
    expect(created.payload.error).toBeNull()

    const sessionId = created.payload.data && created.payload.data.session_id
    expect(sessionId).toMatch(/^twf_/)
    await openTutorRuntimeSocket(page, sessionId)

    const startEventIndex = await page.evaluate(() => (window.__tutorRuntimeEvents || []).length)
    const started = await postJson(page, config.baseUrl, `/api/ai/tutor-workflow-sessions/${sessionId}/runs`, {
      event: 'PLAN_START',
      event_data: {
        reason: '联调用例：学生确认进入陪练',
        trigger_source: 'integration_test',
        current_phase: 'CODING'
      }
    })
    expect(started.response.status()).toBe(202)
    expect(started.payload.error).toBeNull()
    expect(started.payload.data.runtime_state).toBe('QUEUED')

    const completedStartEvent = await waitForRuntimeEvent(page, startEventIndex, event => {
      return event.server_event === 'TASK_COMPLETED' && event.client_event === 'PLAN_START'
    })
    expect(completedStartEvent.data.plan.status).toBe('active')
    expect(completedStartEvent.data.plan.current_step.step_id).toBe('task_representation')

    const startedPlan = await waitForPlan(page, config.baseUrl, sessionId, plan => {
      return Boolean(
        plan &&
        plan.status === 'active' &&
        plan.current_step_index === 0 &&
        plan.current_step &&
        plan.current_step.step_id === 'task_representation' &&
        plan.recommendation_reason === '联调用例：学生确认进入陪练'
      )
    })

    expect(Array.isArray(startedPlan.steps)).toBeTruthy()
    expect(startedPlan.steps).toHaveLength(5)
    expect(startedPlan.current_step.evidence_type).toBe('text')
    expect(startedPlan.recommendation_reason).toBe('联调用例：学生确认进入陪练')

    const advanceEventIndex = await page.evaluate(() => (window.__tutorRuntimeEvents || []).length)
    const advanced = await postJson(page, config.baseUrl, `/api/ai/tutor-workflow-sessions/${sessionId}/runs`, {
      event: 'PLAN_RESPONSE',
      event_data: {
        plan_id: startedPlan.plan_id,
        step_id: startedPlan.current_step.step_id,
        evidence_type: 'text',
        response_text: '这题要读取输入并输出结果，我最担心的是边界条件和输入格式。'
      }
    })
    expect(advanced.response.status()).toBe(202)
    expect(advanced.payload.error).toBeNull()

    const completedAdvanceEvent = await waitForRuntimeEvent(page, advanceEventIndex, event => {
      return event.server_event === 'TASK_COMPLETED' && event.client_event === 'PLAN_RESPONSE'
    })
    expect(completedAdvanceEvent.data.plan.current_step_index).toBe(1)
    expect(completedAdvanceEvent.data.plan.current_step.step_id).toBe('idea_externalization')

    const secondStep = await waitForPlan(page, config.baseUrl, sessionId, plan => {
      return Boolean(
        plan &&
        plan.status === 'active' &&
        plan.current_step_index === 1 &&
        plan.current_step &&
        plan.current_step.step_id === 'idea_externalization'
      )
    })
    expect(secondStep.current_step.evidence_type).toBe('text')

    const pauseEventIndex = await page.evaluate(() => (window.__tutorRuntimeEvents || []).length)
    const paused = await postJson(page, config.baseUrl, `/api/ai/tutor-workflow-sessions/${sessionId}/runs`, {
      event: 'PLAN_STEERING',
      event_data: {
        plan_id: secondStep.plan_id,
        signal_type: 'pause'
      }
    })
    expect(paused.response.status()).toBe(202)
    expect(paused.payload.error).toBeNull()

    await waitForRuntimeEvent(page, pauseEventIndex, event => {
      return event.server_event === 'TASK_COMPLETED' &&
        event.client_event === 'PLAN_STEERING' &&
        event.data &&
        event.data.plan &&
        event.data.plan.status === 'paused'
    })

    await expect.poll(async () => {
      const snapshot = await getSessionSnapshot(page, config.baseUrl, sessionId)
      return snapshot.data && snapshot.data.plan && snapshot.data.plan.status
    }, { timeout: PLAN_POLL_TIMEOUT_MS }).toBe('paused')

    const resumeEventIndex = await page.evaluate(() => (window.__tutorRuntimeEvents || []).length)
    const resumed = await postJson(page, config.baseUrl, `/api/ai/tutor-workflow-sessions/${sessionId}/runs`, {
      event: 'PLAN_STEERING',
      event_data: {
        plan_id: secondStep.plan_id,
        signal_type: 'resume'
      }
    })
    expect(resumed.response.status()).toBe(202)
    expect(resumed.payload.error).toBeNull()

    await waitForRuntimeEvent(page, resumeEventIndex, event => {
      return event.server_event === 'TASK_COMPLETED' &&
        event.client_event === 'PLAN_STEERING' &&
        event.data &&
        event.data.plan &&
        event.data.plan.status === 'active'
    })

    await expect.poll(async () => {
      const snapshot = await getSessionSnapshot(page, config.baseUrl, sessionId)
      return snapshot.data && snapshot.data.plan && snapshot.data.plan.status
    }, { timeout: PLAN_POLL_TIMEOUT_MS }).toBe('active')

    const invalid = await postJson(page, config.baseUrl, `/api/ai/tutor-workflow-sessions/${sessionId}/runs`, {
      event: 'PLAN_RESPONSE',
      event_data: {
        plan_id: 'plan_missing_fields'
      }
    })

    expect(invalid.response.status()).toBe(422)
    expect(invalid.payload.error).toMatch(/step_id is required|evidence_type is required/)
  })
})
