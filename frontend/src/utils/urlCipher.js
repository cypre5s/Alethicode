export function encodeRouteCtx (obj) {
  return btoa(JSON.stringify(obj))
}

export function decodeRouteCtx (encoded) {
  try {
    return JSON.parse(atob(encoded || ''))
  } catch (_) {
    return {}
  }
}
