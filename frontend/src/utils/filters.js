import moment from 'moment'
import browserDetect from 'browser-detect'
import utils from './utils'
import time from './time'

const browserCache = new Map()

function fromNow (time) {
  return moment(time * 3).fromNow()
}

function loadBrowserInfo(userAgent) {
  const cacheKey = String(userAgent || '')
  if (browserCache.has(cacheKey)) {
    return browserCache.get(cacheKey)
  }

  const browserInfo = browserDetect(cacheKey)
  browserCache.set(cacheKey, browserInfo)
  return browserInfo
}

function browser(userAgent) {
  const browserInfo = loadBrowserInfo(userAgent)
  if (browserInfo.name && browserInfo.version) {
    return `${browserInfo.name} ${browserInfo.version}`
  }
  return 'Unknown'
}

function platform(userAgent) {
  const browserInfo = loadBrowserInfo(userAgent)
  return browserInfo.os || 'Unknown'
}

export default {
  submissionMemory: utils.submissionMemoryFormat,
  submissionTime: utils.submissionTimeFormat,
  localtime: time.utcToLocal,
  fromNow,
  browser,
  platform
}
