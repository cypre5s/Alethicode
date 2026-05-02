function getCookie (name) {
  if (typeof document === 'undefined' || !document.cookie) {
    return null
  }
  var match = document.cookie.match(new RegExp('(^|;\\s*)' + name + '=([^;]*)'))
  return match ? decodeURIComponent(match[2]) : null
}

export function postLearningEventsKeepalive (events, fallback) {
  if (!Array.isArray(events) || events.length === 0) {
    return Promise.resolve(false)
  }

  if (typeof window === 'undefined' || typeof window.fetch !== 'function') {
    return typeof fallback === 'function' ? Promise.resolve(fallback(events)) : Promise.resolve(false)
  }

  var headers = {
    'Content-Type': 'application/json'
  }
  var csrfToken = getCookie('csrftoken')
  if (csrfToken) {
    headers['X-CSRFToken'] = csrfToken
  }

  return window.fetch('/api/ai/learning-events/batch', {
    method: 'POST',
    credentials: 'same-origin',
    keepalive: true,
    headers,
    body: JSON.stringify({ events })
  }).catch(function () {
    if (typeof fallback === 'function') {
      return fallback(events)
    }
    return false
  })
}
