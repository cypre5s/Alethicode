/**
 * 用户账号：登录 / 注册 / 个人资料 / 两步验证 / 会话 / 密码重置。
 */

import { ajax, normalizeCaptchaResponse } from './shared'

export default {
  login(data) {
    return ajax('login', 'post', {
      data
    })
  },
  checkUsernameOrEmail(username, email) {
    return ajax('check-username-or-email', 'post', {
      data: {
        username,
        email
      }
    })
  },
  register(data) {
    return ajax('register', 'post', {
      data
    })
  },
  logout() {
    return ajax('logout', 'get')
  },
  getCaptcha() {
    return ajax('captcha', 'get').then(normalizeCaptchaResponse)
  },
  getUserInfo(username = undefined) {
    return ajax('profile', 'get', {
      params: {
        username
      }
    })
  },
  updateProfile(profile) {
    return ajax('profile', 'put', {
      data: profile
    })
  },
  uploadAvatar(formData) {
    return ajax('upload-avatar', 'post', {
      data: formData,
      silent: true
    })
  },
  freshDisplayID(userID) {
    return ajax('profile/fresh-display-id', 'get', {
      params: {
        user_id: userID
      }
    })
  },
  twoFactorAuth(method, data) {
    return ajax('two-factor-auth', method, {
      data,
      silent: method === 'get'
    })
  },
  tfaRequiredCheck(username) {
    return ajax('tfa-required', 'post', {
      data: {
        username
      }
    })
  },
  getSessions() {
    return ajax('sessions', 'get')
  },
  deleteSession(sessionKey) {
    return ajax('sessions', 'delete', {
      params: {
        session_key: sessionKey
      }
    })
  },
  applyResetPassword(data) {
    return ajax('apply-reset-password', 'post', {
      data
    })
  },
  resetPassword(data) {
    return ajax('reset-password', 'post', {
      data
    })
  },
  changePassword(data) {
    return ajax('change-password', 'post', {
      data
    })
  },
  changeEmail(data) {
    return ajax('change-email', 'post', {
      data
    })
  }
}
