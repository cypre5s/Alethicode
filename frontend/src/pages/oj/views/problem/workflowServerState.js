import { markRaw } from 'vue'
import { QueryClient } from '@tanstack/vue-query'
import api from '@oj/api'

const SESSION_STALE_TIME_MS = 1500
const CHECKPOINTS_STALE_TIME_MS = 1500
const COURSEWARE_PREVIEW_STALE_TIME_MS = 5 * 60 * 1000
const SESSION_GC_TIME_MS = 5 * 60 * 1000

function extractApiData(response) {
  if (response && response.data && response.data.data !== undefined) {
    return response.data.data
  }
  return response ? response.data : null
}

export function createWorkflowSessionQueryClient() {
  return markRaw(new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
        staleTime: SESSION_STALE_TIME_MS,
        gcTime: SESSION_GC_TIME_MS,
        refetchOnWindowFocus: false
      }
    }
  }))
}

export function workflowSessionQueryKey(sessionId) {
  if (!sessionId) {
    throw new Error('workflow session id is required')
  }
  return ['tutor-workflow-session', sessionId]
}

export function workflowCheckpointsQueryKey(sessionId) {
  if (!sessionId) {
    throw new Error('workflow session id is required')
  }
  return ['tutor-workflow-checkpoints', sessionId]
}

export function coursewarePreviewQueryKey(languagePackId, documentId, pageNo) {
  if (!languagePackId) {
    throw new Error('language pack id is required')
  }
  if (!documentId) {
    throw new Error('document id is required')
  }
  if (!pageNo) {
    throw new Error('page number is required')
  }
  return ['courseware-preview-page', String(languagePackId), String(documentId), String(pageNo)]
}

export async function fetchWorkflowSessionSnapshot(queryClient, sessionId, options = {}) {
  if (!queryClient) {
    throw new Error('workflow query client is required')
  }
  const queryKey = workflowSessionQueryKey(sessionId)
  if (options.force) {
    await queryClient.invalidateQueries({ queryKey, exact: true })
  }
  return queryClient.fetchQuery({
    queryKey,
    staleTime: options.force ? 0 : SESSION_STALE_TIME_MS,
    queryFn: async () => {
      const response = await api.tutorWorkflowGetSession(sessionId, { silent: !!options.silent })
      return extractApiData(response)
    }
  })
}

export function setWorkflowSessionSnapshot(queryClient, sessionId, snapshot) {
  if (!queryClient || !sessionId || !snapshot) return
  queryClient.setQueryData(workflowSessionQueryKey(sessionId), snapshot)
}

export function getWorkflowSessionSnapshot(queryClient, sessionId) {
  if (!queryClient || !sessionId) return null
  return queryClient.getQueryData(workflowSessionQueryKey(sessionId)) || null
}

export function removeWorkflowSessionSnapshot(queryClient, sessionId) {
  if (!queryClient || !sessionId) return
  queryClient.removeQueries({ queryKey: workflowSessionQueryKey(sessionId), exact: true })
}

export async function fetchWorkflowCheckpoints(queryClient, sessionId, options = {}) {
  if (!queryClient) {
    throw new Error('workflow query client is required')
  }
  const queryKey = workflowCheckpointsQueryKey(sessionId)
  if (options.force) {
    await queryClient.invalidateQueries({ queryKey, exact: true })
  }
  return queryClient.fetchQuery({
    queryKey,
    staleTime: options.force ? 0 : CHECKPOINTS_STALE_TIME_MS,
    queryFn: async () => {
      const response = await api.tutorWorkflowGetCheckpoints(sessionId)
      const data = extractApiData(response)
      return data && Array.isArray(data.checkpoints) ? data.checkpoints : []
    }
  })
}

export function setWorkflowCheckpoints(queryClient, sessionId, checkpoints) {
  if (!queryClient || !sessionId || !Array.isArray(checkpoints)) return
  queryClient.setQueryData(workflowCheckpointsQueryKey(sessionId), checkpoints)
}

export function getWorkflowCheckpoints(queryClient, sessionId) {
  if (!queryClient || !sessionId) return []
  return queryClient.getQueryData(workflowCheckpointsQueryKey(sessionId)) || []
}

export function removeWorkflowCheckpoints(queryClient, sessionId) {
  if (!queryClient || !sessionId) return
  queryClient.removeQueries({ queryKey: workflowCheckpointsQueryKey(sessionId), exact: true })
}

export async function fetchCoursewarePreviewPage(queryClient, languagePackId, documentId, pageNo, options = {}) {
  if (!queryClient) {
    throw new Error('workflow query client is required')
  }
  const queryKey = coursewarePreviewQueryKey(languagePackId, documentId, pageNo)
  if (options.force) {
    await queryClient.invalidateQueries({ queryKey, exact: true })
  }
  return queryClient.fetchQuery({
    queryKey,
    staleTime: options.force ? 0 : COURSEWARE_PREVIEW_STALE_TIME_MS,
    queryFn: async () => {
      const response = await api.getLanguagePackQaCitationPage(languagePackId, documentId, pageNo)
      return extractApiData(response)
    }
  })
}

export function setCoursewarePreviewPage(queryClient, languagePackId, documentId, pageNo, page) {
  if (!queryClient || !languagePackId || !documentId || !pageNo || !page) return
  queryClient.setQueryData(coursewarePreviewQueryKey(languagePackId, documentId, pageNo), page)
}

export function getCoursewarePreviewPage(queryClient, languagePackId, documentId, pageNo) {
  if (!queryClient || !languagePackId || !documentId || !pageNo) return null
  return queryClient.getQueryData(coursewarePreviewQueryKey(languagePackId, documentId, pageNo)) || null
}

export function removeCoursewarePreviewPage(queryClient, languagePackId, documentId, pageNo) {
  if (!queryClient || !languagePackId || !documentId || !pageNo) return
  queryClient.removeQueries({
    queryKey: coursewarePreviewQueryKey(languagePackId, documentId, pageNo),
    exact: true
  })
}
