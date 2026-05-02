import { h } from 'vue'

const LEGACY_ICON_CLASS_MAP = {
  'el-icon-search': 'el-icon-search',
  'el-icon-refresh': 'el-icon-refresh',
  'el-icon-delete': 'el-icon-delete',
  'el-icon-plus': 'el-icon-plus',
  'el-icon-edit': 'el-icon-edit',
  'el-icon-setting': 'el-icon-setting',
  'el-icon-caret-bottom': 'el-icon-caret-bottom',
  'el-icon-caret-top': 'el-icon-caret-top',
  'el-icon-arrow-up': 'el-icon-arrow-up',
  'el-icon-arrow-down': 'el-icon-arrow-down',
  'el-icon-document-checked': 'el-icon-document-checked',
  'el-icon-loading': 'el-icon-loading',
  'el-icon-fa-edit': 'el-icon-fa-edit',
  'el-icon-fa-trash': 'el-icon-fa-trash',
  'el-icon-fa-upload': 'el-icon-fa-upload',
  'el-icon-fa-undo': 'el-icon-fa-undo',
  'el-icon-fa-users': 'el-icon-fa-users',
  'el-icon-fa-question-circle': 'el-icon-fa-question-circle',
  'el-icon-fa-cogs': 'el-icon-fa-cogs',
  'el-icon-fa-graduation-cap': 'el-icon-fa-graduation-cap',
  'el-icon-fa-bars': 'el-icon-fa-bars',
  'el-icon-fa-line-chart': 'el-icon-fa-line-chart',
  'el-icon-fa-font': 'el-icon-fa-font'
}

function createLegacyIconComponent(iconClassName) {
  return {
    name: iconClassName,
    render() {
      return h('i', { class: iconClassName })
    }
  }
}

export function installLegacyIconBridge(app) {
  Object.keys(LEGACY_ICON_CLASS_MAP).forEach((legacyIconName) => {
    app.component(legacyIconName, createLegacyIconComponent(LEGACY_ICON_CLASS_MAP[legacyIconName]))
  })
}
