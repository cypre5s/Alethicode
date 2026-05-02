<template>
  <ElDialog
    :model-value="visible"
    title="数据采集与隐私说明"
    width="520px"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    :show-close="false"
    class="beta-privacy-dialog"
    @update:model-value="onDialogUpdate"
  >
    <div class="bp-body">
      <p>
        公测期间，平台会采集您的页面访问、交互操作、性能指标、前端错误摘要、提交结果与 AI 导学事件，
        用于评估并改进教学平台。
      </p>
      <p>
        <strong>不会</strong>采集账户密码、完整对话记录或完整代码内容。
      </p>
      <p>反馈入口上传的截图存储于后台私有目录，仅授权管理员可查看。</p>
      <p>
        您可在「个人设置 &gt; 数据」中查询或申请删除已提交的反馈与截图。
      </p>
      <p class="bp-version">版本：{{ serverVersion || '未配置' }}</p>
    </div>
    <template #footer>
      <ElButton @click="decline">不同意并退出</ElButton>
      <ElButton type="primary" :disabled="!serverVersion" @click="accept">
        同意并继续使用
      </ElButton>
    </template>
  </ElDialog>
</template>

<script>
import { mapState, mapGetters } from 'vuex'

const STORAGE_KEY = 'betaPrivacyVersion'

export default {
  name: 'BetaPrivacyNotice',
  data () {
    return {
      visible: false
    }
  },
  computed: {
    ...mapState(['website']),
    ...mapGetters(['isAuthenticated']),
    serverVersion () {
      const cfg = this.website || {}
      return cfg.beta_privacy_version || cfg.betaPrivacyVersion || ''
    }
  },
  watch: {
    isAuthenticated: {
      immediate: true,
      handler (val) {
        if (!val) {
          this.visible = false
          return
        }
        this.checkVisibility()
      }
    },
    serverVersion () {
      this.checkVisibility()
    }
  },
  methods: {
    checkVisibility () {
      if (!this.isAuthenticated) {
        this.visible = false
        return
      }
      if (!this.serverVersion) {
        this.visible = false
        return
      }
      const stored = readStorage()
      this.visible = stored !== this.serverVersion
    },
    accept () {
      if (!this.serverVersion) return
      writeStorage(this.serverVersion)
      this.visible = false
    },
    decline () {
      this.visible = false
      this.$router.replace({ name: 'logout' }).catch(() => {
        if (typeof window !== 'undefined') {
          window.location.href = '/'
        }
      })
    },
    onDialogUpdate (val) {
      if (val === false && !this.visible) {
        return
      }
    }
  }
}

function readStorage () {
  if (typeof window === 'undefined' || !window.localStorage) return ''
  try {
    return window.localStorage.getItem(STORAGE_KEY) || ''
  } catch (_) {
    return ''
  }
}

function writeStorage (version) {
  if (typeof window === 'undefined' || !window.localStorage) return
  try {
    window.localStorage.setItem(STORAGE_KEY, version)
  } catch (_) { /* silent */ }
}
</script>

<style scoped lang="less">
.bp-body {
  font-size: var(--fs-md);
  color: var(--text-primary);
  line-height: var(--leading-loose);

  p {
    margin: 0 0 var(--space-2);
  }

  strong {
    color: var(--color-danger);
    font-weight: 600;
  }
}

.bp-version {
  font-size: var(--fs-sm);
  color: var(--text-disabled);
  margin-top: var(--space-3);
}
</style>
