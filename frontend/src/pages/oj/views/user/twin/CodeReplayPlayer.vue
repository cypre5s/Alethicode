<template>
  <div class="cr-player" role="region" aria-label="代码重放播放器">
    <div class="cr-player__header">
      <h3 class="cr-player__title">代码考古</h3>
      <select v-model="selectedProblemId" class="cr-player__select" @change="loadReplay">
        <option :value="null" disabled>选择题目...</option>
        <option v-for="p in problems" :key="p.problem_id" :value="p.problem_id">
          {{ p.title }} ({{ p.frame_count }} 帧)
        </option>
      </select>
    </div>

    <div v-if="loading" class="cr-player__skeleton">
      <el-skeleton :rows="5" animated />
    </div>

    <div v-else-if="!hasReplay && selectedProblemId" class="cr-player__empty">
      这道题没有可重放的编码记录
    </div>

    <template v-else-if="frames.length > 0">
      <div class="cr-player__code-area">
        <pre class="cr-player__code"><code>{{ currentCode }}</code></pre>
      </div>

      <div class="cr-player__controls">
        <button type="button" class="cr-ctrl-btn" aria-label="上一帧" :disabled="currentFrame <= 0" @click="prevFrame">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="15 18 9 12 15 6"/></svg>
        </button>
        <button type="button" class="cr-ctrl-btn cr-ctrl-btn--play" :aria-label="playing ? '暂停' : '播放'" @click="togglePlay">
          <svg v-if="!playing" width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><polygon points="5 3 19 12 5 21"/></svg>
          <svg v-else width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><rect x="6" y="4" width="4" height="16"/><rect x="14" y="4" width="4" height="16"/></svg>
        </button>
        <button type="button" class="cr-ctrl-btn" aria-label="下一帧" :disabled="currentFrame >= frames.length - 1" @click="nextFrame">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg>
        </button>

        <input
          type="range"
          class="cr-slider"
          :min="0"
          :max="frames.length - 1"
          :value="currentFrame"
          :aria-label="`帧 ${currentFrame + 1} / ${frames.length}`"
          @input="seekFrame($event.target.value)"
        />

        <span class="cr-frame-label">{{ currentFrame + 1 }} / {{ frames.length }}</span>
      </div>

      <div v-if="stats" class="cr-player__stats">
        <span>耗时 {{ Math.round(stats.duration_seconds / 60) }} 分钟</span>
        <span>+{{ stats.total_chars_added }} / -{{ stats.total_chars_deleted }} 字符</span>
        <span>最长 {{ stats.max_line_count }} 行</span>
      </div>
    </template>

    <div v-else-if="!selectedProblemId" class="cr-player__hint">
      选择一道做过的题目，重放你的编码过程
    </div>
  </div>
</template>

<script>
import api from '@oj/api'

export default {
  name: 'CodeReplayPlayer',
  data () {
    return {
      problems: [],
      selectedProblemId: null,
      loading: false,
      frames: [],
      stats: null,
      hasReplay: false,
      currentFrame: 0,
      playing: false,
      playTimer: null
    }
  },
  computed: {
    currentCode () {
      if (this.frames.length === 0 || this.currentFrame >= this.frames.length) return ''
      return this.frames[this.currentFrame].code || ''
    }
  },
  mounted () {
    this.loadProblems()
  },
  beforeUnmount () {
    this.stopPlay()
  },
  methods: {
    async loadProblems () {
      try {
        const res = await api.getCodeReplayEvents({ list: true })
        this.problems = res.data.data || []
      } catch {
        this.problems = []
      }
    },
    async loadReplay () {
      if (!this.selectedProblemId) return
      this.loading = true
      this.stopPlay()
      this.currentFrame = 0
      try {
        const res = await api.getCodeReplayEvents({ problem_id: this.selectedProblemId })
        const d = res.data.data
        this.frames = d.frames || []
        this.stats = d.stats || null
        this.hasReplay = d.has_replay || false
      } catch {
        this.frames = []
        this.hasReplay = false
      } finally {
        this.loading = false
      }
    },
    prevFrame () {
      if (this.currentFrame > 0) this.currentFrame--
    },
    nextFrame () {
      if (this.currentFrame < this.frames.length - 1) this.currentFrame++
    },
    seekFrame (val) {
      this.currentFrame = parseInt(val)
    },
    togglePlay () {
      if (this.playing) {
        this.stopPlay()
      } else {
        this.startPlay()
      }
    },
    startPlay () {
      this.playing = true
      this.playTimer = setInterval(() => {
        if (this.currentFrame < this.frames.length - 1) {
          this.currentFrame++
        } else {
          this.stopPlay()
        }
      }, 800)
    },
    stopPlay () {
      this.playing = false
      if (this.playTimer) {
        clearInterval(this.playTimer)
        this.playTimer = null
      }
    }
  }
}
</script>

<style lang="less" scoped>
@import '~@/styles/l99-tokens.less';

.cr-player {
  background: #fff;
  border-radius: @l99-radius-md;
  border: 1px solid @l99-neutral-200;
  box-shadow: @l99-shadow-1;
  padding: @l99-sp-5;

  &__header {
    display: flex;
    align-items: center;
    gap: @l99-sp-4;
    margin-bottom: @l99-sp-4;
  }
  &__title { font-size: @l99-fs-lg; font-weight: 600; color: @l99-neutral-900; margin: 0; }
  &__select {
    flex: 1;
    padding: @l99-sp-2 @l99-sp-3;
    border: 1px solid @l99-neutral-200;
    border-radius: @l99-radius-sm;
    font-size: @l99-fs-sm;
    &:focus { outline: none; border-color: @l99-primary; }
  }
  &__skeleton { padding: @l99-sp-4; }
  &__empty, &__hint {
    text-align: center; padding: @l99-sp-8; color: @l99-neutral-500; font-size: @l99-fs-sm;
  }

  &__code-area {
    background: @l99-neutral-100;
    border-radius: @l99-radius-sm;
    padding: @l99-sp-4;
    margin-bottom: @l99-sp-4;
    max-height: 360px;
    overflow: auto;
  }
  &__code {
    margin: 0;
    font-family: @l99-font-mono;
    font-size: @l99-fs-sm;
    line-height: 1.6;
    white-space: pre-wrap;
    word-break: break-all;
    color: @l99-neutral-900;
  }

  &__controls {
    display: flex;
    align-items: center;
    gap: @l99-sp-3;
    margin-bottom: @l99-sp-3;
  }

  &__stats {
    display: flex;
    gap: @l99-sp-4;
    font-size: @l99-fs-xs;
    color: @l99-neutral-500;
  }
}

.cr-ctrl-btn {
  width: 32px; height: 32px;
  border: 1px solid @l99-neutral-200;
  border-radius: @l99-radius-sm;
  background: #fff;
  color: @l99-neutral-700;
  cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  &:hover { background: @l99-neutral-100; }
  &:disabled { opacity: 0.3; cursor: not-allowed; }
  &--play { background: @l99-primary; color: #fff; border-color: @l99-primary; &:hover { opacity: 0.9; } }
}

.cr-slider {
  flex: 1;
  height: 4px;
  appearance: none;
  background: @l99-neutral-200;
  border-radius: 2px;
  &::-webkit-slider-thumb {
    appearance: none; width: 14px; height: 14px;
    border-radius: 50%; background: @l99-primary; cursor: pointer;
  }
}

.cr-frame-label {
  font-size: @l99-fs-xs;
  color: @l99-neutral-500;
  font-family: @l99-font-mono;
  min-width: 60px;
  text-align: right;
}
</style>
