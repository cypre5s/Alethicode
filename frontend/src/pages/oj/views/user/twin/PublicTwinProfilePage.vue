<template>
  <div class="ptp-page">
    <div v-if="loading" class="ptp-skeleton">
      <el-skeleton :rows="5" animated />
    </div>

    <div v-else-if="notFound" class="ptp-not-found">
      <h2>找不到这个孪生</h2>
      <p>可能还没公开，或者链接有误</p>
      <router-link to="/" class="ptp-back-link">回首页</router-link>
    </div>

    <template v-else>
      <div class="ptp-hero">
        <div class="ptp-avatar">
          <img v-if="profile.avatar_url" :src="profile.avatar_url" :alt="profile.handle" />
          <svg v-else width="80" height="80" viewBox="0 0 80 80" fill="none">
            <circle cx="40" cy="40" r="40" fill="#E5EEF7"/>
            <circle cx="40" cy="30" r="12" fill="#0F4C81"/>
            <path d="M16 68c0-13.255 10.745-24 24-24s24 10.745 24 24" fill="#0F4C81"/>
          </svg>
        </div>
        <h1 class="ptp-handle">@{{ profile.handle }}</h1>
        <p v-if="profile.bio" class="ptp-bio">{{ profile.bio }}</p>
      </div>

      <div v-if="profile.persona_text" class="ptp-section">
        <h2 class="ptp-section__title">关于 ta 的学习</h2>
        <p class="ptp-persona-text">{{ profile.persona_text }}</p>
      </div>

      <div v-if="profile.museum && profile.museum.length > 0" class="ptp-section">
        <h2 class="ptp-section__title">ta 的错误博物馆</h2>
        <div class="ptp-museum-grid">
          <div v-for="pin in profile.museum" :key="pin.pin_id" class="ptp-museum-card">
            <div class="ptp-museum-card__summary">{{ pin.memory_value }}</div>
            <blockquote v-if="pin.annotation" class="ptp-museum-card__annotation">{{ pin.annotation }}</blockquote>
          </div>
        </div>
      </div>

      <div class="ptp-footer">
        <p>Powered by <strong>Alethicode</strong> · 东华大学</p>
        <router-link to="/register" class="ptp-cta">你也来养一只学习孪生 →</router-link>
      </div>
    </template>
  </div>
</template>

<script>
import api from '@oj/api'

export default {
  name: 'PublicTwinProfilePage',
  data () {
    return { loading: false, notFound: false, profile: {} }
  },
  mounted () {
    const handle = this.$route.params.handle
    if (handle) this.loadProfile(handle)
  },
  methods: {
    async loadProfile (handle) {
      this.loading = true
      try {
        const res = await api.getPublicProfile(handle)
        this.profile = res.data.data
      } catch {
        this.notFound = true
      } finally {
        this.loading = false
      }
    }
  }
}
</script>

<style lang="less" scoped>
@import '~@/styles/l99-tokens.less';

.ptp-page { max-width: 800px; margin: 0 auto; padding: @l99-sp-8 @l99-sp-6; }
.ptp-skeleton { padding: @l99-sp-6; }
.ptp-not-found {
  text-align: center; padding: @l99-sp-10;
  h2 { font-size: @l99-fs-xl; color: @l99-neutral-900; }
  p { color: @l99-neutral-500; margin: @l99-sp-3 0; }
}
.ptp-back-link { color: @l99-primary; text-decoration: none; &:hover { text-decoration: underline; } }

.ptp-hero { text-align: center; margin-bottom: @l99-sp-8; }
.ptp-avatar {
  margin-bottom: @l99-sp-4;
  img { width: 80px; height: 80px; border-radius: 50%; object-fit: cover; }
}
.ptp-handle { font-size: @l99-fs-2xl; color: @l99-primary; margin: 0 0 @l99-sp-2; }
.ptp-bio { font-size: @l99-fs-md; color: @l99-neutral-700; line-height: 1.6; }

.ptp-section {
  margin-bottom: @l99-sp-8;
  &__title { font-size: @l99-fs-lg; font-weight: 600; color: @l99-neutral-900; margin: 0 0 @l99-sp-4; }
}
.ptp-persona-text { font-size: @l99-fs-md; color: @l99-neutral-700; line-height: 1.7; }

.ptp-museum-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: @l99-sp-4; }
.ptp-museum-card {
  padding: @l99-sp-4;
  background: @l99-neutral-100;
  border-radius: @l99-radius-md;
  &__summary { font-size: @l99-fs-sm; color: @l99-neutral-900; line-height: 1.5; margin-bottom: @l99-sp-2; }
  &__annotation {
    margin: 0; padding: @l99-sp-2 @l99-sp-3;
    border-left: 2px solid @l99-accent;
    font-size: @l99-fs-xs; color: @l99-neutral-500; font-style: italic;
  }
}

.ptp-footer {
  text-align: center; padding: @l99-sp-8 0; border-top: 1px solid @l99-neutral-200;
  p { font-size: @l99-fs-sm; color: @l99-neutral-500; }
}
.ptp-cta {
  display: inline-block; margin-top: @l99-sp-3; padding: @l99-sp-2 @l99-sp-5;
  background: @l99-primary; color: #fff; border-radius: @l99-radius-sm;
  font-size: @l99-fs-sm; text-decoration: none;
  &:hover { opacity: 0.9; }
}
</style>
