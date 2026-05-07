import storage from '@/utils/storage'
import { STORAGE_KEY } from '@/utils/constants'
import ojAPI from '@oj/api'
import { getHttpClient } from '@/api/httpClient'
import { notify } from '@/utils/notifications'

const httpClient = getHttpClient()

function submissionMemoryFormat(memory) {
  if (memory === undefined) return '--'
  // Judge Server 返回字节数，前端展示为 MB。
  let t = parseInt(memory) / 1048576
  return String(t.toFixed(0)) + 'MB'
}

function submissionTimeFormat(time) {
  if (time === undefined) return '--'
  return time + 'ms'
}

function getACRate(acCount, totalCount) {
  const accepted = Number(acCount)
  const total = Number(totalCount)
  if (total === 0 || accepted === 0) {
    return '0%'
  }
  let rate = (accepted / total * 100).toFixed(2)
  return String(rate) + '%'
}

function filterEmptyValue(object) {
  let query = {}
  Object.keys(object).forEach(key => {
    if (object[key] || object[key] === 0 || object[key] === false) {
      query[key] = object[key]
    }
  })
  return query
}

/**
 * 按字符宽度近似换行；中文字符按半数阈值处理。
 */
function breakLongWords(value, length = 16) {
  let re
  if (escape(value).indexOf('%u') === -1) {
    // 匹配固定长度的连续非中文字符片段。
    re = new RegExp('(.{' + length + '})', 'g')
  } else {
    // 匹配固定长度的连续中文混排片段。
    re = new RegExp('(.{' + (length / 2 + 1) + '})', 'g')
  }
  return value.replace(re, '$1\n')
}

function downloadFile(url) {
  return new Promise((resolve, reject) => {
    httpClient.get(url, { responseType: 'blob' }).then(resp => {
      let headers = resp.headers
      if (headers['content-type'].indexOf('json') !== -1) {
        let fr = new window.FileReader()
        if (resp.data.error) {
          notify.error(resp.data.error)
        } else {
          notify.error('Invalid file format')
        }
        fr.onload = (event) => {
          let data = JSON.parse(event.target.result)
          if (data.error) {
            notify.error(data.data)
          } else {
            notify.error('Invalid file format')
          }
        }
        let b = new window.Blob([resp.data], { type: 'application/json' })
        fr.readAsText(b)
        return
      }
      let link = document.createElement('a')
      link.href = window.URL.createObjectURL(new window.Blob([resp.data], { type: headers['content-type'] }))
      link.download = (headers['content-disposition'] || '').split('filename=')[1]
      document.body.appendChild(link)
      link.click()
      link.remove()
      resolve()
    }).catch(() => { })
  })
}

function getLanguages() {
  return new Promise((resolve, reject) => {
    let languages = storage.get(STORAGE_KEY.languages)
    if (languages) {
      resolve(languages)
    }
    ojAPI.getLanguages().then(res => {
      let languages = res.data.data.languages
      storage.set(STORAGE_KEY.languages, languages)
      resolve(languages)
    }, err => {
      reject(err)
    })
  })
}

export default {
  submissionMemoryFormat: submissionMemoryFormat,
  submissionTimeFormat: submissionTimeFormat,
  getACRate: getACRate,
  filterEmptyValue: filterEmptyValue,
  breakLongWords: breakLongWords,
  downloadFile: downloadFile,
  getLanguages: getLanguages
}
