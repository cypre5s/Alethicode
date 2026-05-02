<template>
  <div class="setting-main">
    <p class="section-title">{{$t('m.Sessions')}}</p>
    <div class="flex-container setting-content">
      <template v-for="session in sessions" :key="session.session_key">
        <ElCard :body-style="{ padding: '20px' }" class="flex-child">
          <template #header>
            <div style="display: flex; align-items: center; justify-content: space-between;">
              <span style="line-height: 20px">{{session.ip}}</span>
              <div>
                <ElTag v-if="session.current_session" type="success">Current</ElTag>
                <ElButton v-else
                        type="warning"
                        size="small"
                        @click="deleteSession(session.session_key)">Revoke
                </ElButton>
              </div>
            </div>
          </template>
          <ElForm :label-width="100">
            <ElFormItem label="OS :" class="item">
              {{ platform(session.user_agent) }}
            </ElFormItem>
            <ElFormItem label="Browser :" class="item">
              {{ browser(session.user_agent) }}
            </ElFormItem>
            <ElFormItem label="Last Activity :" class="item">
              {{ localtime(session.last_activity) }}
            </ElFormItem>
          </ElForm>
        </ElCard>
      </template>
    </div>

    <p class="section-title">{{$t('m.Two_Factor_Authentication')}}</p>
    <div class="mini-container setting-content">
      <ElForm>
        <ElAlert v-if="TFAOpened"
               type="success"
               class="notice"
               show-icon
               :closable="false">You have enabled two-factor authentication.
        </ElAlert>
        <ElFormItem v-if="!TFAOpened">
          <div class="oj-relative" v-loading="loadingQRcode">
            <img :src="qrcodeSrc" id="qr-img">
          </div>
        </ElFormItem>
        <template v-if="!loadingQRcode">
          <ElFormItem style="width: 250px">
            <ElInput v-model="formTwoFactor.code" placeholder="Enter the code from your application"/>
          </ElFormItem>
          <ElButton type="primary"
                  :loading="loadingBtn"
                  @click="updateTFA(false)"
                  v-if="!TFAOpened">Open TFA
          </ElButton>
          <ElButton type="danger"
                  :loading="loadingBtn"
                  @click="closeTFA"
                  v-else>Close TFA
          </ElButton>
        </template>
      </ElForm>
    </div>
  </div>
</template>

<script>
  import api from '@oj/api'
  import {mapGetters, mapActions} from 'vuex'
  import { resolveTwoFactorQrSrc } from '@/utils/twoFactorQrCode'
  import filters from '@/utils/filters'
  import { ElMessageBox } from 'element-plus'

  export default {
    name: 'SecuritySetting',
    data () {
      return {
        qrcodeSrc: '',
        loadingQRcode: false,
        loadingBtn: false,
        formTwoFactor: {
          code: ''
        },
        sessions: []
      }
    },
    mounted () {
      this.getSessions()
      if (!this.TFAOpened) {
        this.getAuthImg()
      }
    },
    methods: {
      ...mapActions(['getProfile']),
      localtime: filters.localtime,
      browser: filters.browser,
      platform: filters.platform,
      async getAuthImg () {
        this.loadingQRcode = true
        try {
          const res = await api.twoFactorAuth('get')
          this.qrcodeSrc = await resolveTwoFactorQrSrc(res.data.data)
        } catch (error) {
          this.qrcodeSrc = ''
        } finally {
          this.loadingQRcode = false
        }
      },
      getSessions () {
        api.getSessions().then(res => {
          let data = res.data.data
          let sessions = data.filter(session => {
            return session.current_session
          })
          data.forEach(session => {
            if (!session.current_session) {
              sessions.push(session)
            }
          })
          this.sessions = sessions
        }).catch(() => { this.$error('获取登录会话失败') })
      },
      deleteSession (sessionKey) {
        ElMessageBox.confirm(
          'Are you sure to revoke the session?',
          'Confirm',
          { confirmButtonText: 'OK', cancelButtonText: 'Cancel', type: 'warning' }
        ).then(() => {
          api.deleteSession(sessionKey).then(res => {
            this.getSessions()
          }, _ => {
          })
        }).catch(() => {})
      },
      closeTFA () {
        ElMessageBox.confirm(
          'Two-factor Authentication is a powerful tool to protect your account, are you sure to close it?',
          'Confirm',
          { confirmButtonText: 'OK', cancelButtonText: 'Cancel', type: 'warning' }
        ).then(() => {
          this.updateTFA(true)
        }).catch(() => {})
      },
      updateTFA (close) {
        let method = close === false ? 'post' : 'put'
        this.loadingBtn = true
        api.twoFactorAuth(method, this.formTwoFactor).then(res => {
          this.loadingBtn = false
          this.getProfile()
          if (close === true) {
            this.getAuthImg()
            this.formTwoFactor.code = ''
          }
          this.formTwoFactor.code = ''
        }, err => {
          this.formTwoFactor.code = ''
          this.loadingBtn = false
          if (err.data.data.indexOf('session') > -1) {
            this.getProfile()
            this.getAuthImg()
          }
        })
      }
    },
    computed: {
      ...mapGetters(['user']),
      TFAOpened () {
        return this.user && this.user.two_factor_auth
      }
    }
  }
</script>

<style lang="less" scoped>
  .notice {
    font-size: 16px;
    margin-bottom: 20px;
    display: inline-block;
  }

  .oj-relative {
    width: 150px;
    #qr-img {
      width: 300px;
      margin: -10px 0 -30px -20px;
    }
  }

  .flex-container {
    flex-flow: row wrap;
    justify-content: flex-start;
    .flex-child {
      flex: 1 0;
      max-width: 350px;
      margin-right: 30px;
      margin-bottom: 30px;
      .item {
        margin-bottom: 0;
      }
    }
  }
</style>
