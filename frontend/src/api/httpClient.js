import axios from 'axios'

let initialized = false

function getCookie(name) {
  var match = document.cookie.match(new RegExp('(^|;\\s*)' + name + '=([^;]*)'))
  return match ? decodeURIComponent(match[2]) : null
}

function initHttpClient() {
  if (initialized) {
    return axios
  }
  axios.defaults.baseURL = '/api'
  axios.defaults.xsrfHeaderName = 'X-CSRFToken'
  axios.defaults.xsrfCookieName = 'csrftoken'
  axios.interceptors.request.use(function (config) {
    var token = getCookie('csrftoken')
    if (token && config.headers) {
      config.headers['X-CSRFToken'] = token
    }
    return config
  })
  axios.interceptors.response.use(function (response) {
    return response
  }, function (error) {
    try {
      const url = error && error.config ? error.config.url : ''
      const isTelemetryEndpoint = typeof url === 'string' &&
        (url.includes('beta/telemetry/events') || url.includes('beta/telemetry/web-vitals'))
      if (!isTelemetryEndpoint) {
        const status = error && error.response ? error.response.status : null
        const message = error && error.response && error.response.data
          ? (error.response.data.error || error.response.data.data || error.message)
          : (error && error.message ? error.message : 'unknown')
        // 动态 import 避免循环引用，遥测客户端可能在 init 之前不可用
        import('@/utils/betaTelemetry').then(mod => {
          if (mod && typeof mod.reportApiError === 'function') {
            mod.reportApiError(url, status, message)
          }
        }).catch(err => { void err })
      }
    } catch (err) {
      void err
    }
    return Promise.reject(error)
  })
  initialized = true
  return axios
}

export function getHttpClient() {
  return initHttpClient()
}
