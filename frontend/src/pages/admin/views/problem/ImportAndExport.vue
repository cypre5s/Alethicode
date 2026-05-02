<template>
  <div class="view">
    <panel title="题目导入导出">
      <div class="filter-toolbar">
        <el-input
          v-model="keyword"
          size="small"
          class="keyword-input"
          prefix-icon="el-icon-search"
          placeholder="搜索题目标题或展示 ID">
        </el-input>
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
        <el-button size="small" icon="el-icon-refresh" @click="refreshList" :loading="loadingProblems">刷新</el-button>
      </div>
      <el-table :data="problems"
                v-loading="loadingProblems" @selection-change="handleSelectionChange">
        <el-table-column
          type="selection"
          width="60">
        </el-table-column>
        <el-table-column
          label="ID"
          width="100"
          prop="id"
          align="center">
        </el-table-column>
        <el-table-column
          label="展示 ID"
          width="200"
          prop="_id"
          align="center">
        </el-table-column>
        <el-table-column
          label="标题"
          prop="title"
          align="center">
        </el-table-column>
        <el-table-column
          prop="created_by.username"
          label="作者"
          align="center">
        </el-table-column>
        <el-table-column
          prop="create_time"
          label="创建时间"
          align="center">
          <template #default="scope">
            {{ localtime(scope.row.create_time) }}
          </template>
        </el-table-column>
      </el-table>

      <div class="panel-options">
        <div class="action-bar">
          <el-button
            type="primary"
            size="small"
            icon="el-icon-download"
            :disabled="!selected_problems.length"
            @click="exportProblems">
            导出题目{{ selected_problems.length ? ` (${selected_problems.length})` : '' }}
          </el-button>

          <el-divider direction="vertical" />

          <el-upload
            ref="importUpload"
            action="/api/admin/import-problems"
            name="file"
            :data="uploadData"
            :show-file-list="false"
            :with-credentials="true"
            :limit="1"
            :on-change="handleImportFile"
            :auto-upload="false"
            accept=".zip"
            class="inline-upload">
            <template #trigger>
              <el-button
                size="small"
                type="success"
                icon="el-icon-upload2"
                :loading="loadingImporting">
                导入题目
              </el-button>
            </template>
          </el-upload>

          <el-tooltip content="未提供知识点字段时自动根据标签关联" placement="top">
            <div class="auto-kc-inline">
              <el-switch v-model="autoKC" size="small" />
              <span class="auto-kc-label">自动关联知识点</span>
            </div>
          </el-tooltip>

          <el-tooltip content="查看导入格式说明" placement="top">
            <el-button text circle size="small" class="help-btn" @click="showImportHelp = true">?</el-button>
          </el-tooltip>
        </div>

        <AdminPagination
          :total="total"
          :current-page="page"
          :page-size="limit"
          :page-sizes="[10, 20, 50, 100]"
          @update:currentPage="page = $event"
          @update:pageSize="limit = $event"
          @change="handlePaginationChange">
        </AdminPagination>
      </div>
    </panel>

    <el-dialog v-model="showImportHelp" title="题目包导入格式说明" width="680px">
      <div class="import-help-content">
        <h4>支持格式</h4>
        <p>ZIP 压缩包，包含以下结构：</p>
        <pre><code>problem-pack.zip
├── problem.json          # 题目元数据（必须）
├── 1/                    # 测试点目录
│   ├── 1.in              # 输入文件
│   └── 1.out             # 期望输出
├── 2/
│   ├── 1.in
│   └── 1.out
└── ...</code></pre>

        <h4>problem.json 字段</h4>
        <table class="help-table">
          <thead>
            <tr><th>字段</th><th>类型</th><th>必须</th><th>说明</th></tr>
          </thead>
          <tbody>
            <tr><td>title</td><td>string</td><td>是</td><td>题目标题</td></tr>
            <tr><td>description</td><td>string</td><td>是</td><td>题目描述（HTML）</td></tr>
            <tr><td>input_description</td><td>string</td><td>否</td><td>输入说明</td></tr>
            <tr><td>output_description</td><td>string</td><td>否</td><td>输出说明</td></tr>
            <tr><td>difficulty</td><td>string</td><td>否</td><td>Low / Mid / High</td></tr>
            <tr><td>tags</td><td>array</td><td>否</td><td>知识点标签</td></tr>
            <tr><td>time_limit</td><td>int</td><td>否</td><td>时间限制（ms），默认 1000</td></tr>
            <tr><td>memory_limit</td><td>int</td><td>否</td><td>内存限制（MB），默认 256</td></tr>
            <tr><td>samples</td><td>array</td><td>否</td><td>示例输入输出</td></tr>
          </tbody>
        </table>

        <h4>注意事项</h4>
        <ul>
          <li>ZIP 文件大小不超过 100MB</li>
          <li>每个测试点的输入输出文件必须成对存在</li>
          <li>开启"自动关联知识点"后，系统会根据标签自动关联知识点</li>
        </ul>
      </div>
    </el-dialog>

  </div>
