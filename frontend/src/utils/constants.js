export const JUDGE_STATUS = {
  '-2': {
    name: 'Compile Error',
    short: 'CE',
    color: '#8c8c8c',
    type: 'warning'
  },
  '-1': {
    name: 'Wrong Answer',
    short: 'WA',
    color: '#f5222d',
    type: 'error'
  },
  '0': {
    name: 'Accepted',
    short: 'AC',
    color: '#52c41a',
    type: 'success'
  },
  '1': {
    name: 'Time Limit Exceeded',
    short: 'TLE',
    color: '#fa8c16',
    type: 'error'
  },
  '2': {
    name: 'Time Limit Exceeded',
    short: 'TLE',
    color: '#fa8c16',
    type: 'error'
  },
  '3': {
    name: 'Memory Limit Exceeded',
    short: 'MLE',
    color: '#fa8c16',
    type: 'error'
  },
  '4': {
    name: 'Runtime Error',
    short: 'RE',
    color: '#722ed1',
    type: 'error'
  },
  '5': {
    name: 'System Error',
    short: 'SE',
    color: '#f5222d',
    type: 'error'
  },
  '6': {
    name: 'Pending',
    color: '#1890ff',
    type: 'warning'
  },
  '7': {
    name: 'Judging',
    color: '#1890ff',
    type: 'info'
  },
  '8': {
    name: 'Partial Accepted',
    short: 'PAC',
    color: '#1890ff',
    type: 'info'
  },
  '9': {
    name: 'Submitting',
    color: '#1890ff',
    type: 'warning'
  }
}

export const USER_TYPE = {
  REGULAR_USER: 'Regular User',
  TEACHER: 'Teacher',
  ADMIN: 'Admin'
}

export const PROBLEM_PERMISSION = {
  NONE: 'None',
  OWN: 'Own',
  ALL: 'All'
}

export const STORAGE_KEY = {
  AUTHED: 'authed',
  PROBLEM_CODE: 'problemCode',
  PROBLEM_ERRORS: 'problemErrors',
  languages: 'languages'
}

export function buildProblemCodeKey (problemID) {
  return `${STORAGE_KEY.PROBLEM_CODE}_NaN_${problemID}`
}

export function buildProblemErrorKey (problemID) {
  return `${STORAGE_KEY.PROBLEM_ERRORS}_NaN_${problemID}`
}

export const GOOGLE_ANALYTICS_ID = 'UA-111499601-1'
