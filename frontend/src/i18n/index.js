import { createI18n } from 'vue-i18n'
import elenUS from 'element-plus/es/locale/lang/en'
import elzhCN from 'element-plus/es/locale/lang/zh-cn'
import elzhTW from 'element-plus/es/locale/lang/zh-tw'
import { m as ojEnUS } from './oj/en-US'
import { m as ojZhCN } from './oj/zh-CN'
import { m as ojZhTW } from './oj/zh-TW'
import { m as adminEnUS } from './admin/en-US'
import { m as adminZhCN } from './admin/zh-CN'
import { m as adminZhTW } from './admin/zh-TW'

const languages = [
  { value: 'en-US', label: 'English', el: elenUS },
  { value: 'zh-CN', label: '简体中文', el: elzhCN },
  { value: 'zh-TW', label: '繁體中文', el: elzhTW }
]
const localeBundles = {
  'en-US': { oj: ojEnUS, admin: adminEnUS },
  'zh-CN': { oj: ojZhCN, admin: adminZhCN },
  'zh-TW': { oj: ojZhTW, admin: adminZhTW }
}

const messages = languages.reduce((result, lang) => {
  const locale = lang.value
  const localeBundle = localeBundles[locale]
  result[locale] = {
    m: Object.assign({}, localeBundle.oj, localeBundle.admin),
    ...(lang.el || {})
  }
  return result
}, {})
const i18n = createI18n({
  legacy: false,
  globalInjection: true,
  locale: 'zh-CN',
  messages
})

export default i18n
export { languages }