</template>
<script>
  import api from '@admin/api'
  import utils from '@/utils/utils'
  import { utcToLocal } from '@/utils/time'
  import {
    appendLanguagePackQuery,
    normalizeLanguagePackId,
    resolveCurrentLanguagePackId
  } from '@admin/utils/languagePackContext'

  export default {
    name: 'ImportAndExport',
    data () {
      return {
        autoKC: true,
        page: 1,
        limit: 10,
        total: 0,
        loadingProblems: false,
        loadingImporting: false,
        keyword: '',
        selectedLanguagePackId: '',
        languagePackOptions: [],
        problems: [],
        selected_problems: [],
        showImportHelp: false
      }
    },
    computed: {
      uploadData () {
        return {
          auto_kc: String(this.autoKC),
          language_pack_id: this.selectedLanguagePackId
        }
      }
    },
    mounted () {
      this.keyword = this.$route.query.keyword || ''
      this.loadLanguagePackOptions()
    },
    methods: {
      localtime: utcToLocal,
      loadLanguagePackOptions () {
        api.getVisibleLanguagePacks().then(res => {
          this.languagePackOptions = res.data.data || []
          this.selectedLanguagePackId = resolveCurrentLanguagePackId(this.$route.query.language_pack_id, this.languagePackOptions)
          this.syncLanguagePackRoute(true)
          this.getProblems(this.page)
        }).catch(() => {
          this.languagePackOptions = []
          this.selectedLanguagePackId = ''
          this.problems = []
          this.total = 0
        })
      },
      handleSelectionChange (val) {
        this.selected_problems = val
      },
      getProblems (page = 1) {
        this.page = page
        if (!this.selectedLanguagePackId) {
          this.problems = []
          this.total = 0
          this.loadingProblems = false
          return
        }
        let params = {
          keyword: this.keyword,
          offset: (page - 1) * this.limit,
          limit: this.limit,
          language_pack_id: this.selectedLanguagePackId
        }
        this.loadingProblems = true
        api.getProblemList(params).then(res => {
          this.problems = res.data.data.results
          this.total = res.data.data.total
          this.loadingProblems = false
        }).catch(() => {
          this.loadingProblems = false
        })
      },
      handleLanguagePackChange () {
        this.selectedLanguagePackId = normalizeLanguagePackId(this.selectedLanguagePackId)
        this.syncLanguagePackRoute()
        this.getProblems(1)
      },
      resetFilters () {
        this.keyword = ''
        this.selected_problems = []
        this.syncLanguagePackRoute()
        this.getProblems(1)
      },
      refreshList () {
        this.getProblems(this.page)
      },
      handlePaginationChange ({ page, pageSize }) {
        this.page = page
        this.limit = pageSize
        this.getProblems(page)
      },
      exportProblems () {
        let params = []
        for (let p of this.selected_problems) {
          params.push('problem_id=' + p.id)
        }
        let url = '/admin/export-problems?' + params.join('&')
        utils.downloadFile(url)
      },
      handleImportFile (file) {
        if (!file || file.status !== 'ready') return
        if (!this.selectedLanguagePackId) {
          this.$error('请先选择课程内容包')
          this.$refs.importUpload.clearFiles()
          return
        }
        if (!file.name.toLowerCase().endsWith('.zip')) {
          this.$error('仅支持 ZIP 格式的题目包')
          this.$refs.importUpload.clearFiles()
          return
        }
        this.loadingImporting = true
        this.$refs.importUpload.submit()
      },
      uploadSucceeded (response) {
        this.loadingImporting = false
        this.$refs.importUpload.clearFiles()
        if (response.error) {
          this.$error(response.data)
        } else {
          const d = response.data
          let msg = `成功导入 ${d.import_count} 道题目`
          if (d.kc_bindcount > 0) {
            msg += `，其中 ${d.kc_bindcount} 条为手动知识点关联`
          }
          if (d.kc_auto_bindcount > 0) {
            msg += `，${d.kc_auto_bindcount} 条为自动知识点关联`
          }
          this.$success(msg)
          this.getProblems()
        }
      },
      uploadFailed () {
        this.loadingImporting = false
        this.$refs.importUpload.clearFiles()
        this.$error('导入失败')
      },
      syncLanguagePackRoute (replace = false) {
        const nextQuery = appendLanguagePackQuery(
          { keyword: this.keyword || '' },
          this.selectedLanguagePackId
        )
        const currentQuery = appendLanguagePackQuery(
          { keyword: this.$route.query.keyword || '' },
          this.$route.query.language_pack_id
        )
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
    },
    watch: {
      'keyword' () {
        this.syncLanguagePackRoute()
        this.getProblems(1)
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
  margin-bottom: 12px;
}

.keyword-input {
  width: 260px;
}

.language-pack-select {
  width: 220px;
}

.action-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.inline-upload {
  display: inline-flex;
}

.auto-kc-inline {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0 4px;
  cursor: pointer;
}

.auto-kc-label {
  font-size: 12px;
  color: #606266;
  white-space: nowrap;
  user-select: none;
}

.help-btn {
  font-weight: 700;
  color: #909399;
}

.import-help-content {
  h4 { font-size: 15px; font-weight: 600; margin: 18px 0 8px; color: #1f2937; &:first-child { margin-top: 0; } }
  p { color: #4b5563; font-size: 13px; line-height: 1.6; margin: 0 0 8px; }
  pre { background: #f1f5f9; padding: 12px 16px; border-radius: 8px; overflow-x: auto; font-size: 12px; line-height: 1.5; }
  code { font-family: 'JetBrains Mono', Menlo, monospace; }
  ul { padding-left: 20px; color: #4b5563; font-size: 13px; li { margin-bottom: 4px; } }
}
.help-table {
  width: 100%; border-collapse: collapse; font-size: 13px; margin: 8px 0;
  th, td { padding: 6px 10px; border: 1px solid #e5e7eb; text-align: left; }
  th { background: #f9fafb; font-weight: 600; color: #374151; }
  td { color: #4b5563; }
}
</style>
