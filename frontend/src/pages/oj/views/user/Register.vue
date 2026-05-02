<template>
<div>
    <ElForm ref="formRegister" :model="formRegister" :rules="ruleRegister">
      <ElFormItem prop="username">
        <ElInput type="text" v-model="formRegister.username" :placeholder="$t('m.RegisterUsername')" size="large" @keyup.enter="handleRegister">
          <template #prepend><ElIcon><User /></ElIcon></template>
        </ElInput>
      </ElFormItem>
      <ElFormItem prop="email">
        <ElInput v-model="formRegister.email" :placeholder="$t('m.Email_Address')" size="large" @keyup.enter="handleRegister">
          <template #prepend><ElIcon><Message /></ElIcon></template>
        </ElInput>
      </ElFormItem>
      <ElFormItem prop="password">
        <ElInput type="password" v-model="formRegister.password" :placeholder="$t('m.RegisterPassword')" size="large" @keyup.enter="handleRegister">
          <template #prepend><ElIcon><Lock /></ElIcon></template>
        </ElInput>
      </ElFormItem>
      <ElFormItem prop="passwordAgain">
        <ElInput type="password" v-model="formRegister.passwordAgain" :placeholder="$t('m.Password_Again')" size="large" @keyup.enter="handleRegister">
          <template #prepend><ElIcon><Lock /></ElIcon></template>
        </ElInput>
      </ElFormItem>
      <ElFormItem prop="captcha" style="margin-bottom:10px">
        <div class="oj-captcha">
          <div class="oj-captcha-code">
            <ElInput v-model="formRegister.captcha" :placeholder="$t('m.Captcha')" size="large" @keyup.enter="handleRegister">
              <template #prepend><ElIcon><Sunny /></ElIcon></template>
            </ElInput>
          </div>
          <div class="oj-captcha-img">
            <ElTooltip content="Click to refresh" placement="top">
              <img :src="captchaSrc" @click="getCaptchaSrc"/>
            </ElTooltip>
          </div>
        </div>
      </ElFormItem>
    </ElForm>
    <div class="footer">
      <ElButton
        type="primary"
        @click="handleRegister"
        class="btn"
        style="width:100%"
        :loading="btnRegisterLoading">
        {{$t('m.UserRegister')}}
      </ElButton>
      <ElButton
        @click="switchMode('login')"
        class="btn"
        style="width:100%">
        {{$t('m.Already_Registed')}}
      </ElButton>
    </div>
  </div>
</template>

<script>
  import { mapGetters, mapActions } from 'vuex'
  import api from '@oj/api'
  import { User, Lock, Sunny, Message } from '@element-plus/icons-vue'

  export default {
    name: 'Register',
    components: { User, Lock, Sunny, Message },
    mounted () {
      this.getCaptchaSrc()
    },
    data () {
      const CheckUsernameNotExist = (rule, value, callback) => {
        api.checkUsernameOrEmail(value, undefined).then(res => {
          if (res.data.data.username === true) {
            callback(new Error(this.$t('m.The_username_already_exists')))
          } else {
            callback()
          }
        }, _ => callback())
      }
      const CheckEmailNotExist = (rule, value, callback) => {
        api.checkUsernameOrEmail(undefined, value).then(res => {
          if (res.data.data.email === true) {
            callback(new Error(this.$t('m.The_email_already_exists')))
          } else {
            callback()
          }
        }, _ => callback())
      }
      const CheckPassword = (rule, value, callback) => {
        if (this.formRegister.password !== '') {
          // 对第二个密码框再次验证
          this.$refs.formRegister.validateField('passwordAgain')
        }
        callback()
      }

      const CheckAgainPassword = (rule, value, callback) => {
        if (value !== this.formRegister.password) {
          callback(new Error(this.$t('m.password_does_not_match')))
        }
        callback()
      }

      return {
        btnRegisterLoading: false,
        formRegister: {
          username: '',
          password: '',
          passwordAgain: '',
          email: '',
          captcha: ''
        },
        ruleRegister: {
          username: [
            {required: true, trigger: 'blur'},
            {validator: CheckUsernameNotExist, trigger: 'blur'}
          ],
          email: [
            {required: true, type: 'email', trigger: 'blur'},
            {validator: CheckEmailNotExist, trigger: 'blur'}
          ],
          password: [
            {required: true, trigger: 'blur', min: 6, max: 20},
            {validator: CheckPassword, trigger: 'blur'}
          ],
          passwordAgain: [
            {required: true, validator: CheckAgainPassword, trigger: 'change'}
          ],
          captcha: [
            {required: true, trigger: 'blur', min: 1, max: 10}
          ]
        }
      }
    },
    methods: {
      ...mapActions(['changeModalStatus', 'getProfile']),
      switchMode (mode) {
        if (this.$route.name === 'register' && mode === 'login') {
          const redirect = typeof this.$route.query.redirect === 'string' ? this.$route.query.redirect : '/'
          this.$router.push({name: 'login', query: {redirect}})
          return
        }
        this.changeModalStatus({
          mode,
          visible: true
        })
      },
      handleRegister () {
        this.$refs.formRegister.validate().then(valid => {
          let formData = Object.assign({}, this.formRegister)
          delete formData['passwordAgain']
          this.btnRegisterLoading = true
          api.register(formData).then(res => {
            this.$success(this.$t('m.Thanks_for_registering'))
            this.switchMode('login')
            this.btnRegisterLoading = false
          }, _ => {
            this.getCaptchaSrc()
            this.formRegister.captcha = ''
            this.btnRegisterLoading = false
          })
        })
      }
    },
    computed: {
      ...mapGetters(['website', 'modalStatus'])

    }
  }
</script>

<style scoped lang="less">
  .footer {
    overflow: auto;
    margin-top: 24px;
    margin-bottom: -15px;
    text-align: left;
    
    .btn {
      margin: 0 0 16px 0;
      height: 40px;
      font-size: 14px;
      font-weight: 600;
      
      &:last-child {
        margin: 0;
      }
    }
  }
</style>
