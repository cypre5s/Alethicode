/**
 * useChatComposer
 *
 * 通用聊天输入框组合式 hook，封装两个对话页（AI 导学助手、课件问答）共用的输入区状态机：
 *  - `@` 引用菜单：键盘 上下/Enter 选中，分组渲染，懒加载 provider
 *  - `/` 斜杠命令菜单：与 `@` 互斥
 *  - 上下方向键的历史输入召回：textarea 为空时按上键进入历史
 *  - 草稿暂存：scope 切换 / 输入变化 debounce 500ms 写 localStorage
 *
 * 设计原则
 * - 与 backend ReferenceResolver 完全解耦：本 hook 只负责输入框交互与 token 字符串生成
 * - failfast：参数缺失（无 atProviders / 无 slashCommands 数组）直接抛错，不静默兜底
 * - 不直接操作 DOM；只在需要时通过 textareaRef.value.selectionStart 读取
 */

import { ref, computed, watch, onUnmounted, isRef } from 'vue'
import { readDraft, writeDraft, readHistory, pushHistory } from './composerStorage'

const AT_TRIGGER_RE = /(?:^|\s)@([^\s]*)$/
const SLASH_TRIGGER_RE = /^\/(\S*)(?:\s+([\s\S]*))?$/

function ensureArray(value, label) {
  if (!Array.isArray(value)) {
    throw new TypeError('useChatComposer: `' + label + '` must be an Array, got ' + (typeof value))
  }
  return value
}

function unwrap(value) {
  return isRef(value) ? value.value : value
}

function debounce(fn, wait) {
  let timer = null
  const debounced = function () {
    const args = Array.prototype.slice.call(arguments)
    if (timer) clearTimeout(timer)
    timer = setTimeout(function () {
      timer = null
      fn.apply(null, args)
    }, wait)
  }
  debounced.cancel = function () {
    if (timer) {
      clearTimeout(timer)
      timer = null
    }
  }
  debounced.flush = function () {
    const args = Array.prototype.slice.call(arguments)
    if (timer) {
      clearTimeout(timer)
      timer = null
    }
    fn.apply(null, args)
  }
  return debounced
}

