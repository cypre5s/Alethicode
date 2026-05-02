import { ref, getCurrentInstance } from 'vue'
import api from '@oj/api'

export function useForm () {
  const instance = getCurrentInstance()
  const vm = () => instance.proxy

  const captchaSrc = ref('')

  function validateForm (formName) {
    return new Promise((resolve, reject) => {
      vm().$refs[formName].validate(valid => {
        if (!valid) {
          vm().$error('please validate the error fields')
        } else {
          resolve(valid)
        }
      })
    })
  }

  function getCaptchaSrc () {
    api.getCaptcha().then(res => {
      captchaSrc.value = res.data.data
    })
  }

  return { captchaSrc, validateForm, getCaptchaSrc }
}
