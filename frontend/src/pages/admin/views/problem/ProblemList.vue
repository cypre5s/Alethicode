<template>
  <div class="view">
    <Panel :title="$t('m.Problem_List')">
      <template #header><div class="filter-toolbar">
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
        <el-button size="small" icon="el-icon-refresh" @click="refreshList" :loading="loading">刷新</el-button>
      </div></template>
      <el-table
        v-loading="loading"
        element-loading-text="加载中"
        ref="table"
        :data="problemList"
        @row-dblclick="handleDblclick"
        style="width: 100%">
        <el-table-column
          width="100"
          prop="id"
          label="ID"
          align="center">
        </el-table-column>
        <el-table-column
          width="150"
          label="展示 ID"
          align="center">
          <template #default="{row}">
            <span v-show="!row.isEditing">{{row._id}}</span>
            <el-input v-show="row.isEditing" v-model="row._id"
                      @keyup.enter="handleInlineEdit(row)">

            </el-input>
          </template>
        </el-table-column>
        <el-table-column
          prop="title"
          label="标题"
          align="center">
          <template #default="{row}">
            <span v-show="!row.isEditing">{{row.title}}</span>
            <el-input v-show="row.isEditing" v-model="row.title"
                      @keyup.enter="handleInlineEdit(row)">
            </el-input>
          </template>
        </el-table-column>
        <el-table-column
          prop="created_by.username"
          label="作者"
          align="center">
        </el-table-column>
        <el-table-column
          width="200"
          prop="create_time"
          label="创建时间"
          align="center">
          <template #default="scope">
            {{ localtime(scope.row.create_time) }}
          </template>
        </el-table-column>
        <el-table-column
          width="100"
          prop="visible"
          label="可见"
          align="center">
          <template #default="scope">
            <el-switch v-model="scope.row.visible"
                       active-text=""
                       inactive-text=""
                       @change="updateProblem(scope.row)">
            </el-switch>
          </template>
        </el-table-column>
        <el-table-column
          fixed="right"
          label="操作"
          width="250"
          align="center">
          <template #default="scope">
            <icon-btn name="编辑" icon="edit" @click="goEdit(scope.row.id)"></icon-btn>
            <icon-btn icon="download" name="下载测试数据"
                      @click="downloadTestCase(scope.row.id)"></icon-btn>
            <icon-btn icon="trash" name="删除题目"
                      @click="deleteProblem(scope.row.id)"></icon-btn>
          </template>
        </el-table-column>
      </el-table>
      <div class="panel-options">
        <el-button type="primary" size="small"
                   @click="goCreateProblem" icon="el-icon-plus">新建题目
        </el-button>
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
    <el-dialog title="确认更新题目"
               width="20%"
               v-model="InlineEditDialogVisible"
               @close-on-click-modal="false">
      <div>
        <p>展示 ID：{{currentRow._id}}</p>
        <p>标题：{{currentRow.title}}</p>
      </div>
      <template #footer>
        <cancel @click="InlineEditDialogVisible = false; getProblemList(currentPage)"></cancel>
        <save @click="updateProblem(currentRow)"></save>
      </template>
    </el-dialog>
  </div>
</template>

<script>
  import api from '../../api.js'
  import utils from '@/utils/utils'
  import { utcToLocal } from '@/utils/time'
  import {
    appendLanguagePackQuery,
    normalizeLanguagePackId,
    resolveCurrentLanguagePackId
  } from '@admin/utils/languagePackContext'

  export default {
    name: 'ProblemList',
    data () {
      return {
        pageSize: 10,
        total: 0,
        problemList: [],
        keyword: '',
        selectedLanguagePackId: '',
        languagePackOptions: [],
        loading: false,
        currentPage: 1,
        currentRow: {},
        InlineEditDialogVisible: false
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
          this.getProblemList(this.currentPage)
        }).catch(() => {
          this.languagePackOptions = []
          this.selectedLanguagePackId = ''
          this.problemList = []
          this.total = 0
        })
      },
      handleDblclick (row) {
        row.isEditing = true
      },
      goEdit (problemId) {
        this.$router.push({
          name: 'edit-problem',
          params: { problemId },
          query: appendLanguagePackQuery({}, this.selectedLanguagePackId)
        })
      },
      goCreateProblem () {
        this.$router.push({
          name: 'create-problem',
          query: appendLanguagePackQuery({}, this.selectedLanguagePackId)
        })
      },
      handlePaginationChange ({ page, pageSize }) {
        this.currentPage = page
        this.pageSize = pageSize
        this.getProblemList(page)
      },
      handleLanguagePackChange () {
        this.selectedLanguagePackId = normalizeLanguagePackId(this.selectedLanguagePackId)
        this.currentPage = 1
        this.syncLanguagePackRoute()
        this.getProblemList(1)
      },
      resetFilters () {
        this.keyword = ''
        this.currentPage = 1
        this.syncLanguagePackRoute()
        this.getProblemList(1)
      },
      refreshList () {
        this.getProblemList(this.currentPage)
      },
      getProblemList (page = 1) {
        if (!this.selectedLanguagePackId) {
          this.problemList = []
          this.total = 0
          this.loading = false
          return
        }
        this.loading = true
        let params = {
          limit: this.pageSize,
          offset: (page - 1) * this.pageSize,
          keyword: this.keyword,
          language_pack_id: this.selectedLanguagePackId
        }
        api.getProblemList(params).then(res => {
          this.loading = false
          this.total = res.data.data.total
          for (let problem of res.data.data.results) {
            problem.isEditing = false
          }
          this.problemList = res.data.data.results
        }, res => {
          this.loading = false
        })
      },
      deleteProblem (id) {
        this.$confirm('确认删除这道题目？相关提交记录也会一并删除。', '删除题目', {
          type: 'warning'
        }).then(() => {
          api.deleteProblem(id).then(() => {
            const nextPage = this.currentPage > 1 ? this.currentPage - 1 : 1
            this.getProblemList(nextPage)
          }).catch(() => {})
        }).catch(() => {})
      },
      updateProblem (row) {
        let data = Object.assign({}, row)
        data.language_pack_id = this.selectedLanguagePackId
        api.editProblem(data).then(res => {
          this.InlineEditDialogVisible = false
          this.getProblemList(this.currentPage)
        }).catch(() => {
          this.InlineEditDialogVisible = false
        })
      },
      handleInlineEdit (row) {
        this.currentRow = row
        this.InlineEditDialogVisible = true
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
      },
      downloadTestCase (problemID) {
        let url = '/admin/test-cases?problem_id=' + problemID
        utils.downloadFile(url)
      }
    },
    watch: {
      'keyword' () {
        this.currentPage = 1
        this.syncLanguagePackRoute()
        this.getProblemList(1)
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

.keyword-input {
  width: 260px;
}

.language-pack-select {
  width: 220px;
}
</style>
