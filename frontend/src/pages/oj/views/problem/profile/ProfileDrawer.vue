<template>
  <el-drawer
    v-model="visibleModel"
    title="我的学习画像"
    direction="rtl"
    size="380px"
    :close-on-click-modal="true"
  >
    <div v-if="loading" class="profile-loading">加载中…</div>
    <div v-else-if="error" class="profile-error">{{ error }}</div>
    <div v-else-if="profile" class="profile-body">
      <section class="profile-section">
        <header class="section-header">
          <span class="section-title">个性化推理</span>
          <el-switch
            :model-value="profile.personalizationEnabled"
            :loading="saving"
            @change="onTogglePersonalization"
          />
        </header>
        <p class="section-hint">
          关闭后，AI 将以通用模式回应，不再把你的画像注入提示词。
        </p>
      </section>

      <section class="profile-section">
        <header class="section-header">
          <span class="section-title">近 30 天学习摘要</span>
          <span class="section-meta">v{{ profile.narrativeSummary?.version ?? '-' }}</span>
        </header>
        <textarea
          v-model="editingSummary"
          class="summary-text"
          rows="6"
          :readonly="!editing"
          :placeholder="profile.narrativeSummary?.text || '暂无摘要，点击“重新生成”让 AI 总结你近期学习'"
        />
        <div class="summary-actions">
          <el-button
            v-if="!editing"
            size="small"
            :loading="saving"
            @click="onRefresh"
          >
            重新生成
          </el-button>
          <el-button
            v-if="!editing"
            size="small"
            type="primary"
            @click="startEdit"
          >
            手动改写
          </el-button>
          <el-button
            v-if="editing"
            size="small"
            @click="cancelEdit"
          >
            取消
          </el-button>
          <el-button
            v-if="editing"
            size="small"
            type="primary"
            :loading="saving"
            @click="onOverride"
          >
            保存为我自己写的画像
          </el-button>
        </div>
        <p v-if="profile.isUserOverridden" class="section-hint">
          已是你手动改写版本，AI 不会再自动覆盖（除非你重新生成）。
        </p>
      </section>

      <section class="profile-section">
        <header class="section-header">
          <span class="section-title">教学风格偏好</span>
        </header>
        <p class="section-line">
          {{ learningStyleLabel }} ({{ learningStyleKey }})
        </p>
      </section>

      <section v-if="profile.topWeakKcs && profile.topWeakKcs.length" class="profile-section">
        <header class="section-header">
          <span class="section-title">待巩固知识点</span>
        </header>
        <ul class="kc-list">
          <li v-for="kc in profile.topWeakKcs" :key="'weak-' + kc.name">
            <span class="kc-name">{{ kc.name }}</span>
            <span class="kc-score">{{ Math.round(kc.mastery * 100) }}%</span>
          </li>
        </ul>
      </section>

      <section v-if="profile.topStrongKcs && profile.topStrongKcs.length" class="profile-section">
        <header class="section-header">
          <span class="section-title">已掌握知识点</span>
        </header>
        <ul class="kc-list">
          <li v-for="kc in profile.topStrongKcs" :key="'strong-' + kc.name">
            <span class="kc-name">{{ kc.name }}</span>
            <span class="kc-score">{{ Math.round(kc.mastery * 100) }}%</span>
          </li>
        </ul>
      </section>

      <section v-if="profile.topErrors && profile.topErrors.length" class="profile-section">
        <header class="section-header">
          <span class="section-title">近期高频错误</span>
        </header>
        <ul class="error-list">
          <li v-for="err in profile.topErrors" :key="'err-' + err.taxonomyLabel">
            <span class="error-name">{{ err.taxonomyLabel }}</span>
            <span class="error-meta">{{ err.count30d }} 次</span>
          </li>
        </ul>
      </section>

      <section v-if="profile.stats30d" class="profile-section">
        <header class="section-header">
          <span class="section-title">30 天数据</span>
        </header>
        <ul class="stats-list">
          <li v-for="(v, k) in profile.stats30d" :key="k">
            <span class="stats-name">{{ formatStatLabel(k) }}</span>
            <span class="stats-value">{{ formatStat(v) }}</span>
          </li>
        </ul>
      </section>
    </div>
  </el-drawer>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useProfileApi } from '@/composables/useProfileApi'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  }
})
const emit = defineEmits(['update:modelValue'])

