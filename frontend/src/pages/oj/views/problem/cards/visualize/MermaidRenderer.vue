<template>
  <div class="viz-mermaid">
    <div v-if="error" class="viz-error">{{ error }}</div>
    <div
      v-else
      class="viz-mermaid-svg"
      :class="{ 'is-clickable': !!svgMarkup }"
      title="点击放大查看"
      @click="openZoom"
      v-html="svgMarkup"
    ></div>
    <button v-if="svgMarkup" type="button" class="viz-zoom-hint" @click="openZoom">
      放大查看
    </button>
    <Teleport to="body">
      <div
        v-if="zoomVisible"
        class="viz-zoom-mask"
        role="dialog"
        aria-modal="true"
        aria-label="流程图放大预览"
        @click.self="closeZoom"
      >
        <div class="viz-zoom-dialog">
          <div class="viz-zoom-header">
            <span>流程图放大预览</span>
            <button type="button" class="viz-zoom-close" @click="closeZoom">关闭</button>
          </div>
          <div class="viz-zoom-body">
            <img :src="svgDataUri" alt="流程图放大预览" />
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script>
import mermaid from 'mermaid'

export default {
  name: 'MermaidRenderer',
  props: {
    payload: {
      type: String,
      default: ''
    }
  },
  data () {
    return {
      svgMarkup: '',
      error: '',
      zoomVisible: false,
      _renderSeq: 0
    }
  },
  computed: {
    svgDataUri () {
      return this.svgMarkup
        ? `data:image/svg+xml;charset=UTF-8,${encodeURIComponent(this.svgMarkup)}`
        : ''
    }
  },
  watch: {
    payload: {
      immediate: true,
      handler () {
        this.renderMermaid()
      }
    }
  },
  methods: {
    async renderMermaid () {
      this.error = ''
      this.svgMarkup = ''
      const source = (this.payload || '').trim()
      if (!source) {
        this.error = 'Mermaid 内容为空'
        return
      }
      const seq = ++this._renderSeq
      try {
        mermaid.initialize({
          startOnLoad: false,
          securityLevel: 'strict',
          suppressErrorRendering: true,
          theme: 'default'
        })
        await mermaid.parse(source)
        const renderId = 'viz_mermaid_' + Date.now() + '_' + Math.random().toString(36).slice(2, 8)
        const rendered = await mermaid.render(renderId, source)
        if (seq !== this._renderSeq) return
        this.svgMarkup = rendered.svg || ''
      } catch {
        if (seq !== this._renderSeq) return
        this.error = 'Mermaid 渲染失败'
      }
    },
    openZoom () {
      if (!this.svgMarkup) return
      this.zoomVisible = true
    },
    closeZoom () {
      this.zoomVisible = false
    }
  }
}
</script>

<style scoped>
.viz-mermaid {
  width: 100%;
  position: relative;
}

.viz-mermaid-svg {
  overflow-x: auto;
}

.viz-mermaid-svg.is-clickable {
  cursor: zoom-in;
}

.viz-zoom-hint {
  margin-top: 6px;
  border: 1px solid rgba(37, 99, 235, 0.18);
  border-radius: 999px;
  padding: 3px 10px;
  background: rgba(239, 246, 255, 0.78);
  color: #2563eb;
  font-size: 12px;
  cursor: zoom-in;
}

.viz-zoom-mask {
  position: fixed;
  inset: 0;
  z-index: 3000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 28px;
  background: rgba(15, 23, 42, 0.46);
  backdrop-filter: blur(6px);
}

.viz-zoom-dialog {
  width: min(960px, 94vw);
  max-height: 88vh;
  display: flex;
  flex-direction: column;
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 24px 70px rgba(15, 23, 42, 0.26);
  overflow: hidden;
}

.viz-zoom-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #e5e7eb;
  font-size: 14px;
  font-weight: 600;
  color: #111827;
}

.viz-zoom-close {
  border: 0;
  border-radius: 999px;
  padding: 5px 12px;
  background: #f1f5f9;
  color: #475569;
  cursor: pointer;
}

.viz-zoom-body {
  overflow: auto;
  padding: 18px;
  background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
}

.viz-zoom-body img {
  display: block;
  min-width: 720px;
  max-width: none;
  margin: 0 auto;
}

.viz-error {
  color: #b91c1c;
  font-size: 13px;
}
</style>
