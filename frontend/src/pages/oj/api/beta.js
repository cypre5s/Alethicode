/**
 * 公测反馈与遥测 API。
 *
 * 反馈表单要求 JSON 部分使用 `data` Blob，截图逐个追加到 `screenshots` 字段。
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
