<template>
  <div class="view">
    <Panel title="AI 变体题审核">
      <template #header><div class="filter-toolbar">
        <el-tag v-if="total > 0" type="warning" size="small" effect="dark">
          {{ total }} 道待审核
        </el-tag>
        <el-tag v-else type="success" size="small" effect="dark">
          暂无待审核
        </el-tag>
        <el-select
          v-model="selectedLanguagePackId"
          size="small"
          class="language-pack-select"
          placeholder="请选择课程内容包"
          @change="handleLanguagePackChange">
          <el-option
            v-for="pack in languagePackOptions"
            :key="pack.id"
            :label="pack.name + ' v' + pack.version"
            :value="String(pack.id)">
          </el-option>
        </el-select>
        <el-button size="small" @click="resetFilters">重置</el-button>
        <el-button size="small" icon="el-icon-refresh" @click="fetchList" :loading="loading">刷新</el-button>
      </div></template>

      <el-table
        v-loading="loading"
        element-loading-text="加载中"
        :data="list"
        :header-cell-style="{ textAlign: 'center' }"
        style="width: 100%">

        <el-table-column prop="display_id" label="展示 ID" width="160">
          <template #default="scope">
            <span class="mono-text">{{ scope.row.display_id }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="title" label="标题" min-width="200">
          <template #default="scope">
            <span>{{ scope.row.title }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="source_problem_id" label="原题" width="160">
          <template #default="scope">
            <el-tooltip v-if="scope.row.source_problem_title"
                        :content="scope.row.source_problem_title" placement="top">
              <el-tag size="small" effect="plain">{{ scope.row.source_problem_id }}</el-tag>
            </el-tooltip>
            <span v-else>—</span>
          </template>
        </el-table-column>

        <el-table-column prop="difficulty" label="难度" width="100" align="center">
          <template #default="scope">
            <el-tag size="small" :type="difficultyTag(scope.row.difficulty)">
              {{ scope.row.difficulty }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="test_case_count" label="测试点" width="90" align="center">
          <template #default="scope">
            <span>{{ scope.row.test_case_count }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="create_time" label="创建时间" width="180">
          <template #default="scope">
            {{ localtime(scope.row.create_time) }}
          </template>
        </el-table-column>

        <el-table-column fixed="right" label="操作" width="140" align="center">
          <template #default="scope">
            <el-button size="small" type="primary" plain
                       @click="openPreview(scope.row)">
              预览
            </el-button>
            <el-button size="small" type="danger"
                       @click="handleReject(scope.row)" :loading="scope.row._rejecting">
              驳回
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="panel-options">
        <AdminPagination
          :total="total"
          :current-page="currentPage"
          :page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          @update:currentPage="currentPage = $event"
          @update:pageSize="pageSize = $event"
          @change="handlePaginationChange">
        </AdminPagination>
      </div>
    </Panel>

    <el-dialog
      title="题目预览"
      v-model="previewVisible"
      width="760px"
      top="5vh"
      :close-on-click-modal="false">
      <div v-if="previewItem" class="preview-container">
        <div class="preview-header">
          <div class="preview-title">
            <span class="mono-text">{{ previewItem.display_id }}</span>
            <span class="preview-sep">—</span>
            <strong>{{ previewItem.title }}</strong>
          </div>
          <div class="preview-meta">
            <el-tag size="small" :type="difficultyTag(previewItem.difficulty)">{{ previewItem.difficulty }}</el-tag>
            <el-tag size="small" type="info">{{ previewItem.test_case_count }} 个测试点</el-tag>
            <el-tag v-if="previewItem.source_problem_id" size="small" effect="plain">
              源题: {{ previewItem.source_problem_id }}
            </el-tag>
          </div>
        </div>

        <el-divider></el-divider>

        <div class="problem-content markdown-body">
          <div v-if="previewItem.description">
            <p class="section-title">题目描述</p>
            <div class="section-body" v-html="sanitize(previewItem.description)"></div>
          </div>

          <div v-if="previewItem.input_description">
            <p class="section-title">输入描述</p>
            <div class="section-body" v-html="sanitize(previewItem.input_description)"></div>
          </div>

          <div v-if="previewItem.output_description">
            <p class="section-title">输出描述</p>
            <div class="section-body" v-html="sanitize(previewItem.output_description)"></div>
          </div>

          <div v-if="previewItem.samples && previewItem.samples.length">
            <div v-for="(sample, index) in previewItem.samples" :key="index">
              <div class="sample-block">
                <div class="sample-item">
                  <p class="section-title">样例输入 {{ index + 1 }}</p>
                  <pre class="sample-pre">{{ normalizeSample(sample.input) }}</pre>
                </div>
                <div class="sample-item">
                  <p class="section-title">样例输出 {{ index + 1 }}</p>
                  <pre class="sample-pre">{{ normalizeSample(sample.output) }}</pre>
                </div>
              </div>
            </div>
          </div>

          <div v-if="previewItem.hint">
            <p class="section-title">提示</p>
            <div class="section-body" v-html="sanitize(previewItem.hint)"></div>
          </div>
        </div>
      </div>

      <template #footer><span class="dialog-footer">
        <el-button @click="previewVisible = false">关闭</el-button>
        <el-button type="primary" icon="el-icon-edit" @click="goEdit(previewItem)">
          在编辑器中编辑
        </el-button>
      </span></template>
    </el-dialog>
  </div>
</template>

<script>
  import api from '../../api.js'
  import { utcToLocal } from '@/utils/time'
  import { sanitize } from '@/utils/sanitize'
  import {
    appendLanguagePackQuery,
    normalizeLanguagePackId,
    resolveCurrentLanguagePackId
  } from '@admin/utils/languagePackContext'

  export default {
    name: 'AIVariantReview',
    data () {
      return {
        loading: true,
        list: [],
        total: 0,
        currentPage: 1,
        pageSize: 20,
        selectedLanguagePackId: '',
        languagePackOptions: [],
        previewVisible: false,
        previewItem: null
      }
    },
    mounted () {
      this.loadLanguagePackOptions()
    },
    methods: {
      sanitize,
      localtime: utcToLocal,
      loadLanguagePackOptions () {
        api.getPublishedLanguagePacks().then(res => {
          this.languagePackOptions = res.data.data || []
          this.selectedLanguagePackId = resolveCurrentLanguagePackId(this.$route.query.language_pack_id, this.languagePackOptions)
          this.syncLanguagePackRoute(true)
          this.fetchList()
        }).catch(() => {
          this.languagePackOptions = []
          this.selectedLanguagePackId = ''
          this.list = []
          this.total = 0
        })
      },
      fetchList () {
        if (!this.selectedLanguagePackId) {
          this.list = []
          this.total = 0
          this.loading = false
          return
        }
        this.loading = true
        const params = {
          page: this.currentPage,
          limit: this.pageSize,
          language_pack_id: this.selectedLanguagePackId
        }
        api.getAIVariantList(params).then(res => {
          this.loading = false
          this.total = res.data.data.total
          this.list = res.data.data.results.map(item => {
            item._rejecting = false
            return item
          })
        }, () => {
          this.loading = false
        })
      },
      handleLanguagePackChange () {
        this.selectedLanguagePackId = normalizeLanguagePackId(this.selectedLanguagePackId)
        this.currentPage = 1
        this.syncLanguagePackRoute()
        this.fetchList()
      },
      resetFilters () {
        this.currentPage = 1
        this.syncLanguagePackRoute()
        this.fetchList()
      },
      handlePaginationChange ({ page, pageSize }) {
        this.currentPage = page
        this.pageSize = pageSize
        this.fetchList()
      },
      openPreview (item) {
        this.previewItem = item
        this.previewVisible = true
      },
      goEdit (item) {
        this.previewVisible = false
        this.$router.push({
          name: 'edit-problem',
          params: { problemId: item.id },
          query: appendLanguagePackQuery({}, this.selectedLanguagePackId)
        })
      },
      handleReject (item) {
        this.$confirm('确认驳回该 AI 变体题？驳回后将删除题目及测试数据，不可恢复。', '驳回', {
          confirmButtonText: '确认驳回',
          cancelButtonText: '取消',
          type: 'warning'
        }).then(() => {
          item._rejecting = true
          api.rejectAIVariant(item.id).then(() => {
            item._rejecting = false
            this.fetchList()
          }).catch(() => {
            item._rejecting = false
          })
        }).catch(() => {})
      },
      difficultyTag (difficulty) {
        if (!difficulty) return 'info'
        var map = { Low: 'success', Mid: '', High: 'danger' }
        return map[difficulty] !== undefined ? map[difficulty] : 'info'
      },
      normalizeSample (value) {
        if (value === null || typeof value === 'undefined') return ''
        return String(value)
          .replace(/\\r\\n/g, '\n')
          .replace(/\\n/g, '\n')
          .replace(/\\t/g, '\t')
      },
      syncLanguagePackRoute (replace = false) {
        const nextQuery = appendLanguagePackQuery({}, this.selectedLanguagePackId)
        const currentQuery = appendLanguagePackQuery({}, this.$route.query.language_pack_id)
        if (JSON.stringify(nextQuery) === JSON.stringify(currentQuery)) {
          return
        }
        const payload = { name: this.$route.name, query: nextQuery }
        if (replace) {
          this.$router.replace(payload)
          return
        }
        this.$router.push(payload)
      }
    }
  }
</script>

<style scoped lang="less">
  .filter-toolbar {
    display: flex;
    align-items: center;
    justify-content: flex-end;
    gap: 10px;
    flex-wrap: wrap;
  }

  .language-pack-select {
    width: 220px;
  }

  .mono-text {
    font-family: 'Courier New', Courier, monospace;
    font-size: 13px;
    color: #606266;
  }

  .preview-container {
    max-height: 70vh;
    overflow-y: auto;
    padding-right: 4px;
  }

  .preview-header {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 12px;
    flex-wrap: wrap;
  }

  .preview-title {
    font-size: 16px;
    line-height: 1.5;
    .preview-sep {
      margin: 0 8px;
      color: #c0c4cc;
    }
  }

  .preview-meta {
    display: flex;
    align-items: center;
    gap: 6px;
    flex-shrink: 0;
  }

  .problem-content {
    font-size: 14px;
    line-height: 1.7;
    color: #303133;

    .section-title {
      font-weight: 700;
      color: #409eff;
      margin: 18px 0 8px;
      font-size: 14px;

      &:first-child {
        margin-top: 0;
      }
    }

    .section-body {
      margin-bottom: 4px;

      :deep(p ) { margin: 4px 0; }
      :deep(pre ) {
        background: #f4f4f5;
        border: 1px solid #e4e7ed;
        border-radius: 4px;
        padding: 10px 12px;
        font-family: 'Courier New', Courier, monospace;
        font-size: 13px;
        overflow-x: auto;
        white-space: pre-wrap;
        word-break: break-all;
      }
      :deep(code ) {
        background: #f4f4f5;
        padding: 1px 4px;
        border-radius: 3px;
        font-family: 'Courier New', Courier, monospace;
        font-size: 12px;
      }
    }

    .sample-block {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 12px;
      margin-bottom: 8px;

      .sample-item {
        min-width: 0;
      }

      .sample-pre {
        background: #f4f4f5;
        border: 1px solid #e4e7ed;
        border-radius: 4px;
        padding: 10px 12px;
        font-family: 'Courier New', Courier, monospace;
        font-size: 13px;
        margin: 0;
        white-space: pre-wrap;
        word-break: break-all;
        min-height: 40px;
      }
    }
  }
</style>
