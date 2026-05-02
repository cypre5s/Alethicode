<template>
  <div class="flex-container">
    <div id="main">
      <OjPanel shadow class="submission-list-panel">
        <template #title><div class="submission-panel-title">{{title}}</div></template>
        <template #extra><div>
          <ul class="filter">
            <li>
              <el-dropdown @command="handleResultChange">
                <span class="el-dropdown-link filter-trigger">{{status}}
                  <el-icon><ArrowDown /></el-icon>
                </span>
                <template #dropdown><el-dropdown-menu>
                  <el-dropdown-item command="">{{$t('m.All')}}</el-dropdown-item>
                  <el-dropdown-item v-for="s in Object.keys(JUDGE_STATUS)" :key="s" :command="s">
                    {{$t('m.' + JUDGE_STATUS[s].name.replace(/ /g, "_"))}}
                  </el-dropdown-item>
                </el-dropdown-menu></template>
              </el-dropdown>
            </li>

            <li>
              <el-switch
                v-model="formFilter.myself"
                :active-text="$t('m.Mine')"
                :inactive-text="$t('m.All')"
                @change="handleQueryChange"
              />
            </li>
            <li>
              <el-input v-model="formFilter.username" :placeholder="$t('m.Search_Author')" @keyup.enter="handleQueryChange"/>
            </li>

            <li>
              <el-button type="primary" class="submission-refresh-btn" @click="getSubmissions">
                <el-icon><Refresh /></el-icon>
                {{$t('m.Refresh')}}
              </el-button>
            </li>
          </ul>
        </div></template>

        <el-table stripe :data="submissions" v-loading="loadingTable">
          <el-table-column :label="$t('m.When')" align="center" width="170">
            <template #default="scope">
              {{ utcToLocal(scope.row.create_time) }}
            </template>
          </el-table-column>

          <el-table-column :label="$t('m.ID')" align="center" width="150">
            <template #default="scope">
              <span v-if="scope.row.show_link"
                    style="color: #57a3f3; cursor: pointer"
                    @click="$router.push('/status/' + scope.row.id)">
                {{ scope.row.id.slice(0, 12) }}
              </span>
              <span v-else>{{ scope.row.id.slice(0, 12) }}</span>
            </template>
          </el-table-column>

          <el-table-column :label="$t('m.Status')" align="center" width="120">
            <template #default="scope">
              <el-tag
                :color="JUDGE_STATUS[scope.row.result].color"
                effect="dark"
                disable-transitions
              >
                {{ $t('m.' + JUDGE_STATUS[scope.row.result].name.replace(/ /g, '_')) }}
              </el-tag>
            </template>
          </el-table-column>

          <el-table-column :label="$t('m.Problem')" align="center" width="120">
            <template #default="scope">
              <span style="color: #57a3f3; cursor: pointer"
                    @click="$router.push({name: 'problem-details', params: {problemID: scope.row.problem}})">
                {{ scope.row.problem }}
              </span>
            </template>
          </el-table-column>

          <el-table-column :label="$t('m.Time')" align="center" width="100">
            <template #default="scope">
              {{ submissionTimeFormat(scope.row.statistic_info.time_cost) }}
            </template>
          </el-table-column>

          <el-table-column :label="$t('m.Memory')" align="center" width="100">
            <template #default="scope">
              {{ submissionMemoryFormat(scope.row.statistic_info.memory_cost) }}
            </template>
          </el-table-column>

          <el-table-column :label="$t('m.Language')" align="center" prop="language" width="110" />

          <el-table-column :label="$t('m.Author')" align="center" width="110">
            <template #default="scope">
              <a style="display: inline-block; max-width: 150px; cursor: pointer"
                 @click="$router.push({name: 'user-home', query: {username: scope.row.username}})">
                {{ scope.row.username }}
              </a>
            </template>
          </el-table-column>

          <el-table-column
            v-if="rejudgeColumnVisible"
            :label="$t('m.Option')"
            align="center"
            width="132"
            class-name="submission-option-column"
          >
            <template #default="scope">
              <el-button type="primary" size="small" class="submission-rejudge-btn" :loading="scope.row.loading"
                         @click="handleRejudge(scope.row.id, scope.$index)">
                {{ $t('m.Rejudge') }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <Pagination :total="total" :page-size="limit" @change="onSubmissionPageChange" v-model:current-page="page"></Pagination>
      </OjPanel>
    </div>
  </div>
</template>

<script>
  import { mapGetters } from 'vuex'
  import api from '@oj/api'
  import { ArrowDown, Refresh } from '@element-plus/icons-vue'
  import { JUDGE_STATUS, USER_TYPE } from '@/utils/constants'
  import utils from '@/utils/utils'
  import time from '@/utils/time'
  import Pagination from '@/components/Pagination.vue'

  export default {
    name: 'SubmissionList',
    components: {
      Pagination,
      ArrowDown,
      Refresh
    },
    data () {
      return {
        formFilter: {
          myself: false,
          result: '',
          username: ''
        },
        loadingTable: false,
        submissions: [],
        total: 30,
        limit: 12,
        page: 1,
        problemID: '',
        routeName: '',
        JUDGE_STATUS: ''
      }
    },
    mounted () {
      document.documentElement.classList.add('fullscreen-page')
      this.init()
      this.JUDGE_STATUS = Object.assign({}, JUDGE_STATUS)
      delete this.JUDGE_STATUS['9']
      delete this.JUDGE_STATUS['2']
    },
    beforeUnmount () {
      document.documentElement.classList.remove('fullscreen-page')
    },
    methods: {
      utcToLocal: time.utcToLocal,
      submissionTimeFormat: utils.submissionTimeFormat,
      submissionMemoryFormat: utils.submissionMemoryFormat,
      init () {
        let query = this.$route.query
        this.problemID = query.problemID

        if (query.myself !== undefined) {
          this.formFilter.myself = query.myself === '1'
        } else if (query.username && this.user && query.username === this.user.username) {
          this.formFilter.myself = true
        } else {
          this.formFilter.myself = false
        }

        this.formFilter.result = query.result || ''
        this.formFilter.username = query.username || ''

        this.page = parseInt(query.page) || 1
        if (this.page < 1) {
          this.page = 1
        }
        this.routeName = this.$route.name
        this.getSubmissions()
      },
      buildQuery () {
        return {
          myself: this.formFilter.myself === true ? '1' : '0',
          result: this.formFilter.result,
          username: this.formFilter.myself ? '' : this.formFilter.username,
          page: this.page
        }
      },
      getSubmissions () {
        let params = this.buildQuery()
        params.problem_id = this.problemID
        let offset = (this.page - 1) * this.limit
        this.loadingTable = true
        api.getSubmissionList(offset, this.limit, params).then(res => {
          let data = res.data.data
          for (let v of data.results) {
            v.loading = false
          }
          this.loadingTable = false
          this.submissions = data.results
          this.total = data.total
        }).catch(() => {
          this.loadingTable = false
          this.$error('加载提交列表失败')
        })
      },
      changeRoute () {
        let query = utils.filterEmptyValue(this.buildQuery())
        query.problemID = this.problemID
        this.$router.push({
          name: 'submission-list',
          query: utils.filterEmptyValue(query)
        })
      },
      onSubmissionPageChange (payload) {
        if (payload && typeof payload.page === 'number') {
          this.page = payload.page
        }
        this.changeRoute()
      },
      goRoute (route) {
        this.$router.push(route)
      },
      handleResultChange (status) {
        this.page = 1
        this.formFilter.result = status
        this.changeRoute()
      },
      handleQueryChange () {
        this.page = 1
        this.changeRoute()
      },
      handleRejudge (id, index) {
        this.submissions[index].loading = true
        api.submissionRejudge(id).then(res => {
          this.submissions[index].loading = false
          this.$success('Succeeded')
          this.getSubmissions()
        }, () => {
          this.submissions[index].loading = false
        })
      }
    },
    computed: {
      ...mapGetters(['isAuthenticated', 'user']),
      title () {
        if (this.problemID) {
          return this.$t('m.Problem_Submissions')
        }
        return this.$t('m.Status')
      },
      status () {
        return this.formFilter.result === '' ? this.$t('m.Status') : this.$t('m.' + JUDGE_STATUS[this.formFilter.result].name.replace(/ /g, '_'))
      },
      rejudgeColumnVisible () {
        return this.user.admin_type === USER_TYPE.ADMIN
      }
    },
    watch: {
      '$route' (newVal, oldVal) {
        if (newVal !== oldVal) {
          this.init()
        }
      },
      'isAuthenticated' () {
        this.init()
      }
    }
  }
</script>

<style scoped lang="less">
  .flex-container {
    box-sizing: border-box;
    height: calc(100vh - var(--oj-content-top-offset, 64px));
    height: calc(100dvh - var(--oj-content-top-offset, 64px));
    max-height: calc(100vh - var(--oj-content-top-offset, 64px));
    max-height: calc(100dvh - var(--oj-content-top-offset, 64px));
    overflow-y: auto;
    padding: 20px;

    #main {
      flex: auto;

      .filter {
        display: flex;
        align-items: center;
        gap: 16px;
        margin-bottom: 0;
        list-style: none;
        padding: 0;

        li {
          display: flex;
          align-items: center;
        }

        .el-input {
          width: 180px;
        }

        .el-button {
          box-shadow: var(--shadow-sm);
          &:hover {
            box-shadow: var(--shadow-md);
          }
        }
      }
    }
  }

  :deep(.submission-list-panel .el-card__header) {
    padding: 8px 18px;
  }

  :deep(.submission-list-panel .panel-header) {
    min-height: 36px;
  }

  :deep(.submission-list-panel .panel-title) {
    padding: 0;
    display: inline-flex;
    align-items: center;
  }

  :deep(.submission-list-panel .panel-extra) {
    line-height: 1;
  }

  .submission-panel-title {
    font-size: 14px;
    font-weight: 700;
    line-height: 1;
    color: var(--text-primary);
  }

  :deep(.submission-list-panel .filter .el-input__inner),
  :deep(.submission-list-panel .filter .el-button),
  :deep(.submission-list-panel .filter .el-switch__label),
  :deep(.submission-list-panel .filter .el-dropdown-link) {
    font-size: 14px;
  }

  :deep(.submission-list-panel .submission-option-column .cell) {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 100%;
  }

  :deep(.submission-list-panel .submission-rejudge-btn) {
    min-width: 92px;
    justify-content: center;
  }

  .el-dropdown-link {
    cursor: pointer;
    display: flex;
    align-items: center;
    gap: 4px;
    color: var(--primary-color);
  }
</style>
