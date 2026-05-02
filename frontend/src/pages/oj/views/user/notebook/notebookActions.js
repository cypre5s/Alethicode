import api from '@oj/api'
import { encodeRouteCtx } from '@/utils/urlCipher'
import { normalizeEntries } from './notebookFormatters.js'

export async function fetchNotebookEntries() {
  const res = await api.getLearnerNotebook({})
  const raw = (res.data && res.data.data && res.data.data.entries) || []
  return normalizeEntries(raw)
}

export async function fetchDueReviews(limit = 20) {
  try {
    const res = await api.getReviewDue(limit)
    return (res.data && res.data.data && res.data.data.due_reviews) || []
  } catch { return [] }
}

export async function fetchMisconceptions() {
  try {
    const res = await api.getMyMisconceptions()
    return (res.data && res.data.data && res.data.data.misconceptions) || []
  } catch { return [] }
}

export async function fetchClassFrequency() {
  try {
    const res = await api.getNotebookClassFrequency()
    const list = res.data.data || []
    const map = {}
    for (const item of list) {
      if (item.error_taxonomy && item.total_classmates > 0) {
        map[item.error_taxonomy] = Math.round((item.classmate_count / item.total_classmates) * 100)
      }
    }
    return map
  } catch { return {} }
}

export async function generateReflection(entry) {
  const res = await api.generateNotebookReflection({
    error_taxonomy: entry.error_taxonomy,
    root_cause: entry.root_cause,
    fix_outcome: entry.fix_outcome
  })
  return (res.data && res.data.data && res.data.data.reflection) || ''
}

export function buildReviewPackageGroups(entries, selectedCategory = '') {
  const source = selectedCategory
    ? (entries || []).filter(entry => entry && entry.error_taxonomy === selectedCategory)
    : (entries || [])
  const seen = new Set()
  const groups = []
  for (const entry of source) {
    if (!entry || !entry.error_taxonomy || seen.has(entry.error_taxonomy)) continue
    seen.add(entry.error_taxonomy)
    groups.push({ taxonomy: entry.error_taxonomy, anchor: entry })
  }
  return groups
}

export async function createReviewPackages({ groups }) {
  if (!Array.isArray(groups) || groups.length === 0) {
    const err = new Error('缺少错题分组')
    err.userMessage = '暂无错题记录，无法生成强化训练'
    throw err
  }
  const items = groups.map(group => {
    const anchor = group.anchor
    const languagePackId = anchor && anchor.language_pack_id ? Number(anchor.language_pack_id) : null
    if (!languagePackId) {
      const err = new Error('缺少课程内容包信息')
      err.userMessage = '缺少课程内容包信息，暂时无法生成强化训练'
      throw err
    }
    return {
      error_taxonomy: group.taxonomy,
      language_pack_id: languagePackId,
      problem_id: anchor && anchor.problem_id ? anchor.problem_id : null,
      trigger: 'wrong_answer'
    }
  })
  const res = await api.createReviewPackages({ items })
  return res.data.data || []
}

export function buildReviewPackageRoute(packageId, packageIds = []) {
  const ctx = { pkg: packageId }
  if (Array.isArray(packageIds) && packageIds.length > 1) ctx.pkgs = packageIds
  return { name: 'error-review-package', query: { ctx: encodeRouteCtx(ctx) } }
}

export async function exportNotebookAsJsonFile() {
  const res = await api.exportLearnerNotebook()
  const payload = (res.data && res.data.data) || {}
  const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json;charset=utf-8' })
  const url = window.URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `learner_notebook_${Date.now()}.json`
  a.click()
  window.URL.revokeObjectURL(url)
}
