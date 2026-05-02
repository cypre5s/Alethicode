function createAnalyticsClient(measurementId) {
  return {
    page(payload = {}) {
      if (typeof window === 'undefined' || !measurementId) {
        return
      }

      const location = window.location
      const params = new URLSearchParams({
        v: '1',
        tid: measurementId,
        cid: 'frontend-new',
        t: 'pageview',
        dp: payload.page || `${location.pathname}${location.search}`,
        dt: payload.title || document.title || ''
      })

      const beaconUrl = `https://www.google-analytics.com/collect?${params.toString()}`
      if (typeof navigator !== 'undefined' && typeof navigator.sendBeacon === 'function') {
        navigator.sendBeacon(beaconUrl)
        return
      }

      const image = new Image()
      image.src = beaconUrl
    },
    event() {}
  }
}

function schedulePageview(send) {
  if (typeof window !== 'undefined' && typeof window.requestAnimationFrame === 'function') {
    window.requestAnimationFrame(() => {
      setTimeout(send, 0)
    })
    return
  }
  setTimeout(send, 0)
}

export default {
  install(app, options = {}) {
    const analytics = createAnalyticsClient(options.id)
    app.config.globalProperties.$ga = analytics

    if (options.router && typeof options.router.afterEach === 'function') {
      options.router.afterEach((to) => {
        const reportPage = () => {
          analytics.page({
            page: to && to.fullPath ? to.fullPath : undefined,
            title: typeof document !== 'undefined' ? document.title : ''
          })
        }
        if (options.deferPageview) {
          schedulePageview(reportPage)
          return
        }
        reportPage()
      })
    }
  }
}
