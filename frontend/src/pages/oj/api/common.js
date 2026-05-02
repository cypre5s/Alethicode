/**
 * 通用接口：站点配置 / CSRF / 公告 / 语言列表。
 * 这些接口不属于任何具体业务域，但各子模块都可能需要。
 */

import { ajax } from './shared'

export default {
  getWebsiteConf(params) {
    return ajax('website', 'get', {
      params
    })
  },
  csrf() {
    return ajax('csrf', 'get', { silent: true })
  },
  getAnnouncementList(offset, limit) {
    let params = {
      offset: offset,
      limit: limit
    }
    return ajax('announcements', 'get', {
      params
    })
  },
  getLanguages() {
    return ajax('languages', 'get')
  }
}
