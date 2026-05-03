import * as Sentry from '@sentry/vue'
import { FRONTEND_ENV } from './runtimeEnv'

const DEFAULT_SAMPLE_RATE = 0.0
const DEFAULT_TRACES_SAMPLE_RATE = 0.0
const DEFAULT_REPLAY_SAMPLE_RATE = 0.0
const DEFAULT_REPLAY_ON_ERROR_SAMPLE_RATE = 0.0

function readDsn () {
  if (typeof window !== 'undefined' && window.__APP_RUNTIME__ && window.__APP_RUNTIME__.SENTRY_DSN) {
    return String(window.__APP_RUNTIME__.SENTRY_DSN || '').trim()
  }
  if (typeof import.meta !== 'undefined' && import.meta.env && import.meta.env.VITE_SENTRY_DSN) {
    return String(import.meta.env.VITE_SENTRY_DSN || '').trim()
  }
  return ''
}

function readEnvironment () {
  if (FRONTEND_ENV.isProduction) return 'prod'
  if (FRONTEND_ENV.isDevelopment) return 'dev'
  return 'unknown'
}

function readNumber (key, fallback) {
  if (typeof import.meta === 'undefined' || !import.meta.env) return fallback
  const raw = import.meta.env[key]
  if (raw == null || raw === '') return fallback
  const value = Number(raw)
  return Number.isFinite(value) ? value : fallback
}

export function initSentry ({ app, router }) {
  const dsn = readDsn()
  if (!dsn) {
    return
  }

  Sentry.init({
    app,
    dsn,
    environment: readEnvironment(),
    release: typeof __APP_VERSION__ !== 'undefined' ? __APP_VERSION__ : 'dev',
    sampleRate: readNumber('VITE_SENTRY_SAMPLE_RATE', DEFAULT_SAMPLE_RATE),
    tracesSampleRate: readNumber('VITE_SENTRY_TRACES_SAMPLE_RATE', DEFAULT_TRACES_SAMPLE_RATE),
    replaysSessionSampleRate: readNumber('VITE_SENTRY_REPLAYS_SAMPLE_RATE', DEFAULT_REPLAY_SAMPLE_RATE),
    replaysOnErrorSampleRate: readNumber('VITE_SENTRY_REPLAYS_ON_ERROR_SAMPLE_RATE', DEFAULT_REPLAY_ON_ERROR_SAMPLE_RATE),
    integrations: [
      Sentry.browserTracingIntegration({ router })
    ],
    sendDefaultPii: false,
    beforeSend (event) {
      if (event && event.request) {
        delete event.request.cookies
      }
      if (event && event.extra) {
        for (const key of Object.keys(event.extra)) {
          if (/password|token|secret|authorization/i.test(key)) {
            event.extra[key] = '[redacted]'
          }
        }
      }
      return event
    }
  })
}
