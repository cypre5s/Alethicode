export function showSettingsToast(message, type) {
  const toastContainer = document.getElementById('settings-toasts')
  if (!toastContainer) {
    return
  }
  const toast = document.createElement('div')
  toast.className = 'settings-toast'
  const dot = document.createElement('div')
  dot.className = 'settings-toast-dot' + (type === 'warn' ? ' warn' : '')
  toast.appendChild(dot)
  toast.appendChild(document.createTextNode(message))
  toastContainer.appendChild(toast)
  setTimeout(() => {
    toast.classList.add('out')
    toast.addEventListener('animationend', () => toast.remove())
  }, 2400)
}
