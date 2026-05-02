/* eslint-env jest */

const fs = require('fs')
const path = require('path')
const babel = require('@babel/core')
const { reactive } = require('vue')

jest.mock('@oj/api', () => ({
  __esModule: true,
  default: {
    tutorWorkflowGetSession: jest.fn(),
    tutorWorkflowGetCheckpoints: jest.fn(),
    getLanguagePackQaCitationPage: jest.fn()
  }
}))

const PRIVATE_DEFAULT_OPTIONS = new WeakMap()

function loadModule() {
  const filePath = path.resolve(__dirname, '../../src/pages/oj/views/problem/workflowServerState.js')
  const source = fs.readFileSync(filePath, 'utf8')
  const transformed = babel.transformSync(source, {
    filename: filePath,
    presets: [require.resolve('@babel/preset-env')]
  })
  const module = { exports: {} }
  const localRequire = (request) => {
    if (request === '@tanstack/vue-query') {
      class QueryClient {
        constructor(options = {}) {
          PRIVATE_DEFAULT_OPTIONS.set(this, options.defaultOptions || {})
          this.cache = new Map()
          this.inFlight = new Map()
        }

        defaultQueryOptions() {
          if (!PRIVATE_DEFAULT_OPTIONS.has(this)) {
            throw new TypeError('Cannot read private member #defaultOptions from an object whose class did not declare it')
          }
          return PRIVATE_DEFAULT_OPTIONS.get(this)
        }

        async fetchQuery({ queryKey, queryFn, staleTime = 0 }) {
          const key = JSON.stringify(queryKey)
          const cached = this.cache.get(key)
          const now = Date.now()
          if (cached && cached.expiresAt > now) {
            return cached.data
          }
          if (this.inFlight.has(key)) {
            return this.inFlight.get(key)
          }
          const promise = Promise.resolve(queryFn()).then((data) => {
            this.cache.set(key, { data, expiresAt: now + staleTime })
            this.inFlight.delete(key)
            return data
          }).catch((error) => {
            this.inFlight.delete(key)
            throw error
          })
          this.inFlight.set(key, promise)
          return promise
        }

        setQueryData(queryKey, data) {
          this.defaultQueryOptions()
          this.cache.set(JSON.stringify(queryKey), { data, expiresAt: Number.MAX_SAFE_INTEGER })
        }

        getQueryData(queryKey) {
          const cached = this.cache.get(JSON.stringify(queryKey))
          return cached ? cached.data : undefined
        }

        async invalidateQueries({ queryKey }) {
          this.cache.delete(JSON.stringify(queryKey))
        }

        removeQueries({ queryKey }) {
          this.cache.delete(JSON.stringify(queryKey))
        }
      }

      return { QueryClient }
    }
    return require(request)
  }
  // eslint-disable-next-line no-new-func
  const fn = new Function('module', 'exports', 'require', transformed.code)
  fn(module, module.exports, localRequire)
  return module.exports
}