export function useChatComposer(options) {
  if (!options || typeof options !== 'object') {
    throw new TypeError('useChatComposer: options object is required')
  }
  ensureArray(options.atProviders, 'atProviders')
  ensureArray(options.slashCommands, 'slashCommands')

  const atProviders = options.atProviders
  const slashCommands = options.slashCommands
  const onSubmit = typeof options.onSubmit === 'function' ? options.onSubmit : null
  const isInputBlockedSource = options.isInputBlocked

  const rawText = ref('')
  const atMenuVisible = ref(false)
  const atQuery = ref('')
  const atActiveIndex = ref(0)
  const slashMenuVisible = ref(false)
  const slashQuery = ref('')
  const slashActiveIndex = ref(0)
  const historyCursor = ref(-1)
  const lazyLoadedKeys = new Set()
  const providerCache = ref({})

  const scopeKeyRef = isRef(options.scopeKey) ? options.scopeKey : ref(options.scopeKey || 'default')
  const currentScope = computed(function () { return String(scopeKeyRef.value || 'default') })
  let activeScope = currentScope.value
  rawText.value = readDraft(activeScope)

  const persistDraft = debounce(function (scope, value) { writeDraft(scope, value) }, 500)

  function isInputBlocked() {
    return Boolean(unwrap(isInputBlockedSource))
  }

  function getProviderItems(provider) {
    if (!provider || typeof provider !== 'object') return []
    if (typeof provider.items !== 'function') {
      return Array.isArray(provider.items) ? provider.items : []
    }
    if (provider.lazyLoad && !lazyLoadedKeys.has(provider.key)) {
      try {
        const result = provider.items()
        if (result && typeof result.then === 'function') {
          result.then(function (items) {
            providerCache.value = Object.assign({}, providerCache.value, { [provider.key]: Array.isArray(items) ? items : [] })
            lazyLoadedKeys.add(provider.key)
          }).catch(function (err) {
            console.warn('[useChatComposer] lazy provider failed:', provider.key, err && err.message)
            providerCache.value = Object.assign({}, providerCache.value, { [provider.key]: [] })
            lazyLoadedKeys.add(provider.key)
          })
          return providerCache.value[provider.key] || []
        }
        providerCache.value = Object.assign({}, providerCache.value, { [provider.key]: Array.isArray(result) ? result : [] })
        lazyLoadedKeys.add(provider.key)
        return providerCache.value[provider.key]
      } catch (err) {
        console.warn('[useChatComposer] provider items() failed:', provider.key, err && err.message)
        providerCache.value = Object.assign({}, providerCache.value, { [provider.key]: [] })
        return []
      }
    }
    if (providerCache.value[provider.key]) return providerCache.value[provider.key]
    try {
      const items = provider.items()
      return Array.isArray(items) ? items : []
    } catch (err) {
      console.warn('[useChatComposer] provider items() failed:', provider.key, err && err.message)
      return []
    }
  }

  const atGroups = computed(function () {
    var query = String(atQuery.value || '').toLowerCase()
    return atProviders
      .map(function (provider) {
        var allItems = getProviderItems(provider)
        var filtered = allItems.filter(function (item) {
          if (!item || !item.token) return false
          if (!query) return true
          var haystack = ((item.token || '') + ' ' + (item.label || '') + ' ' + (item.desc || '')).toLowerCase()
          return haystack.indexOf(query) !== -1
        })
        var maxDisplay = provider.maxInitialDisplay
        if (!query && typeof maxDisplay === 'number' && maxDisplay > 0 && filtered.length > maxDisplay) {
          filtered = filtered.slice(0, maxDisplay)
        }
        return {
          group: provider.group || provider.label || provider.key,
          key: provider.key,
          items: filtered
        }
      })
      .filter(function (group) { return group.items.length > 0 })
  })

  const flatAtItems = computed(function () {
    return atGroups.value.reduce(function (acc, group) { return acc.concat(group.items) }, [])
  })

  const slashGroups = computed(function () {
    const query = String(slashQuery.value || '').toLowerCase()
    const buckets = new Map()
    slashCommands.forEach(function (cmd) {
      if (!cmd || !cmd.command) return
      const command = String(cmd.command || '')
      const label = String(cmd.label || '')
      const hint = String(cmd.hint || '')
      if (query) {
        const haystack = (command + ' ' + label + ' ' + hint).toLowerCase()
        if (haystack.indexOf(query) === -1) return
      }
      const groupName = cmd.group || '其他'
      if (!buckets.has(groupName)) buckets.set(groupName, [])
      buckets.get(groupName).push(cmd)
    })
    const result = []
    buckets.forEach(function (items, group) { result.push({ group: group, items: items }) })
    return result
  })

  const flatSlashItems = computed(function () {
    return slashGroups.value.reduce(function (acc, group) { return acc.concat(group.items) }, [])
  })

  function updateMenusFromText(text) {
    if (typeof text !== 'string') {
      atMenuVisible.value = false
      slashMenuVisible.value = false
      return
    }
    const slashMatch = text.match(SLASH_TRIGGER_RE)
    if (slashMatch) {
      slashMenuVisible.value = true
      slashQuery.value = slashMatch[1] || ''
      atMenuVisible.value = false
      atQuery.value = ''
      slashActiveIndex.value = 0
      return
    }
    slashMenuVisible.value = false
    slashQuery.value = ''
    const atMatch = text.match(AT_TRIGGER_RE)
    if (atMatch) {
      atMenuVisible.value = true
      atQuery.value = atMatch[1] || ''
      atActiveIndex.value = 0
      return
    }
    atMenuVisible.value = false
    atQuery.value = ''
  }

  function onInput(value) {
    const text = typeof value === 'string'
      ? value
      : (value && value.target ? value.target.value : '')
    rawText.value = text
    updateMenusFromText(text)
    persistDraft(activeScope, text)
    historyCursor.value = -1
  }

  function selectAtItem(item) {
    if (!item || !item.token) return
    const token = String(item.token)
    const raw = rawText.value || ''
    const replaced = raw.replace(/(^|\s)@([\w:.-]*)$/, function (match, prefix) {
      return prefix + token + ' '
    })
    const trailing = raw.trim() ? ' ' : ''
    const next = replaced === raw
      ? (raw.replace(/\s+$/, '') + trailing + token + ' ')
      : replaced
    rawText.value = next
    persistDraft(activeScope, next)
    atMenuVisible.value = false
    atQuery.value = ''
  }

  function selectSlashItem(item) {
    if (!item || !item.command) return
    if (item.status === 'placeholder') {
      if (typeof item.onPlaceholder === 'function') {
        try { item.onPlaceholder(item) } catch (err) {
          console.warn('[useChatComposer] placeholder handler failed:', item.command, err && err.message)
        }
      }
      slashMenuVisible.value = false
      slashQuery.value = ''
      return
    }
    const raw = String(rawText.value || '')
    const command = String(item.command || '')
    const args = raw === command || raw.indexOf(command + ' ') === 0
      ? raw.slice(command.length).trim()
      : raw.replace(SLASH_TRIGGER_RE, '').trim()
    if (typeof item.run === 'function') {
      try {
        item.run({
          args: args,
          replace: function (text) {
            rawText.value = text || ''
            persistDraft(activeScope, rawText.value)
          }
        })
      } catch (err) {
        console.warn('[useChatComposer] slash run failed:', item.command, err && err.message)
      }
    }
    rawText.value = ''
    persistDraft(activeScope, '')
    slashMenuVisible.value = false
    slashQuery.value = ''
  }

  function moveActive(direction) {
    if (atMenuVisible.value) {
      const total = flatAtItems.value.length
      if (!total) return
      atActiveIndex.value = (atActiveIndex.value + direction + total) % total
      return
    }
    if (slashMenuVisible.value) {
      const total = flatSlashItems.value.length
      if (!total) return
      slashActiveIndex.value = (slashActiveIndex.value + direction + total) % total
    }
  }

  function commitActiveMenu() {
    if (atMenuVisible.value) {
      const item = flatAtItems.value[atActiveIndex.value]
      if (item) selectAtItem(item)
      return true
    }
    if (slashMenuVisible.value) {
      const item = flatSlashItems.value[slashActiveIndex.value]
      if (item) selectSlashItem(item)
      return true
    }
    return false
  }

  function recallHistory(direction) {
    const history = readHistory(activeScope)
    if (!history.length) return
    const trimmed = String(rawText.value || '').trim()
    if (direction < 0) {
      if (trimmed && historyCursor.value === -1) return
      const next = historyCursor.value === -1 ? history.length - 1 : Math.max(0, historyCursor.value - 1)
      historyCursor.value = next
      rawText.value = history[next]
      persistDraft(activeScope, rawText.value)
    } else {
      if (historyCursor.value === -1) return
      const next = historyCursor.value + 1
      if (next >= history.length) {
        historyCursor.value = -1
        rawText.value = ''
        persistDraft(activeScope, '')
      } else {
        historyCursor.value = next
        rawText.value = history[next]
        persistDraft(activeScope, rawText.value)
      }
    }
  }

  function onKeydown(event) {
    if (!event) return
    if (isInputBlocked()) return
    const key = event.key
    const menuOpen = atMenuVisible.value || slashMenuVisible.value
    if (menuOpen) {
      if (key === 'ArrowDown') { event.preventDefault(); moveActive(1); return }
      if (key === 'ArrowUp') { event.preventDefault(); moveActive(-1); return }
      if (key === 'Enter' && !event.shiftKey) {
        if (commitActiveMenu()) { event.preventDefault(); return }
      }
      if (key === 'Escape') {
        event.preventDefault()
        atMenuVisible.value = false
        slashMenuVisible.value = false
        return
      }
    }
    if (!menuOpen && key === 'ArrowUp' && !event.shiftKey) {
      const trimmed = String(rawText.value || '').trim()
      if (!trimmed || historyCursor.value !== -1) {
        event.preventDefault()
        recallHistory(-1)
        return
      }
    }
    if (!menuOpen && key === 'ArrowDown' && !event.shiftKey && historyCursor.value !== -1) {
      event.preventDefault()
      recallHistory(1)
      return
    }
    if (key === 'Enter' && !event.shiftKey && !menuOpen) {
      event.preventDefault()
      submit()
    }
  }

  function submit() {
    if (isInputBlocked()) return
    const text = String(rawText.value || '').trim()
    if (!text) return
    pushHistory(activeScope, text)
    historyCursor.value = -1
    if (onSubmit) {
      try { onSubmit(text) } catch (err) {
        console.warn('[useChatComposer] onSubmit threw:', err && err.message)
      }
    }
    rawText.value = ''
    persistDraft.flush(activeScope, '')
    atMenuVisible.value = false
    slashMenuVisible.value = false
  }

  function setText(value) {
    rawText.value = String(value == null ? '' : value)
    updateMenusFromText(rawText.value)
    persistDraft(activeScope, rawText.value)
  }

  function clear() {
    rawText.value = ''
    atMenuVisible.value = false
    slashMenuVisible.value = false
    persistDraft.flush(activeScope, '')
  }

  function refreshProvider(key) {
    lazyLoadedKeys.delete(key)
    if (providerCache.value[key]) {
      const next = Object.assign({}, providerCache.value)
      delete next[key]
      providerCache.value = next
    }
  }

  watch(currentScope, function (next, prev) {
    if (next === prev) return
    persistDraft.flush(prev, rawText.value)
    activeScope = next
    rawText.value = readDraft(next)
    atMenuVisible.value = false
    slashMenuVisible.value = false
    historyCursor.value = -1
  })

  onUnmounted(function () { persistDraft.flush(activeScope, rawText.value) })

  return {
    rawText: rawText,
    atMenuVisible: atMenuVisible,
    atQuery: atQuery,
    atGroups: atGroups,
    atActiveIndex: atActiveIndex,
    slashMenuVisible: slashMenuVisible,
    slashQuery: slashQuery,
    slashGroups: slashGroups,
    slashActiveIndex: slashActiveIndex,
    historyCursor: historyCursor,
    handlers: {
      onInput: onInput,
      onKeydown: onKeydown,
      selectAtItem: selectAtItem,
      selectSlashItem: selectSlashItem,
      submit: submit,
      clear: clear,
      setText: setText,
      refreshProvider: refreshProvider
    }
  }
}

export const __INTERNAL__ = {
  AT_TRIGGER_RE: AT_TRIGGER_RE,
  SLASH_TRIGGER_RE: SLASH_TRIGGER_RE
}

export default useChatComposer
