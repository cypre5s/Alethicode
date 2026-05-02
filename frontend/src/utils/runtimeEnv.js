export function resolveFrontendEnv(env) {
  const runtimeEnv = env || {}
  const appVersion = typeof runtimeEnv.VITE_APP_VERSION === 'string' && runtimeEnv.VITE_APP_VERSION.trim()
    ? runtimeEnv.VITE_APP_VERSION.trim()
    : 'dev'

  return {
    appVersion,
    isDevelopment: Boolean(runtimeEnv.DEV),
    isProduction: Boolean(runtimeEnv.PROD)
  }
}

export const FRONTEND_ENV = resolveFrontendEnv({
  DEV: typeof __APP_DEV__ !== 'undefined' ? __APP_DEV__ : false,
  PROD: typeof __APP_PROD__ !== 'undefined' ? __APP_PROD__ : false,
  VITE_APP_VERSION: typeof __APP_VERSION__ !== 'undefined' ? __APP_VERSION__ : 'dev'
})
