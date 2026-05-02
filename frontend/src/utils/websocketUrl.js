export function buildWebSocketUrl(pathname) {
  const normalizedPath = String(pathname || '').startsWith('/') ? String(pathname || '') : `/${String(pathname || '')}`
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${window.location.host}${normalizedPath}`
}

export function buildClassroomCollabWebSocketPath(sessionId) {
  return `/ws/classroom/collab/${sessionId}`
}

export function buildQaWebSocketPath(sessionId) {
  return `/ws/qa/${sessionId}`
}
