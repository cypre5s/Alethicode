<template>
  <div class="setting-main">
    <div class="flex-container">
      <div class="left">
        <p class="section-title">{{$t('m.ChangePassword')}}</p>
        <ElForm class="setting-content" ref="formPassword" :model="formPassword" :rules="rulePassword" label-width="120px" label-position="left">
          <ElFormItem label="旧密码" prop="old_password">
            <ElInput v-model="formPassword.old_password" type="password"/>
          </ElFormItem>
          <ElFormItem label="新密码" prop="new_password">
            <ElInput v-model="formPassword.new_password" type="password"/>
          </ElFormItem>
          <ElFormItem label="确认新密码" prop="again_password">
            <ElInput v-model="formPassword.again_password" type="password"/>
          </ElFormItem>
          <ElFormItem v-if="visible.tfaRequired" label="双因素验证码" prop="tfa_code">
            <ElInput v-model="formPassword.tfa_code"/>
          </ElFormItem>
          <ElFormItem v-if="visible.passwordAlert">
            <ElAlert type="success" :closable="false">密码已修改，将在 5 秒后自动重新登录</ElAlert>
          </ElFormItem>
          <ElButton type="primary" @click="changePassword">{{$t('m.Update_Password')}}</ElButton>
        </ElForm>
      </div>

      <div class="middle separator"></div>

      <div class="right">
        <p class="section-title">{{$t('m.ChangeEmail')}}</p>
        <ElForm class="setting-content" ref="formEmail" :model="formEmail" :rules="ruleEmail" label-width="120px" label-position="left">
          <ElFormItem label="当前密码" prop="password">
            <ElInput v-model="formEmail.password" type="password"/>
          </ElFormItem>
          <ElFormItem label="旧邮箱">
            <ElInput v-model="formEmail.old_email" disabled/>
          </ElFormItem>
          <ElFormItem label="新邮箱" prop="new_email">
            <ElInput v-model="formEmail.new_email"/>
          </ElFormItem>
          <ElFormItem v-if="visible.tfaRequired" label="双因素验证码" prop="tfa_code">
            <ElInput v-model="formEmail.tfa_code"/>
          </ElFormItem>
          <ElButton type="primary" @click="changeEmail">{{$t('m.ChangeEmail')}}</ElButton>
        </ElForm>
      </div>
    </div>
  </div>
</template>

<script>
  import api from '@oj/api'

  export default {
    name: 'AccountSetting',
    data () {
      const oldPasswordCheck = [
        { required: true, trigger: 'blur', message: '请输入密码' },
        { min: 6, max: 20, trigger: 'blur', message: '密码长度需为 6-20 位' }
      ]
      const tfaCheck = [{ required: true, trigger: 'change', message: '请输入双因素验证码' }]
      const CheckAgainPassword = (rule, value, callback) => {
        if (value !== this.formPassword.new_password) {
          callback(new Error('两次输入的新密码不一致'))
        }
        callback()
      }
      const CheckNewPassword = (rule, value, callback) => {
        if (this.formPassword.old_password !== '') {
          if (this.formPassword.old_password === this.formPassword.new_password) {
            callback(new Error('新密码不能与旧密码相同'))
          } else {
            // 对第二个密码框再次验证
            this.$refs.formPassword.validateField('again_password')
          }
        }
        callback()
      }
      return {
        loading: {
          btnPassword: false,
          btnEmail: false
        },
        visible: {
          passwordAlert: false,
          emailAlert: false,
          tfaRequired: false
        },
        formPassword: {
          tfa_code: '',
          old_password: '',
          new_password: '',
          again_password: ''
        },
        formEmail: {
          tfa_code: '',
          password: '',
          old_email: '',
          new_email: ''
        },
        rulePassword: {
          old_password: oldPasswordCheck,
          new_password: [
            { required: true, trigger: 'blur', message: '请输入新密码' },
            { min: 6, max: 20, trigger: 'blur', message: '新密码长度需为 6-20 位' },
            { validator: CheckNewPassword, trigger: 'blur' }
          ],
          again_password: [
            { required: true, trigger: 'change', message: '请再次输入新密码' },
            { validator: CheckAgainPassword, trigger: 'change' }
          ],
          tfa_code: tfaCheck
        },
        ruleEmail: {
          password: oldPasswordCheck,
          new_email: [
            { required: true, trigger: 'change', message: '请输入新邮箱' },
            { type: 'email', trigger: 'change', message: '邮箱格式不正确' }
          ],
          tfa_code: tfaCheck
        }
      }
    },
    mounted () {
      this.formEmail.old_email = this.$store.getters.user.email || ''
    },
    methods: {
      changePassword () {
        this.$refs.formPassword.validate().then(() => {
          this.loading.btnPassword = true
          let data = Object.assign({}, this.formPassword)
          delete data.again_password
          if (!this.visible.tfaRequired) {
            delete data.tfa_code
          }
          api.changePassword(data).then(() => {
            this.loading.btnPassword = false
            this.visible.passwordAlert = true
            this.$success('密码更新成功')
            setTimeout(() => {
              this.visible.passwordAlert = false
              this.$router.push({name: 'logout'})
            }, 5000)
          }, res => {
            if (res.data.data === 'tfa_required') {
              this.visible.tfaRequired = true
            }
            this.loading.btnPassword = false
          })
        })
      },
      changeEmail () {
        this.$refs.formEmail.validate().then(() => {
          this.loading.btnEmail = true
          let data = Object.assign({}, this.formEmail)
          if (!this.visible.tfaRequired) {
            delete data.tfa_code
          }
          api.changeEmail(data).then(() => {
            this.loading.btnEmail = false
            this.visible.emailAlert = true
            this.$success('邮箱修改成功')
            this.$refs.formEmail.resetFields()
          }, res => {
            if (res.data.data === 'tfa_required') {
              this.visible.tfaRequired = true
            }
            this.loading.btnEmail = false
          })
        })
      }
    }
  }
</script>

<style lang="less" scoped>

  .flex-container {
    justify-content: flex-start;
    .left {
      flex: 1 0;
      width: 250px;
      padding-right: 5%;
    }
    > .middle {
      flex: none;
    }
    .right {
      flex: 1 0;
      width: 250px;
    }
  }
</style>
