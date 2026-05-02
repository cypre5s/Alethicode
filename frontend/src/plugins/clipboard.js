function ensureClipboardState(el) {
  if (!el.__clipboardState__) {
    el.__clipboardState__ = {
      copyValue: '',
      successHandler: null,
      errorHandler: null,
      cleanup: null
    }
  }
  return el.__clipboardState__
}

function normalizeCopyValue(value) {
  if (value === null || typeof value === 'undefined') {
    return ''
  }
  return String(value)
}

function normalizeCallback(value) {
  return typeof value === 'function' ? value : null
}

async function writeClipboardText(text) {
  if (typeof navigator !== 'undefined' && navigator.clipboard && typeof navigator.clipboard.writeText === 'function') {
    await navigator.clipboard.writeText(text)
    return
  }

  if (typeof document === 'undefined') {
    throw new Error('Clipboard API is not available')
  }

  const textarea = document.createElement('textarea')
  textarea.value = text
  textarea.setAttribute('readonly', 'readonly')
  textarea.style.position = 'fixed'
  textarea.style.top = '-9999px'
  textarea.style.left = '-9999px'
  document.body.appendChild(textarea)
  textarea.select()

  const copied = document.execCommand('copy')
  document.body.removeChild(textarea)
  if (!copied) {
    throw new Error('document.execCommand(copy) failed')
  }
}

function updateClipboardState(el, binding) {
  const state = ensureClipboardState(el)
  const arg = binding && binding.arg

  if (arg === 'copy') {
    state.copyValue = normalizeCopyValue(binding.value)
  } else if (arg === 'success') {
    state.successHandler = normalizeCallback(binding.value)
  } else if (arg === 'error') {
    state.errorHandler = normalizeCallback(binding.value)
  }

  if (!state.cleanup) {
    const onClick = async (event) => {
      try {
        await writeClipboardText(state.copyValue)
        if (state.successHandler) {
          state.successHandler(event)
        }
      } catch (error) {
        if (state.errorHandler) {
          state.errorHandler(error)
          return
        }
        throw error
      }
    }

    el.addEventListener('click', onClick)
    state.cleanup = () => {
      el.removeEventListener('click', onClick)
    }
  }
}

export default {
  install(app) {
    app.directive('clipboard', {
      mounted(el, binding) {
        updateClipboardState(el, binding)
      },
      updated(el, binding) {
        updateClipboardState(el, binding)
      },
      unmounted(el) {
        if (el.__clipboardState__ && el.__clipboardState__.cleanup) {
          el.__clipboardState__.cleanup()
        }
        delete el.__clipboardState__
      }
    })
  }
}
