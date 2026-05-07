<template>
  <div class="lesson-management">
    <el-card>
      <template #header><div class="card-header">
        <span class="card-title">
          <el-icon><Document /></el-icon>
          课件管理
        </span>
        <div v-if="isStaff">
          <el-button type="primary" @click="showUploadModal = true">
            <el-icon><Upload /></el-icon>
            上传课件
          </el-button>
        </div>
      </div></template>

      <el-table :data="pagedLessons" v-loading="loading">
        <el-table-column label="课件名称" min-width="200" align="center">
          <template #default="scope">
            <div style="display: inline-flex; align-items: center;">
              <el-icon style="margin-right: 5px;"><component :is="getFileIconComponent(scope.row.file_type)"/></el-icon>
              <a @click="viewLesson(scope.row)">{{ scope.row.title }}</a>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="格式" width="100" align="center">
          <template #default="scope">
            <span class="file-format-label">{{ (scope.row.file_type || scope.row.lesson_type || '').toUpperCase() }}</span>
          </template>
        </el-table-column>
        <el-table-column label="页数" width="100" align="center">
          <template #default="scope">{{ scope.row.total_pages || '-' }}</template>
        </el-table-column>
        <el-table-column label="关联题目" width="100" align="center">
          <template #default="scope">
            <span style="color: #000000; font-weight: bold;">{{ scope.row.linked_problems_count || 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="上传时间" width="180" align="center">
          <template #default="scope">{{ formatTime(scope.row.uploaded_at) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="center">
          <template #default="scope">
            <el-button type="primary" link size="small" @click="downloadLesson(scope.row)">下载</el-button>
            <el-button v-if="isStaff" type="danger" link size="small" @click="confirmDelete(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <Pagination
        :total="lessons.length"
        :current-page="lessonPage"
        :page-size="lessonPageSize"
        @update:currentPage="lessonPage = $event"
        @update:pageSize="lessonPageSize = $event"
      />
    </el-card>
    <el-dialog v-model="showUploadModal" title="上传课件" width="640"
               :close-on-click-modal="!uploading" :show-close="!uploading">
      <el-form :label-width="100">
        <el-form-item label="选择文件">
          <el-upload
            ref="upload"
            :before-upload="handleBeforeUpload"
            :show-file-list="false"
            accept=".pdf,.ppt,.pptx,.doc,.docx,.md"
            multiple
            action="#">
            <el-button :disabled="uploading">
              <el-icon><Upload /></el-icon>
              选择文件（可多选）
            </el-button>
          </el-upload>
          <div style="margin-top: 8px; color: #808695; font-size: 12px;">
            支持 PDF、PPT、Word、Markdown，单文件最大 100MB
          </div>
        </el-form-item>
        <el-form-item label="文件列表" v-if="uploadForm.files.length > 0">
          <div class="upload-file-list">
            <div v-for="(item, index) in uploadForm.files" :key="index" class="upload-file-item">
              <div class="file-info">
                <el-icon style="margin-right: 6px;"><component :is="getFileIconComponent(getFileExt(item.file.name))"/></el-icon>
                <span class="file-name">{{ item.file.name }}</span>
                <span class="file-size">{{ formatFileSize(item.file.size) }}</span>
              </div>
              <div class="file-actions">
                <el-tag v-if="item.status === 'success'" type="success">成功</el-tag>
                <el-tag v-else-if="item.status === 'error'" type="danger">
                  <el-tooltip :content="item.errorMsg || '上传失败'" :popper-style="{ maxWidth: '300px' }">
                    <span>失败</span>
                  </el-tooltip>
                </el-tag>
                <el-tag v-else-if="item.status === 'uploading'" type="primary">上传中...</el-tag>
                <el-icon v-if="item.status === 'pending'"
                      style="cursor: pointer; color: #999; font-size: 18px;"
                      @click="removeFile(index)"><CircleClose /></el-icon>
              </div>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="uploadForm.notes" type="textarea" :rows="2" placeholder="选填，所有课件共用备注"/>
        </el-form-item>
      </el-form>
      <template #footer><div>
        <el-button @click="cancelUpload" :disabled="uploading">取消</el-button>
        <el-button type="primary"
                :loading="uploading"
                :disabled="pendingFiles.length === 0"
                @click="uploadLessons">
          上传（{{ pendingFiles.length }} 个文件）
        </el-button>
      </div></template>
    </el-dialog>
    <el-dialog v-model="showViewModal" :title="currentLesson.title" width="90%" fullscreen>
      <div class="lesson-viewer">
        <div class="viewer-toolbar">
          <el-button-group>
            <el-button :disabled="currentPage <= 1" @click="prevPage">
              <el-icon><ArrowLeft /></el-icon>
              上一页
            </el-button>
            <el-button disabled>{{ currentPage }} / {{ totalPages }}</el-button>
            <el-button :disabled="currentPage >= totalPages" @click="nextPage">
              下一页
              <el-icon><ArrowRight /></el-icon>
            </el-button>
          </el-button-group>
          
          <div style="margin-left: 20px;">
            <el-input v-model="jumpPage" placeholder="页码" style="width: 80px" @keyup.enter="jumpToPage"/>
            <el-button @click="jumpToPage" style="margin-left: 5px">跳转</el-button>
          </div>
        </div>
        
        <div class="viewer-content">
          <div v-if="currentLesson.file_type === 'pdf'" class="pdf-viewer">
            <iframe :src="getPdfUrl(currentLesson)" width="100%" height="700px"></iframe>
          </div>
          <div v-else-if="currentLesson.file_type === 'md'" class="markdown-viewer" v-html="sanitize(markdownContent)">
          </div>
          <div v-else-if="currentLesson.file_type === 'ppt' || currentLesson.file_type === 'pptx'" class="ppt-viewer">
            <div class="ppt-preview-card">
              <el-icon :size="80" color="#e67e22"><Files /></el-icon>
              <h3 style="margin: 16px 0 8px;">{{ currentLesson.title }}</h3>
              <p style="color: #808695; margin-bottom: 6px;">
                格式：{{ (currentLesson.file_type || '').toUpperCase() }}
                <span v-if="currentLesson.total_pages"> · {{ currentLesson.total_pages }} 页</span>
              </p>
              <p style="color: #c5c8ce; font-size: 13px; margin-bottom: 20px;">
                PPT/PPTX 文件暂不支持浏览器内直接预览，请下载后使用 Office 软件查看
              </p>
              <el-button type="primary" @click="downloadLesson(currentLesson)">
                <el-icon><Download /></el-icon>
                下载课件
              </el-button>
            </div>
          </div>
          <div v-else class="preview-unavailable">
            <el-icon :size="64" color="#dcdee2"><Document /></el-icon>
            <p>该格式暂不支持在线预览</p>
            <el-button type="primary" @click="downloadLesson(currentLesson)">下载查看</el-button>
          </div>
        </div>
      </div>
      
      <template #footer><div>
        <el-button @click="showViewModal = false">关闭</el-button>
      </div></template>
    </el-dialog>

    <el-dialog v-model="showAIGenerateModal" title="AI 智能出题" width="700">
      <el-form :model="aiForm" :label-width="100">
        <el-form-item label="课件">
          <p>{{ selectedLesson.title }}</p>
        </el-form-item>
        <el-form-item label="页码范围">
          <el-row :gutter="10">
            <el-col :span="11">
              <el-input-number v-model="aiForm.page_start" :min="1" :max="selectedLesson.total_pages" placeholder="起始页"/>
            </el-col>
            <el-col :span="2" style="text-align: center">-</el-col>
            <el-col :span="11">
              <el-input-number v-model="aiForm.page_end" :min="aiForm.page_start" :max="selectedLesson.total_pages" placeholder="结束页"/>
            </el-col>
          </el-row>
        </el-form-item>
        <el-form-item label="难度">
          <el-radio-group v-model="aiForm.difficulty">
            <el-radio value="Easy">简单</el-radio>
            <el-radio value="Medium">中等</el-radio>
            <el-radio value="Hard">困难</el-radio>
            <el-radio value="Mixed">混合</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="题目数量">
          <el-slider v-model="aiForm.count" :min="1" :max="10" show-input/>
        </el-form-item>
      </el-form>
      
      <template #footer><div>
        <el-button @click="showAIGenerateModal = false">取消</el-button>
        <el-button type="primary" :loading="generating" @click="submitGenerate">
          <el-icon><MagicStick /></el-icon>
          开始生成
        </el-button>
      </div></template>
    </el-dialog>
  </div>
</template>

<script>
import { markRaw } from 'vue'
import api from '@oj/api'
import { ElMessageBox } from 'element-plus'
import { Document, Upload, CircleClose, ArrowLeft, ArrowRight, Files, Download, MagicStick } from '@element-plus/icons-vue'
import time from '@/utils/time'
import { sanitize } from '@/utils/sanitize'
import { fetchFreshLessonDetail } from './lessonDetailSync'
import Pagination from '@/components/Pagination.vue'

const FILE_ICONS = {
  pdf: markRaw(Document),
  ppt: markRaw(Files),
  pptx: markRaw(Files),
  doc: markRaw(Document),
  docx: markRaw(Document),
  md: markRaw(Document)
}
const DEFAULT_FILE_ICON = markRaw(Document)

export default {
  name: 'LessonManagement',
  components: { Document, Upload, CircleClose, ArrowLeft, ArrowRight, Files, Download, MagicStick, Pagination },
  props: {
    classroomId: {
      type: String,
      required: true
    },
    isStaff: {
      type: Boolean,
      default: false
    }
  },
  computed: {
    pendingFiles () {
      return this.uploadForm.files.filter(f => f.status === 'pending')
    },
    pagedLessons () {
      const start = (this.lessonPage - 1) * this.lessonPageSize
      return this.lessons.slice(start, start + this.lessonPageSize)
    }
  },
  data () {
    return {
      lessons: [],
      loading: false,
      lessonPage: 1,
      lessonPageSize: 10,

      showUploadModal: false,
      uploadForm: {
        notes: '',
        files: []
      },
      uploading: false,

      showViewModal: false,
      currentLesson: {},
      currentPage: 1,
      totalPages: 1,
      jumpPage: '',
      markdownContent: '',

      showAIGenerateModal: false,
      selectedLesson: {},
      aiForm: {
        page_start: 1,
        page_end: 1,
        difficulty: 'Medium',
        count: 3
      },
      generating: false
    }
  },
  mounted () {
    this.getLessonList()
  },
  methods: {
    sanitize,
    getLessonList () {
      this.loading = true
      api.getLessonList(this.classroomId).then(res => {
        const payload = (res && res.data && res.data.data) || {}
        const rawList = Array.isArray(payload)
          ? payload
          : (Array.isArray(payload.results) ? payload.results : (Array.isArray(res.data.results) ? res.data.results : []))
        this.lessons = rawList.map(item => this.normalizeLesson(item))
        this.loading = false
      }).catch(() => {
        this.loading = false
      })
    },
    normalizeLesson (item = {}) {
      const fileType = item.file_type || item.lesson_type || item.type || ''
      return {
        ...item,
        file_type: fileType,
        lesson_type: item.lesson_type || fileType,
        uploaded_at: item.uploaded_at || item.create_time || item.created_at,
        linked_problems_count: item.linked_problems_count != null ? item.linked_problems_count : (item.problem_count || 0)
      }
    },
    async resolveLessonDetail (lesson) {
      const normalizedLesson = this.normalizeLesson(lesson)
      const { freshLesson, lessons } = await fetchFreshLessonDetail({
        api,
        classroomId: this.classroomId,
        lesson: normalizedLesson,
        lessons: this.lessons,
        normalizeLesson: this.normalizeLesson
      })
      this.lessons = lessons
      return freshLesson
    },

    handleBeforeUpload (file) {
      const allowedFormats = ['pdf', 'ppt', 'pptx', 'doc', 'docx', 'md']
      const ext = file.name.split('.').pop().toLowerCase()
      if (!allowedFormats.includes(ext)) {
        this.$error(`文件 "${file.name}" 格式不支持，请上传 PDF/PPT/Word/Markdown 文件`)
        return false
      }
      if (file.size > 102400 * 1024) {
        this.$error(`文件 "${file.name}" 超过 100MB 限制`)
        return false
      }
      const exists = this.uploadForm.files.some(f => f.file.name === file.name && f.file.size === file.size)
      if (exists) {
        this.$warning(`文件 "${file.name}" 已在列表中`)
        return false
      }
      this.uploadForm.files.push({ file, status: 'pending', errorMsg: '' })
      return false
    },

    removeFile (index) {
      this.uploadForm.files.splice(index, 1)
    },

    async uploadLessons () {
      const pending = this.uploadForm.files.filter(f => f.status === 'pending')
      if (pending.length === 0) {
        this.$warning('没有待上传的文件')
        return
      }

      this.uploading = true
      let successCount = 0
      let failCount = 0

      for (const item of pending) {
        item.status = 'uploading'
        const formData = new FormData()
        formData.append('file', item.file)
        formData.append('title', item.file.name)
        formData.append('notes', this.uploadForm.notes)

        try {
          await api.uploadLesson(this.classroomId, formData)
          item.status = 'success'
          successCount++
        } catch (err) {
          item.status = 'error'
          item.errorMsg = (err && err.response && err.response.data && (err.response.data.error || err.response.data.data)) || '上传失败'
          failCount++
        }
      }

      this.uploading = false
      this.getLessonList()

      if (failCount === 0) {
        this.$success(`全部 ${successCount} 个课件上传成功`)
        this.showUploadModal = false
        this.uploadForm = { notes: '', files: [] }
      } else {
        this.$warning(`${successCount} 个成功，${failCount} 个失败`)
      }
    },

    cancelUpload () {
      this.showUploadModal = false
      this.uploadForm = { notes: '', files: [] }
    },

    formatFileSize (bytes) {
      if (bytes < 1024) return bytes + ' B'
      if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
      return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
    },

    getFileExt (name) {
      const ext = (name || '').split('.').pop().toLowerCase()
      return ext
    },

    getFileIconComponent (fileType) {
      return FILE_ICONS[fileType] || DEFAULT_FILE_ICON
    },

    async viewLesson (lesson) {
      try {
        const freshLesson = await this.resolveLessonDetail(lesson)
        this.currentLesson = freshLesson
        this.currentPage = 1
        this.totalPages = freshLesson.total_pages || 1
        this.showViewModal = true

        if (freshLesson.file_type === 'md') {
          this.loadMarkdownContent(freshLesson)
        }
      } catch (error) {
        this.$error('课件详情加载失败，请刷新后重试')
      }
    },

    loadMarkdownContent (lesson) {
      this.markdownContent = '<p>Markdown 内容加载中...</p>'
      api.getLesson(this.classroomId, lesson.id).then(res => {
        const data = res.data.data
        if (data.content) {
          this.markdownContent = data.content
        } else {
          this.markdownContent = '<p>该 Markdown 文件暂无在线预览内容，请下载查看。</p>'
        }
      }).catch(() => {
        this.markdownContent = '<p>内容加载失败，请尝试下载查看。</p>'
      })
    },

    prevPage () {
      if (this.currentPage > 1) {
        this.currentPage--
      }
    },

    nextPage () {
      if (this.currentPage < this.totalPages) {
        this.currentPage++
      }
    },

    jumpToPage () {
      const page = parseInt(this.jumpPage)
      if (page >= 1 && page <= this.totalPages) {
        this.currentPage = page
        this.jumpPage = ''
      }
    },

    async generateProblems (lesson) {
      try {
        const freshLesson = await this.resolveLessonDetail(lesson)
        this.selectedLesson = freshLesson
        this.aiForm.page_start = 1
        this.aiForm.page_end = freshLesson.total_pages || 1
        this.showAIGenerateModal = true
      } catch (error) {
        this.$error('课件详情加载失败，请刷新后重试')
      }
    },

    submitGenerate () {
      this.generating = true
      const data = {
        lesson_id: this.selectedLesson.id,
        page_start: this.aiForm.page_start,
        page_end: this.aiForm.page_end,
        difficulty: this.aiForm.difficulty,
        count: this.aiForm.count
      }

      api.generateProblemFromLesson(this.classroomId, data).then(res => {
        this.$success('题目生成任务已提交，请稍后查看')
        this.showAIGenerateModal = false
        this.generating = false
      }).catch(() => {
        this.generating = false
      })
    },

    downloadLesson (lesson) {
      window.open(`/api/classroom/${this.classroomId}/lessons/${lesson.id}/download/`, '_blank')
    },

    deleteLesson (lesson) {
      api.deleteLesson(this.classroomId, lesson.id).then(() => {
        this.$success('删除成功')
        this.getLessonList()
      })
    },

    confirmDelete (lesson) {
      ElMessageBox.confirm(
        `<p>确定要删除课件 <b>${lesson.title}</b> 吗？</p><p style="color: #ed4014; margin-top: 8px;">此操作无法撤销。</p>`,
        '确认删除',
        {
          confirmButtonText: '删除',
          cancelButtonText: '取消',
          dangerouslyUseHTMLString: true,
          type: 'warning'
        }
      ).then(() => {
        api.deleteLesson(this.classroomId, lesson.id).then(() => {
          this.$success('删除成功')
          this.getLessonList()
        })
      }).catch(() => {})
    },

    getPdfUrl (lesson) {
      return `/api/classroom/${this.classroomId}/lessons/${lesson.id}/view/?page=${this.currentPage}`
    },

    formatTime (timeStr) {
      return time.utcToLocal(timeStr, 'YYYY-MM-DD HH:mm')
    }
  }
}
</script>

<style lang="less" scoped>
.lesson-management {
  :deep(.el-card__header) {
    padding: 10px 16px;
  }

  .card-header {
    display: flex;
    align-items: center;
    justify-content: space-between;

    .card-title {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      font-size: 14px;
      font-weight: 600;
      color: #17233d;
    }
  }

  .file-format-label {
    display: inline-block;
    padding: 2px 8px;
    font-size: 12px;
    font-weight: 500;
    color: #409eff;
    background: rgba(64, 158, 255, 0.1);
    border-radius: 4px;
    white-space: nowrap;
  }

  .upload-file-list {
    max-height: 240px;
    overflow-y: auto;
    border: 1px solid #e8eaec;
    border-radius: 4px;
    padding: 4px 0;
  }

  .upload-file-item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 6px 12px;
    transition: background-color 0.2s;

    &:hover {
      background-color: #f8f8f9;
    }

    .file-info {
      display: flex;
      align-items: center;
      flex: 1;
      min-width: 0;

      .file-name {
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
        max-width: 300px;
        color: #515a6e;
      }

      .file-size {
        margin-left: 8px;
        color: #c5c8ce;
        font-size: 12px;
        flex-shrink: 0;
      }
    }

    .file-actions {
      flex-shrink: 0;
      margin-left: 12px;
    }
  }

  .lesson-viewer {
    .viewer-toolbar {
      display: flex;
      align-items: center;
      margin-bottom: 15px;
      padding-bottom: 15px;
      border-bottom: 1px solid #e8eaec;
    }
    
    .viewer-content {
      .pdf-viewer {
        border: 1px solid #dcdee2;
        border-radius: 4px;
        overflow: hidden;
      }
      
      .markdown-viewer {
        padding: 20px;
        background: #fff;
        border: 1px solid #dcdee2;
        border-radius: 4px;
        min-height: 500px;
      }
      
      .ppt-viewer {
        display: flex;
        justify-content: center;
        align-items: center;
        min-height: 500px;

        .ppt-preview-card {
          text-align: center;
          padding: 60px 40px;
          background: #f8f8f9;
          border: 2px dashed #dcdee2;
          border-radius: 12px;
          max-width: 480px;

          h3 {
            font-size: 18px;
            color: #2c3e50;
          }
        }
      }

      .preview-unavailable {
        text-align: center;
        padding: 100px 0;
        
        p {
          margin: 20px 0;
          color: #808695;
        }
      }
    }
  }
}
</style>