const visibleModel = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const editing = ref(false)
const editingSummary = ref('')

const { profile, loading, saving, error, load, refresh, updatePreferences, overrideSummary } = useProfileApi()

const STAT_LABELS = Object.freeze({
  ac_rate_30d: '近 30 天通过率',
  problems_ac_30d: '近 30 天通过题数',
  problems_attempted_30d: '近 30 天尝试题数'
})

const LEARNING_STYLE_LABELS = Object.freeze({
  step_by_step: '逐步引导',
  exploratory: '自主探索',
  visual: '样例/图示优先',
  analytical: '严谨推理'
})

const learningStyleKey = computed(() => {
  const key = profile.value?.learningStyle?.key
  return key && String(key).trim() ? key : 'step_by_step'
})

const learningStyleLabel = computed(() => {
  const style = profile.value?.learningStyle || {}
  const key = learningStyleKey.value
  const label = style.label && String(style.label).trim() ? style.label : ''
  return LEARNING_STYLE_LABELS[key] || label || '逐步引导'
})

watch(() => props.modelValue, async (val) => {
  if (val && !profile.value) {
    await load()
  }
  if (val && profile.value) {
    editingSummary.value = profile.value.narrativeSummary?.text || ''
  }
})

watch(profile, (val) => {
  if (val) {
    editingSummary.value = val.narrativeSummary?.text || ''
  }
})

function startEdit() {
  editing.value = true
}

function cancelEdit() {
  editing.value = false
  editingSummary.value = profile.value?.narrativeSummary?.text || ''
}

async function onRefresh() {
  await refresh()
}

async function onOverride() {
  if (!editingSummary.value || !editingSummary.value.trim()) {
    return
  }
  await overrideSummary(editingSummary.value.trim())
  editing.value = false
}

async function onTogglePersonalization(value) {
  await updatePreferences(Boolean(value))
}

function formatStat(value) {
  if (typeof value === 'number') {
    return Number.isInteger(value) ? String(value) : value.toFixed(2)
  }
  return String(value)
}

function formatStatLabel(key) {
  return STAT_LABELS[key] || key
}
</script>

<style scoped>
.profile-loading,
.profile-error {
  padding: 24px;
  font-size: 14px;
  color: #6b7280;
}
.profile-error {
  color: #b91c1c;
}
.profile-body {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 4px 4px 24px 4px;
}
.profile-section {
  border-bottom: 1px solid #e5e7eb;
  padding-bottom: 14px;
}
.profile-section:last-child {
  border-bottom: none;
}
.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.section-title {
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
}
.section-meta {
  font-size: 12px;
  color: #6b7280;
}
.section-hint {
  font-size: 12px;
  color: #9ca3af;
  margin: 6px 0 0 0;
}
.section-line {
  font-size: 13px;
  color: #4b5563;
  margin: 0;
}
.summary-text {
  width: 100%;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  padding: 8px;
  font-size: 13px;
  font-family: inherit;
  resize: vertical;
  background: #f9fafb;
  color: #1f2937;
}
.summary-text:read-only {
  background: #f9fafb;
}
.summary-actions {
  display: flex;
  gap: 8px;
  margin-top: 8px;
  flex-wrap: wrap;
}
.kc-list,
.error-list,
.stats-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.kc-list li,
.error-list li,
.stats-list li {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
}
.kc-name,
.error-name,
.stats-name {
  color: #4b5563;
}
.kc-score,
.error-meta,
.stats-value {
  color: #1f2937;
  font-weight: 500;
}
</style>
