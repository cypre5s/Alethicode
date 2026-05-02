const RESIZE_OBSERVER_NOISE_MESSAGES = [
  'ResizeObserver loop limit exceeded',
  'ResizeObserver loop completed with undelivered notifications.',
  "Cannot read properties of undefined (reading 'map')"
]

export function shouldReportRuntimeError(error) {
  const message = typeof error === 'string'
    ? error
    : (error && typeof error.message === 'string' ? error.message : '')
  return !RESIZE_OBSERVER_NOISE_MESSAGES.some((noise) => message.includes(noise))
}

export function installDevRuntimeErrorFilter() {
  if (typeof window === 'undefined') {
    return
  }

  window.addEventListener('error', (event) => {
    const error = event.error || event.message
    if (shouldReportRuntimeError(error)) {
      return
    }
    event.preventDefault()
    if (typeof event.stopImmediatePropagation === 'function') {
      event.stopImmediatePropagation()
    }
  }, true)

  window.addEventListener('unhandledrejection', (event) => {
    if (shouldReportRuntimeError(event.reason)) {
      return
    }
    event.preventDefault()
  })
}