describe('workflow server state query client', () => {
  beforeEach(() => {
    jest.resetModules()
    jest.clearAllMocks()
  })

  test('fetchWorkflowSessionSnapshot should deduplicate in-flight requests for the same session', async () => {
    const api = require('@oj/api').default
    const payload = { session_id: 'session-1', phase: 'READING' }
    let resolveRequest
    api.tutorWorkflowGetSession.mockImplementation(() => new Promise((resolve) => {
      resolveRequest = () => resolve({ data: { data: payload } })
    }))

    const {
      createWorkflowSessionQueryClient,
      fetchWorkflowSessionSnapshot
    } = loadModule()
    const queryClient = createWorkflowSessionQueryClient()

    const first = fetchWorkflowSessionSnapshot(queryClient, 'session-1')
    const second = fetchWorkflowSessionSnapshot(queryClient, 'session-1')

    expect(api.tutorWorkflowGetSession).toHaveBeenCalledTimes(1)

    resolveRequest()

    await expect(first).resolves.toEqual(payload)
    await expect(second).resolves.toEqual(payload)
  })

  test('fetchWorkflowSessionSnapshot should reuse fresh cache until force refresh is requested', async () => {
    const api = require('@oj/api').default
    api.tutorWorkflowGetSession
      .mockResolvedValueOnce({ data: { data: { session_id: 'session-2', phase: 'READING' } } })
      .mockResolvedValueOnce({ data: { data: { session_id: 'session-2', phase: 'IDEATING' } } })

    const {
      createWorkflowSessionQueryClient,
      fetchWorkflowSessionSnapshot,
      getWorkflowSessionSnapshot
    } = loadModule()
    const queryClient = createWorkflowSessionQueryClient()

    const first = await fetchWorkflowSessionSnapshot(queryClient, 'session-2')
    const second = await fetchWorkflowSessionSnapshot(queryClient, 'session-2')
    const forced = await fetchWorkflowSessionSnapshot(queryClient, 'session-2', { force: true })

    expect(first.phase).toBe('READING')
    expect(second.phase).toBe('READING')
    expect(forced.phase).toBe('IDEATING')
    expect(getWorkflowSessionSnapshot(queryClient, 'session-2')).toEqual(forced)
    expect(api.tutorWorkflowGetSession).toHaveBeenCalledTimes(2)
  })

  test('setWorkflowSessionSnapshot should keep QueryClient usable inside a reactive owner', () => {
    const {
      createWorkflowSessionQueryClient,
      setWorkflowSessionSnapshot,
      getWorkflowSessionSnapshot
    } = loadModule()

    const owner = reactive({
      queryClient: createWorkflowSessionQueryClient()
    })

    expect(() => {
      setWorkflowSessionSnapshot(owner.queryClient, 'session-3', {
        session_id: 'session-3',
        phase: 'READING'
      })
    }).not.toThrow()

    expect(getWorkflowSessionSnapshot(owner.queryClient, 'session-3')).toEqual({
      session_id: 'session-3',
      phase: 'READING'
    })
  })

  test('fetchWorkflowCheckpoints should deduplicate and cache checkpoint requests', async () => {
    const api = require('@oj/api').default
    api.tutorWorkflowGetCheckpoints
      .mockResolvedValueOnce({
        data: {
          data: {
            checkpoints: [
              { checkpoint_id: 'cp-1' },
              { checkpoint_id: 'cp-2' }
            ]
          }
        }
      })
      .mockResolvedValueOnce({
        data: {
          data: {
            checkpoints: [
              { checkpoint_id: 'cp-3' }
            ]
          }
        }
      })

    const {
      createWorkflowSessionQueryClient,
      fetchWorkflowCheckpoints,
      getWorkflowCheckpoints
    } = loadModule()
    const queryClient = createWorkflowSessionQueryClient()

    const first = await fetchWorkflowCheckpoints(queryClient, 'session-4')
    const second = await fetchWorkflowCheckpoints(queryClient, 'session-4')
    const forced = await fetchWorkflowCheckpoints(queryClient, 'session-4', { force: true })

    expect(first).toEqual([{ checkpoint_id: 'cp-1' }, { checkpoint_id: 'cp-2' }])
    expect(second).toEqual(first)
    expect(forced).toEqual([{ checkpoint_id: 'cp-3' }])
    expect(getWorkflowCheckpoints(queryClient, 'session-4')).toEqual(forced)
    expect(api.tutorWorkflowGetCheckpoints).toHaveBeenCalledTimes(2)
  })

  test('fetchCoursewarePreviewPage should reuse cached preview data until forced refresh', async () => {
    const api = require('@oj/api').default
    api.getLanguagePackQaCitationPage
      .mockResolvedValueOnce({
        data: {
          data: {
            document_id: 11,
            page_no: 3,
            page_text: '第一页正文'
          }
        }
      })
      .mockResolvedValueOnce({
        data: {
          data: {
            document_id: 11,
            page_no: 3,
            page_text: '刷新后的正文'
          }
        }
      })

    const {
      createWorkflowSessionQueryClient,
      fetchCoursewarePreviewPage,
      getCoursewarePreviewPage
    } = loadModule()
    const queryClient = createWorkflowSessionQueryClient()

    const first = await fetchCoursewarePreviewPage(queryClient, 7, 11, 3)
    const second = await fetchCoursewarePreviewPage(queryClient, 7, 11, 3)
    const forced = await fetchCoursewarePreviewPage(queryClient, 7, 11, 3, { force: true })

    expect(first.page_text).toBe('第一页正文')
    expect(second.page_text).toBe('第一页正文')
    expect(forced.page_text).toBe('刷新后的正文')
    expect(getCoursewarePreviewPage(queryClient, 7, 11, 3)).toEqual(forced)
    expect(api.getLanguagePackQaCitationPage).toHaveBeenCalledTimes(2)
  })
})
