let _impl = {
  error: console.error,
  warning: console.warn,
  success: () => {},
  info: () => {},
  loadingStart: () => {},
  loadingFinish: () => {}
}

export function initNotifications (impl) {
  Object.assign(_impl, impl)
}

export const notify = {
  error: (msg) => _impl.error(msg),
  warning: (msg) => _impl.warning(msg),
  success: (msg) => _impl.success(msg),
  info: (msg) => _impl.info(msg),
  loadingStart: () => _impl.loadingStart(),
  loadingFinish: () => _impl.loadingFinish()
}
