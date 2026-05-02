/**
 * OJ 端 API 模块共享基础设施
 *
 * 拆分 `api.js` 时抽取出来的底层依赖，集中了：
 *   1. `httpClient` - 从全局 HTTP 工厂取到的 axios 实例
 *   2. `ajax(url, method, options)` - 统一的请求包装，负责业务层 error 处理与登录弹窗
 *   3. `tryAttachCollabSessionId` - 提交代码时补 classroom_session_id 的工具
 *   4. 图形验证码响应规范化：`normalizeCaptchaResponse` 等
 *
 * 所有业务域模块（account/problem/... ）只需要从这里引入 `ajax` 与 `tryAttachCollabSessionId`，
 * 不再各自持有底层 http 逻辑，便于统一调整鉴权 / 错误提示策略。
 */

import store from '@/store'
import { getHttpClient } from '@/api/httpClient'
import { notify } from '@/utils/notifications'

const httpClient = getHttpClient()

/**
 * 在协作（classroom collab）路由下自动补齐 `classroom_session_id`，
 * 避免每个调用方手写一遍 URL 解析。
 *
 * @param {Object} payload - 原始业务载荷
 * @returns {Object} 已补齐 `classroom_session_id`（若适用）的新对象
 */
export function tryAttachCollabSessionId(payload) {
  if (!payload || payload.problem_id) return payload
  if (typeof window === 'undefined' || !window.location || !window.location.pathname) return payload
  const match = window.location.pathname.match(/\/classroom\/[^/]+\/collab\/([^/]+)/)
  if (!match || !match[1]) return payload
  return Object.assign({}, payload, { classroom_session_id: match[1] })
}

/**
 * 统一的 AJAX 调用入口。
 *
 * @param {string} url - 相对后端前缀的路径
 * @param {string} method - HTTP 方法 (get/post/put/delete/patch)
 * @param {Object} [options]
 * @param {Object} [options.params] - URL 查询参数
 * @param {Object|FormData} [options.data] - 请求体（POST/PUT/PATCH）
 * @param {boolean} [options.silent=false] - 为 true 时抑制业务错误的弹窗通知
 * @param {AbortSignal} [options.signal] - 可选的 AbortController 信号
 * @param {number} [options.timeout=0] - 覆盖默认超时（毫秒，0 表示跟随 httpClient 默认）
 * @returns {Promise} axios response；业务 `error` 字段非空会被 reject
 */
export function ajax(url, method, options) {
  let params = {}
  let data = {}
  let silent = false
  let signal = null
  let timeout = 0
  if (options !== undefined) {
    ({ params = {}, data = {}, silent = false, signal = null, timeout = 0 } = options)
  }
  return new Promise((resolve, reject) => {
    httpClient({
      url,
      method,
      params,
      data,
      signal,
      timeout
    }).then(res => {
      if (res.status === 204 || res.data === '' || res.data === null || typeof res.data === 'undefined') {
        resolve(Object.assign({}, res, { data: { error: null, data: 'success' } }))
        return
      }
      if (res.data.error !== null) {
        const errorMsg = res.data.data || res.data.error || 'An error occurred'
        if (!silent) {
          notify.error(errorMsg)
        }
        reject(res)
        if (res.data.error === 'permission-denied' &&
          typeof errorMsg === 'string' && errorMsg.startsWith('Please login')) {
          store.dispatch('changeModalStatus', { 'mode': 'login', 'visible': true })
        }
      } else {
        resolve(res)
      }
    }, res => {
      reject(res)
      if (silent) {
        return
      }
      if (res && res.response && res.response.status === 401) {
        store.dispatch('changeModalStatus', { 'mode': 'login', 'visible': true })
        return
      }
      let errorMsg = 'Unknown Error'
      if (res.response && res.response.data && res.response.data.data) {
        errorMsg = res.response.data.data
      } else if (res.response && res.response.data && res.response.data.error) {
        errorMsg = res.response.data.error
      } else if (res.data && res.data.data) {
        errorMsg = res.data.data
      } else if (res.message) {
        errorMsg = res.message
      }
      notify.error(typeof errorMsg === 'string' ? errorMsg : JSON.stringify(errorMsg))
    })
  })
}

/**
 * 把后端返回的验证码负载规范化为前端可直接 `<img :src="...">` 的 Data URI。
 * 后端历史上同一个接口返回过三种格式：
 *   1. `{ captcha: 'ABCD' }`  - 只有文字，需要自己画 SVG
 *   2. `'data:image/...'`     - 完整 Data URI
 *   3. 相对 URL 或完整 URL    - 直接当 src
 * 这里统一封装，避免每个调用方都写一遍兼容逻辑。
 */
export function normalizeCaptchaResponse(response) {
  if (!response || !response.data) {
    return response
  }
  return Object.assign({}, response, {
    data: Object.assign({}, response.data, {
      data: normalizeCaptchaValue(response.data.data)
    })
  })
}

function normalizeCaptchaValue(rawValue) {
  if (typeof rawValue === 'string') {
    const textValue = rawValue.trim()
    if (!textValue) {
      return ''
    }
    if (isCaptchaImageSource(textValue)) {
      return textValue
    }
    return buildCaptchaSvgDataUri(textValue)
  }

  if (!rawValue || typeof rawValue !== 'object') {
    return ''
  }

  const captchaText = typeof rawValue.captcha === 'string' ? rawValue.captcha.trim() : ''
  if (!captchaText) {
    return ''
  }
  return buildCaptchaSvgDataUri(captchaText)
}

function isCaptchaImageSource(value) {
  return value.startsWith('data:image') || value.startsWith('/') || value.startsWith('http://') || value.startsWith('https://')
}

function buildCaptchaSvgDataUri(captchaText) {
  const escapedText = escapeHtml(captchaText)
  const svgMarkup = `<svg xmlns="http://www.w3.org/2000/svg" width="120" height="42" viewBox="0 0 120 42"><rect width="120" height="42" rx="8" fill="#f7faff"/><rect x="1" y="1" width="118" height="40" rx="7" fill="none" stroke="#c7dafc"/><text x="50%" y="50%" dominant-baseline="middle" text-anchor="middle" font-size="24" font-family="monospace" font-weight="700" fill="#1f2937" letter-spacing="4">${escapedText}</text></svg>`
  return `data:image/svg+xml;charset=UTF-8,${encodeURIComponent(svgMarkup)}`
}

function escapeHtml(text) {
  return text.replace(/[&<>"']/g, char => {
    switch (char) {
      case '&': return '&amp;'
      case '<': return '&lt;'
      case '>': return '&gt;'
      case '"': return '&quot;'
      case '\'': return '&#39;'
      default: return char
    }
  })
}
