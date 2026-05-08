<template>
  <div class="pdf-page-viewer" ref="container">
    <div v-if="loading" class="pdf-page-loading">
      <div class="pdf-page-spinner"></div>
    </div>
    <div v-else-if="error" class="pdf-page-error">{{ error }}</div>
    <canvas v-show="!loading && !error" ref="canvas" class="pdf-page-canvas"></canvas>
  </div>
</template>

<script>
// 必须使用 pdfjs-dist 的 legacy build。modern build（默认 entry）从 5.5.52 起
// 直接调用 ES2025 `Map.prototype.getOrInsertComputed` / `WeakMap.prototype.getOrInsertComputed`
// （TC39 proposal-upsert），仅 Chrome 145+/Firefox 144+/Safari 26.2+ 支持，会让
// 学生端在 renderPage 阶段抛 `TypeError: ...getOrInsertComputed is not a function`，
// UI 上落到「页面渲染失败」。legacy build 通过 core-js 把这些 API 全部 polyfill，
// 是 mozilla/pdf.js 官方对 broader compat 场景的推荐路径。
import * as pdfjsLib from 'pdfjs-dist/legacy/build/pdf.mjs'
import workerUrl from 'pdfjs-dist/legacy/build/pdf.worker.mjs?url'

pdfjsLib.GlobalWorkerOptions.workerSrc = workerUrl

// PDF.js 渲染包含 CJK 字体或非内嵌 Type1 字体的 PDF 时，需要 cmaps 与
// standard_fonts 资源；缺这两个 base url，单页 render 阶段会抛
// UnknownErrorException，UI 上落到「页面渲染失败」。课件 QA 的 PDF 由
// LibreOffice 从 .pptx 转出，普遍依赖中文 cmap，所以这两条 url 必须显式传。
// 资源由 vite-plugin pdfjsAssetsPlugin 在 build 时从 node_modules/pdfjs-dist
// 复制到 dist/static/pdfjs/，dev mode 由同一个插件的 middleware 直接 serve。
const PDFJS_ASSET_BASE_RAW = (import.meta.env && import.meta.env.BASE_URL) || '/'
const PDFJS_ASSET_BASE = PDFJS_ASSET_BASE_RAW.replace(/\/+$/, '') + '/static/pdfjs/'
const CMAP_URL = PDFJS_ASSET_BASE + 'cmaps/'
const STANDARD_FONT_DATA_URL = PDFJS_ASSET_BASE + 'standard_fonts/'

export default {
  name: 'PdfPageViewer',
  emits: ['loaded'],
  props: {
    src: { type: String, required: true },
    page: { type: Number, required: true, default: 1 }
  },
  data () {
    return {
      loading: true,
      error: ''
    }
  },
  watch: {
    src () { this.loadAndRender() },
    page () { this.renderPage() }
  },
  created () {
    this._pdfDoc = null
    this._resizeObserver = null
    this._renderTask = null
  },
  mounted () {
    this._resizeObserver = new ResizeObserver(() => {
      if (this._pdfDoc && !this.loading) this.renderPage()
    })
    this._resizeObserver.observe(this.$refs.container)
    this.loadAndRender()
  },
  beforeUnmount () {
    if (this._resizeObserver) this._resizeObserver.disconnect()
    this._cleanup()
  },
  methods: {
    _cleanup () {
      if (this._renderTask) {
        this._renderTask.cancel()
        this._renderTask = null
      }
      if (this._pdfDoc) {
        this._pdfDoc.destroy()
        this._pdfDoc = null
      }
    },
    async loadAndRender () {
      this.loading = true
      this.error = ''
      this._cleanup()
      try {
        const pdfUrl = this.src.startsWith('/') ? window.location.origin + this.src : this.src
        const loadingTask = pdfjsLib.getDocument({
          url: pdfUrl,
          cMapUrl: CMAP_URL,
          cMapPacked: true,
          standardFontDataUrl: STANDARD_FONT_DATA_URL,
          enableXfa: false,
          withCredentials: true
        })
        this._pdfDoc = await loadingTask.promise
        this.$emit('loaded', { numPages: this._pdfDoc.numPages })
        await this.renderPage()
      } catch (e) {
        console.error('[PdfPageViewer] load error:', e, e && e.message, e && e.status)
        this.error = 'PDF 加载失败'
      } finally {
        this.loading = false
      }
    },
    async renderPage () {
      if (!this._pdfDoc) return
      if (this._renderTask) {
        this._renderTask.cancel()
        this._renderTask = null
      }
      const pageNum = Math.min(Math.max(1, this.page), this._pdfDoc.numPages)
      try {
        const pdfPage = await this._pdfDoc.getPage(pageNum)
        const container = this.$refs.container
        const canvas = this.$refs.canvas
        if (!container || !canvas) return

        const containerWidth = container.clientWidth || 300
        const unscaledViewport = pdfPage.getViewport({ scale: 1 })
        const dpr = window.devicePixelRatio || 1
        const scale = containerWidth / unscaledViewport.width
        const viewport = pdfPage.getViewport({ scale: scale * dpr })

        canvas.width = viewport.width
        canvas.height = viewport.height
        canvas.style.width = `${containerWidth}px`
        canvas.style.height = `${viewport.height / dpr}px`

        this._renderTask = pdfPage.render({
          canvasContext: canvas.getContext('2d'),
          viewport
        })
        await this._renderTask.promise
        this._renderTask = null
      } catch (e) {
        if (e && e.name === 'RenderingCancelled') return
        console.error('[PdfPageViewer] render error:', e)
        this.error = '页面渲染失败'
      }
    }
  }
}
</script>

<style scoped>
.pdf-page-viewer {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f8f8f8;
  border-radius: var(--border-radius-sm, 6px);
  overflow: hidden;
  min-height: 200px;
}
.pdf-page-canvas {
  display: block;
  width: 100%;
}
.pdf-page-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
}
.pdf-page-spinner {
  width: 28px;
  height: 28px;
  border: 3px solid var(--border-color, #e4e7ed);
  border-top-color: var(--primary-color, #2563eb);
  border-radius: 50%;
  animation: pdf-spin 0.8s linear infinite;
}
@keyframes pdf-spin {
  to { transform: rotate(360deg); }
}
.pdf-page-error {
  padding: 20px;
  color: var(--danger-color, #ef4444);
  font-size: 13px;
  text-align: center;
}
</style>
