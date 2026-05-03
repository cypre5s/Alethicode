<template>
  <div class="em-container" role="region" aria-label="错误模式个人馆">
    <h2 class="em-title">我的错误博物馆</h2>
    <p class="em-subtitle">把最让你印象深刻的错误收藏起来，每一个都是你成长的证明</p>

    <div v-if="loading" class="em-skeleton">
      <el-skeleton :rows="3" animated />
    </div>

    <div v-else class="em-grid" role="list">
      <ErrorMuseumExhibit
        v-for="(pin, index) in paddedPins"
        :key="pin ? pin.pin_id : `empty-${index}`"
        :pin="pin"
        @unpin="handleUnpin"
        @update-annotation="handleUpdateAnnotation"
      />
    </div>
  </div>
</template>

<script>
import api from '@oj/api'
import ErrorMuseumExhibit from './ErrorMuseumExhibit.vue'
import { notify } from '@/utils/notifications'

export default {
  name: 'ErrorMuseumView',
  components: { ErrorMuseumExhibit },
  data () {
    return {
      loading: false,
      pins: []
    }
  },
  computed: {
    paddedPins () {
      const result = [...this.pins]
      while (result.length < 9) {
        result.push(null)
      }
      return result
    }
  },
  mounted () {
    this.loadPins()
  },
  methods: {
    async loadPins () {
      this.loading = true
      try {
        const res = await api.getMuseumPins()
        this.pins = res.data.data || []
      } catch {
        this.pins = []
      } finally {
        this.loading = false
      }
    },
    async handleUnpin (pinId) {
      try {
        await api.unpinMuseumMemory(pinId)
        this.pins = this.pins.filter(p => p.pin_id !== pinId)
        notify.success('已取消钉选')
      } catch {
        notify.error('操作失败')
      }
    },
    async handleUpdateAnnotation ({ pinId, annotation }) {
      try {
        await api.updateMuseumPin(pinId, { annotation })
        const pin = this.pins.find(p => p.pin_id === pinId)
        if (pin) pin.annotation = annotation
        notify.success('注释已更新')
      } catch {
        notify.error('保存失败')
      }
    }
  }
}
</script>

<style lang="less" scoped>
@import '~@/styles/l99-tokens.less';

.em-container {
  padding: @l99-sp-6;
}

.em-title {
  font-size: @l99-fs-xl;
  font-weight: 700;
  color: @l99-neutral-900;
  margin: 0 0 @l99-sp-1;
}

.em-subtitle {
  font-size: @l99-fs-sm;
  color: @l99-neutral-500;
  margin: 0 0 @l99-sp-6;
}

.em-skeleton { padding: @l99-sp-4; }

.em-grid {
  display: grid;
  grid-template-columns: repeat(3, 240px);
  gap: @l99-sp-5;
  justify-content: center;
}

@media (max-width: 1023px) {
  .em-grid { grid-template-columns: repeat(2, 240px); }
}

@media (max-width: 575px) {
  .em-grid { grid-template-columns: 1fr; }
  .em-grid > * { width: 100%; }
}
</style>
