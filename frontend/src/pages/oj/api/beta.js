/**
 * 公测反馈与遥测 API。
 *
 * 入口：
 *   - createBetaFeedback(data, screenshots) - multipart/form-data 提交反馈与可选截图
 *   - reportBetaTelemetryBatch(events)      - 批量遥测事件，silent 模式（不弹错误提示）
 *   - reportBetaWebVital(metric)            - 单条 Web Vital，silent
 *
 * 注意：
 *   - 截图必须按 `screenshots` 字段名追加，每个 File 一次 append。
 *   - data 部分用 `Blob` 包成 application/json，避免被 FormData 当成纯文本。
 */

import { ajax } from './shared'
import { getHttpClient } from '@/api/httpClient'

const httpClient = getHttpClient()

export default {
  createBetaFeedback(data, screenshots) {
    const form = new FormData()
    form.append('data', new Blob([JSON.stringify(data || {})], { type: 'application/json' }))
    if (screenshots && screenshots.length) {
      for (const file of screenshots) {
        if (file) form.append('screenshots', file)
      }
    }
    return httpClient.post('beta/feedback-reports', form, {
      headers: { 'Content-Type': 'multipart/form-data' }
    }).then(res => {
      if (res && res.data && res.data.error !== null) {
        return Promise.reject(res)
      }
      return res
    })
  },
  reportBetaTelemetryBatch(events) {
    return ajax('beta/telemetry/events', 'post', {
      data: { events: Array.isArray(events) ? events : [] },
      silent: true
    })
  },
  reportBetaWebVital(metric) {
    return ajax('beta/telemetry/web-vitals', 'post', {
      data: metric || {},
      silent: true
    })
  }
}
