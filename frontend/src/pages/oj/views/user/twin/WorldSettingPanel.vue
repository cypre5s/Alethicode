<template>
  <div class="ws-panel" role="region" aria-label="孪生世界观设置">
    <h3 class="ws-panel__title">定义你的学习世界</h3>
    <p class="ws-panel__desc">给你的学习旅程起个名字，选一个主题配色</p>

    <div class="ws-form">
      <div class="ws-field">
        <label class="ws-label">世界名称</label>
        <input
          v-model="worldName"
          type="text"
          class="ws-input"
          placeholder="比如：代码花园、编程星球..."
          maxlength="120"
        />
      </div>

      <div class="ws-field">
        <label class="ws-label">世界故事</label>
        <textarea
          v-model="worldNarrative"
          class="ws-textarea"
          placeholder="用一段话描述你理想中的学习世界..."
          rows="3"
          maxlength="500"
        ></textarea>
      </div>

      <div class="ws-field">
        <label class="ws-label">主题配色</label>
        <div class="ws-themes">
          <button
            v-for="theme in themes"
            :key="theme.id"
            type="button"
            :class="['ws-theme-btn', { 'is-active': selectedTheme === theme.id }]"
            :style="{ '--theme-color': theme.color }"
            @click="selectedTheme = theme.id"
          >
            <span class="ws-theme-swatch" :style="{ backgroundColor: theme.color }"></span>
            {{ theme.label }}
          </button>
        </div>
      </div>

      <button type="button" class="ws-save-btn" :disabled="saving" @click="save">
        {{ saving ? '保存中...' : '保存世界观' }}
      </button>
    </div>
  </div>
</template>

<script>
import api from '@oj/api'
import { notify } from '@/utils/notifications'

const THEMES = [
  { id: 'academy', label: '学院蓝', color: '#0F4C81' },
  { id: 'forest', label: '森林绿', color: '#065F46' },
  { id: 'sunset', label: '日落橙', color: '#C2410C' },
  { id: 'galaxy', label: '星空紫', color: '#6D28D9' },
  { id: 'ocean', label: '海洋青', color: '#0E7490' },
  { id: 'sakura', label: '樱花粉', color: '#DB2777' }
]

export default {
  name: 'WorldSettingPanel',
  data () {
    return {
      worldName: '编程学院',
      worldNarrative: '',
      selectedTheme: 'academy',
      themes: THEMES,
      saving: false
    }
  },
  mounted () { this.load() },
  methods: {
    async load () {
      try {
        const res = await api.getWorldSetting()
        const d = res.data.data
        if (d) {
          this.worldName = d.world_name || '编程学院'
          this.worldNarrative = d.world_narrative || ''
          this.selectedTheme = d.theme_id || 'academy'
        }
      } catch { /* use defaults */ }
    },
    async save () {
      this.saving = true
      try {
        await api.updateWorldSetting({
          world_name: this.worldName,
          world_narrative: this.worldNarrative,
          theme_id: this.selectedTheme
        })
        notify.success('世界观已更新')
      } catch { notify.error('保存失败，请稍后再试') }
      finally { this.saving = false }
    }
  }
}
</script>

<style lang="less" scoped>
@import '~@/styles/l99-tokens.less';

.ws-panel {
  background: #fff;
  border-radius: @l99-radius-md;
  border: 1px solid @l99-neutral-200;
  box-shadow: @l99-shadow-1;
  padding: @l99-sp-6;

  &__title { font-size: @l99-fs-xl; font-weight: 600; color: @l99-neutral-900; margin: 0 0 @l99-sp-1; }
  &__desc { font-size: @l99-fs-sm; color: @l99-neutral-500; margin: 0 0 @l99-sp-6; }
}

.ws-form { display: flex; flex-direction: column; gap: @l99-sp-5; }
.ws-field { display: flex; flex-direction: column; gap: @l99-sp-2; }
.ws-label { font-size: @l99-fs-sm; font-weight: 500; color: @l99-neutral-700; }
.ws-input, .ws-textarea {
  padding: @l99-sp-3;
  border: 1px solid @l99-neutral-200;
  border-radius: @l99-radius-sm;
  font-size: @l99-fs-md;
  font-family: @l99-font-sans;
  &:focus { outline: none; border-color: @l99-primary; box-shadow: 0 0 0 2px rgba(15,76,129,0.08); }
}
.ws-textarea { resize: vertical; line-height: 1.6; }

.ws-themes { display: flex; flex-wrap: wrap; gap: @l99-sp-2; }
.ws-theme-btn {
  display: flex; align-items: center; gap: @l99-sp-2;
  padding: @l99-sp-2 @l99-sp-3;
  border: 2px solid @l99-neutral-200;
  border-radius: @l99-radius-sm;
  background: #fff;
  font-size: @l99-fs-sm;
  cursor: pointer;
  transition: border-color @l99-dur-fast @l99-ease;
  &.is-active { border-color: var(--theme-color); }
  &:hover { border-color: var(--theme-color); }
}
.ws-theme-swatch { width: 16px; height: 16px; border-radius: 50%; }

.ws-save-btn {
  align-self: flex-start;
  padding: @l99-sp-3 @l99-sp-6;
  background: @l99-primary;
  color: #fff;
  border: none;
  border-radius: @l99-radius-sm;
  font-size: @l99-fs-md;
  font-weight: 500;
  cursor: pointer;
  &:hover { opacity: 0.9; }
  &:disabled { opacity: 0.4; cursor: not-allowed; }
}
</style>
