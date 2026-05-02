<template>
  <div class="pdf-viewer-page">
    <header class="pdf-viewer-bar">
      <div class="pdf-viewer-title">{{ documentTitle || '课件预览' }}</div>
      <div class="pdf-viewer-controls">
        <button class="pdf-viewer-btn" :disabled="currentPage <= 1" @click="goPage(currentPage - 1)">
          <span class="pdf-viewer-arrow">‹</span>
        </button>
        <span class="pdf-viewer-page-info">第 {{ currentPage }} 页</span>
        <button class="pdf-viewer-btn" :disabled="currentPage >= totalPages" @click="goPage(currentPage + 1)">
          <span class="pdf-viewer-arrow">›</span>
        </button>
        <a class="pdf-viewer-btn" :href="pdfUrl" download title="下载 PDF">↓</a>
      </div>
    </header>
    <main class="pdf-viewer-body">
      <PdfPageViewer v-if="pdfUrl" :src="pdfUrl" :page="currentPage" @loaded="onPdfLoaded" />
      <div v-else class="pdf-viewer-empty">缺少预览参数</div>
    </main>
  </div>
</template>

<script>
import PdfPageViewer from '@/components/PdfPageViewer.vue'

export default {
  name: 'PdfViewerPage',
  components: { PdfPageViewer },
  data () {
    return {
      currentPage: 1,
      totalPages: 1
    }
  },
  computed: {
    pdfUrl () {
      return this.$route.query.url || ''
    },
    initialPage () {
      return parseInt(this.$route.query.page, 10) || 1
    },
    documentTitle () {
      return this.$route.query.title || ''
    }
  },
  created () {
    this.currentPage = this.initialPage
  },
  methods: {
    goPage (n) {
      this.currentPage = Math.max(1, Math.min(n, this.totalPages))
    },
    onPdfLoaded (info) {
      if (info && info.numPages) this.totalPages = info.numPages
    }
  }
}
</script>

<style scoped>
.pdf-viewer-page {
  height: 100vh;
  height: 100dvh;
  display: flex;
  flex-direction: column;
  background: #f0f2f5;
}
.pdf-viewer-bar {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 20px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  box-shadow: 0 1px 3px rgba(0,0,0,0.06);
}
.pdf-viewer-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 50%;
}
.pdf-viewer-controls {
  display: flex;
  align-items: center;
  gap: 8px;
}
.pdf-viewer-page-info {
  font-size: 13px;
  color: #606266;
  min-width: 60px;
  text-align: center;
}
.pdf-viewer-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  background: #fff;
  color: #303133;
  font-size: 16px;
  cursor: pointer;
  text-decoration: none;
  transition: border-color 0.15s, background 0.15s;
}
.pdf-viewer-btn:hover:not(:disabled) {
  border-color: #409eff;
  color: #409eff;
}
.pdf-viewer-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.pdf-viewer-arrow {
  font-size: 20px;
  line-height: 1;
}
.pdf-viewer-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  justify-content: center;
  padding: 20px;
}
.pdf-viewer-body :deep(.pdf-page-viewer) {
  max-width: 900px;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.08);
}
.pdf-viewer-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #909399;
  font-size: 14px;
}
</style>
