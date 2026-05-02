import ojApi from '@/pages/oj/api'
import adminApi from '@/pages/admin/api'

function pick(api, names) {
  return names.reduce((acc, name) => {
    acc[name] = (...args) => api[name](...args)
    return acc
  }, {})
}

export const ojAuthApi = pick(ojApi, [
  'login',
  'checkUsernameOrEmail',
  'register',
  'logout',
  'getCaptcha',
  'getUserInfo',
  'updateProfile',
  'freshDisplayID',
  'twoFactorAuth',
  'tfaRequiredCheck',
  'getSessions',
  'deleteSession',
  'applyResetPassword',
  'resetPassword',
  'changePassword',
  'changeEmail',
  'csrf'
])

export const adminAuthApi = pick(adminApi, [
  'login',
  'csrf',
  'logout',
  'getProfile',
  'getSessions'
])

export default {
  oj: ojAuthApi,
  admin: adminAuthApi
}
