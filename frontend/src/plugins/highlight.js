import hljs from '@/utils/hljs'

export default {
  install(app) {
    app.directive('highlight', {
      deep: true,
      beforeMount: function (el, binding) {
        Array.from(el.querySelectorAll('code')).forEach((target) => {
          if (binding.value) {
            target.textContent = binding.value
          }
          try { hljs.highlightElement(target) } catch (e) { console.warn('[highlight] beforeMount failed:', e) }
        })
      },
      updated: function (el, binding) {
        Array.from(el.querySelectorAll('code')).forEach((target) => {
          if (binding.value) {
            target.textContent = binding.value
          }
          try { hljs.highlightElement(target) } catch (e) { console.warn('[highlight] updated failed:', e) }
        })
      }
    })
  }
}
