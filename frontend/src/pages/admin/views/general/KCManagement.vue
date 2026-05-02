<template>
  <div class="view">
    <Panel title="知识图谱管理">
      <template #header><div class="filter-toolbar">
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
        <el-button size="small" icon="el-icon-refresh" @click="fetchKCList" :loading="loading">
          刷新
        </el-button>
      </div></template>

      <el-tabs v-model="activeChapter" @tab-click="onChapterTab">
        <el-tab-pane label="全部" name="all"></el-tab-pane>
        <el-tab-pane v-for="ch in chapters" :key="ch.value" :label="ch.label" :name="ch.value"></el-tab-pane>
      </el-tabs>

      <el-table
        v-loading="loading"
        element-loading-text="加载中"
        :data="kcList"
        stripe
        style="width: 100%">

        <el-table-column type="index" label="编号" width="70" align="center"></el-table-column>
        <el-table-column prop="name" label="名称" width="150" align="center"></el-table-column>
        <el-table-column prop="name_en" label="英文标识" width="160" align="center">
          <template #default="scope">
            <span class="mono-text">{{ scope.row.name_en }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="chapter" label="章节" width="70" align="center"></el-table-column>
        <el-table-column prop="description" label="描述" show-overflow-tooltip align="center"></el-table-column>
        <el-table-column prop="problem_count" label="关联题目" width="90" align="center"></el-table-column>
        <el-table-column label="平均掌握度" width="110" align="center">
          <template #default="scope">
            <span :style="{ color: masteryColor(scope.row.avg_mastery), fontWeight: '600' }">
              {{ (scope.row.avg_mastery * 100).toFixed(1) }}%
            </span>
          </template>
        </el-table-column>
        <el-table-column fixed="right" label="操作" width="170" align="center">
          <template #default="scope">
            <el-button size="small" type="primary" plain @click="openEdit(scope.row)">编辑</el-button>
            <el-button size="small" type="info" plain @click="showProblems(scope.row)" style="margin-left: 8px;">关联题目</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <AdminPagination
          :total="total"
          :current-page="page"
          :page-size="pageSize"
          :page-sizes="[20, 50, 100]"
          @update:currentPage="page = $event"
          @update:pageSize="pageSize = $event"
          @change="handlePaginationChange">
        </AdminPagination>
      </div>
    </Panel>

    <el-dialog title="编辑知识图谱" v-model="editVisible" width="520px">
      <el-form :model="editForm" label-width="100px">
        <el-form-item label="名称">
          <el-input v-model="editForm.name"></el-input>
        </el-form-item>
        <el-form-item label="英文标识">
          <el-input :value="editForm.name_en" disabled></el-input>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="editForm.description" type="textarea" :rows="3"></el-input>
        </el-form-item>
        <el-form-item label="初始掌握率 p_init">
          <el-input-number v-model="editForm.p_init" :min="0" :max="1" :step="0.05" :precision="2" size="small"></el-input-number>
        </el-form-item>
        <el-form-item label="迁移概率 p_transit">
          <el-input-number v-model="editForm.p_transit" :min="0" :max="1" :step="0.05" :precision="2" size="small"></el-input-number>
        </el-form-item>
        <el-form-item label="失误率 p_slip">
          <el-input-number v-model="editForm.p_slip" :min="0" :max="1" :step="0.05" :precision="2" size="small"></el-input-number>
        </el-form-item>
        <el-form-item label="猜对率 p_guess">
          <el-input-number v-model="editForm.p_guess" :min="0" :max="1" :step="0.05" :precision="2" size="small"></el-input-number>
        </el-form-item>
      </el-form>
      <template #footer><div>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" @click="saveEdit" :loading="saving">保存</el-button>
      </div></template>
    </el-dialog>

    <el-dialog :title="'关联题目 — ' + (currentKC ? currentKC.name : '')" v-model="problemsVisible" width="600px">
      <el-table :data="problemList" v-loading="loadingProblems" stripe size="small" style="width: 100%">
        <el-table-column prop="display_id" label="展示 ID" width="120" align="center"></el-table-column>
        <el-table-column prop="title" label="标题" align="center"></el-table-column>
        <el-table-column prop="weight" label="权重" width="80" align="center"></el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script>
import api from '@admin/api'
import {
  appendLanguagePackQuery,
  normalizeLanguagePackId,
  resolveCurrentLanguagePackId
} from '@admin/utils/languagePackContext'

export default {
  name: 'KCManagement',
  data () {
    return {
      activeChapter: 'all',
      chapters: [],
      kcList: [],
      selectedLanguagePackId: '',
      languagePackOptions: [],
      total: 0,
      page: 1,
      pageSize: 20,
      loading: false,
      editVisible: false,
      saving: false,
      editForm: {
        id: null,
        name: '',
        name_en: '',
        description: '',
        p_init: 0.1,
        p_transit: 0.15,
        p_slip: 0.1,
        p_guess: 0.2
      },
      problemsVisible: false,
      currentKC: null,
      problemList: [],
      loadingProblems: false
    }
  },
  mounted () {
    this.loadLanguagePackOptions()
  },
  methods: {
    loadLanguagePackOptions () {
      api.getPublishedLanguagePacks().then(res => {
        this.languagePackOptions = res.data.data || []
        this.selectedLanguagePackId = resolveCurrentLanguagePackId(this.$route.query.language_pack_id, this.languagePackOptions)
        this.syncLanguagePackRoute(true)
        this.fetchChapters()
        this.fetchKCList()
      }).catch(() => {
        this.languagePackOptions = []
        this.selectedLanguagePackId = ''
        this.chapters = []
        this.kcList = []
        this.total = 0
      })
    },
    masteryColor (val) {
      if (val >= 0.7) return '#67C23A'
      if (val >= 0.3) return '#E6A23C'
      if (val > 0) return '#F56C6C'
      return '#909399'
    },
    fetchChapters () {
      if (!this.selectedLanguagePackId) {
        this.chapters = []
        return
      }
      const params = { page: 1, page_size: 500 }
      params.language_pack_id = this.selectedLanguagePackId
      api.getKCList(params).then(res => {
        const rows = (res.data && res.data.data && res.data.data.results) || []
        const chSet = new Set()
        rows.forEach(r => { if (r.chapter) chSet.add(String(r.chapter)) })
        const sorted = Array.from(chSet).sort((a, b) => {
          const na = parseInt(a), nb = parseInt(b)
          if (!isNaN(na) && !isNaN(nb)) return na - nb
          return a.localeCompare(b)
        })
        this.chapters = sorted.map(c => {
          const n = parseInt(c)
          const label = !isNaN(n) ? '第' + '一二三四五六七八九十'[n - 1] + '章' : c
          return { value: c, label: label || c }
        })
      }).catch(() => {})
    },
    fetchKCList () {
      if (!this.selectedLanguagePackId) {
        this.kcList = []
        this.total = 0
        this.loading = false
        return
      }
      this.loading = true
      let params = { page: this.page, page_size: this.pageSize, language_pack_id: this.selectedLanguagePackId }
      if (this.activeChapter && this.activeChapter !== 'all') {
        params.chapter = this.activeChapter
      }
      api.getKCList(params).then(res => {
        this.kcList = res.data.data.results
        this.total = res.data.data.total
        this.loading = false
      }).catch(() => {
        this.loading = false
      })
    },
    onChapterTab () {
      this.page = 1
      this.fetchKCList()
    },
    handleLanguagePackChange () {
      this.selectedLanguagePackId = normalizeLanguagePackId(this.selectedLanguagePackId)
      this.activeChapter = 'all'
      this.page = 1
      this.syncLanguagePackRoute()
      this.fetchChapters()
      this.fetchKCList()
    },
    resetFilters () {
      this.activeChapter = 'all'
      this.page = 1
      this.syncLanguagePackRoute()
      this.fetchChapters()
      this.fetchKCList()
    },
    handlePaginationChange ({ page, pageSize }) {
      this.page = page
      this.pageSize = pageSize
      this.fetchKCList()
    },
    openEdit (row) {
      this.editForm = {
        id: row.id,
        name: row.name,
        name_en: row.name_en,
        description: row.description,
        p_init: row.p_init,
        p_transit: row.p_transit,
        p_slip: row.p_slip,
        p_guess: row.p_guess
      }
      this.editVisible = true
    },
    saveEdit () {
      this.saving = true
      api.updateKC(this.editForm.id, {
        name: this.editForm.name,
        description: this.editForm.description,
        p_init: this.editForm.p_init,
        p_transit: this.editForm.p_transit,
        p_slip: this.editForm.p_slip,
        p_guess: this.editForm.p_guess
      }).then(() => {
        this.saving = false
        this.editVisible = false
        this.$success('保存成功')
        this.fetchKCList()
      }).catch(() => {
        this.saving = false
      })
    },
    showProblems (row) {
      this.currentKC = row
      this.problemsVisible = true
      this.loadingProblems = true
      api.getKCProblems(row.id).then(res => {
        this.problemList = res.data.data.results
        this.loadingProblems = false
      }).catch(() => {
        this.loadingProblems = false
      })
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
.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 20px;
  padding-bottom: 8px;
}
</style>
