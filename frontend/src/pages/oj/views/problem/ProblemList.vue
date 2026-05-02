<template>
  <div class="problem-list-page">
    <div class="problem-list-layout">
      <div class="problem-list-main">
        <OjPanel shadow class="problem-list-panel">
          <template #title><div class="panel-title">{{$t('m.Problem_List')}}</div></template>
          <template #extra><div>
            <ul class="filter">
              <li>
                <el-dropdown @command="filterByDifficulty">
                  <span class="filter-trigger">{{query.difficulty === '' ? $t('m.Difficulty') : $t('m.' + query.difficulty)}}
                    <span class="filter-caret" aria-hidden="true"></span>
                  </span>
                  <template #dropdown><el-dropdown-menu>
                    <el-dropdown-item command="">{{$t('m.All')}}</el-dropdown-item>
                    <el-dropdown-item command="Low">{{$t('m.Low')}}</el-dropdown-item>
                    <el-dropdown-item command="Mid">{{$t('m.Mid')}}</el-dropdown-item>
                    <el-dropdown-item command="High">{{$t('m.High')}}</el-dropdown-item>
                  </el-dropdown-menu></template>
                </el-dropdown>
              </li>
              <li>
                <el-dropdown @command="filterBySort">
                  <span class="filter-trigger">{{ sortLabel }}
                    <span class="filter-caret" aria-hidden="true"></span>
                  </span>
                  <template #dropdown><el-dropdown-menu>
                    <el-dropdown-item command="latest">最新</el-dropdown-item>
                    <el-dropdown-item command="oldest">最早</el-dropdown-item>
                    <el-dropdown-item command="ac_rate">通过率</el-dropdown-item>
                  </el-dropdown-menu></template>
                </el-dropdown>
              </li>
              <li>
                <el-dropdown @command="filterByQuestionType">
                  <span class="filter-trigger">{{ questionTypeLabel }}
                    <span class="filter-caret" aria-hidden="true"></span>
                  </span>
                  <template #dropdown><el-dropdown-menu>
                    <el-dropdown-item command="">全部题型</el-dropdown-item>
                    <el-dropdown-item command="coding">编程题</el-dropdown-item>
                    <el-dropdown-item command="choice">选择题</el-dropdown-item>
                    <el-dropdown-item command="fill_blank">填空题</el-dropdown-item>
                  </el-dropdown-menu></template>
                </el-dropdown>
              </li>
              <li>
                <el-dropdown @command="filterByChapter">
                  <span class="filter-trigger">{{ chapterLabel }}
                    <span class="filter-caret" aria-hidden="true"></span>
                  </span>
                  <template #dropdown><el-dropdown-menu>
                    <el-dropdown-item command="">全部章节</el-dropdown-item>
                    <el-dropdown-item
                      v-for="chapter in languagePackChapters"
                      :key="chapter.chapter_index"
                      :command="String(chapter.chapter_index)"
                    >
                      {{ chapter.title }}
                    </el-dropdown-item>
                  </el-dropdown-menu></template>
                </el-dropdown>
              </li>
              <li v-if="languagePacks.length">
                <el-dropdown @command="filterByLanguagePack">
                  <span class="filter-trigger">{{ languagePackLabel }}
                    <span class="filter-caret" aria-hidden="true"></span>
                  </span>
                  <template #dropdown><el-dropdown-menu>
                    <el-dropdown-item v-for="lp in languagePacks" :key="lp.id" :command="String(lp.id)">{{ lp.name }}</el-dropdown-item>
                  </el-dropdown-menu></template>
                </el-dropdown>
              </li>
              <li>
                <el-input v-model="query.keyword"
                          @keyup.enter="filterByKeyword"
                          placeholder="搜索题目">
                  <template #suffix>
                    <el-icon @click="filterByKeyword" style="cursor:pointer"><Search /></el-icon>
                  </template>
                </el-input>
              </li>
              <li>
                <el-button type="primary" @click="onReset">
                  <el-icon><Refresh /></el-icon>
                  {{$t('m.Reset')}}
                </el-button>
              </li>
            </ul>
          </div></template>
          <div v-if="loadings.table" class="skeleton-table">
            <div v-for="n in 10" :key="n" class="skeleton-row">
              <div class="skeleton-loading" style="width: 50px; height: 20px;"></div>
              <div class="skeleton-loading" style="width: 40%; height: 20px;"></div>
              <div class="skeleton-loading" style="width: 10%; height: 20px;"></div>
              <div class="skeleton-loading" style="width: 10%; height: 20px;"></div>
              <div class="skeleton-loading" style="width: 10%; height: 20px;"></div>
            </div>
          </div>
          <el-table v-else :data="problemList" style="width: 100%" class="problem-table-no-hover">
            <el-table-column v-if="showStatusColumn" width="60" label=" " align="center" class-name="problem-status-column">
              <template #default="scope">
                <template v-if="scope.row.my_status !== null && scope.row.my_status !== undefined">
                  <span :class="'problem-status-indicator ' + (scope.row.my_status === 0 ? 'problem-status-indicator-pass' : 'problem-status-indicator-fail')"
                        :title="scope.row.my_status === 0 ? '已通过' : '未通过'"
                        :aria-label="scope.row.my_status === 0 ? '已通过' : '未通过'">
                    {{ scope.row.my_status === 0 ? '✓' : '-' }}
                  </span>
                </template>
              </template>
            </el-table-column>
            <el-table-column label="#" width="140" align="center">
              <template #default="scope">
                <button class="problem-link-btn" type="button"
                        @click="$router.push({name: 'problem-details', params: {problemID: scope.row._id}})">
                  {{ scope.row._id }}
                </button>
              </template>
            </el-table-column>
            <el-table-column :label="$t('m.Title')" width="560" align="center">
              <template #default="scope">
                <button class="problem-link-btn problem-link-btn-title" type="button"
                        @click="$router.push({name: 'problem-details', params: {problemID: scope.row._id}})">
                  {{ scope.row.title }}
                </button>
              </template>
            </el-table-column>
            <el-table-column :label="$t('m.Level')" width="132" align="center" class-name="problem-difficulty-column">
              <template #default="scope">
                <span :class="`problem-difficulty-pill ${difficultyClass(scope.row.difficulty)}`">
                  {{ difficultyText(scope.row.difficulty) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column :label="$t('m.Total')" width="90" align="center" prop="submission_number" />
            <el-table-column :label="$t('m.AC_Rate')" width="100" align="center" class-name="problem-ac-rate-column">
              <template #default="scope">
                <span class="problem-ac-rate-text">{{ getACRate(scope.row.accepted_number, scope.row.submission_number) }}</span>
              </template>
            </el-table-column>
            <el-table-column :label="$t('m.Tags')" min-width="180" align="center" class-name="problem-tags-column">
              <template #default="scope">
                <template v-if="!scope.row.displayTags || !scope.row.displayTags.length">
                  <span style="color: #999; font-size: 12px">-</span>
                </template>
                <div v-else class="problem-tags-wrap">
                  <button class="problem-list-tag-chip" type="button" @click="filterByTag(scope.row.displayTags[0].rawName)">
                    {{ scope.row.displayTags[0].displayName }}
                  </button>
                  <el-popover
                    v-if="scope.row.displayTags.length > 1"
                    placement="bottom"
                    trigger="click"
                    popper-class="problem-tags-popover"
                    :width="280"
                  >
                    <template #reference>
                      <button class="problem-list-tag-more" type="button" aria-label="更多标签">...</button>
                    </template>
                    <div class="problem-tags-popover-list">
                      <button v-for="tag in scope.row.displayTags.slice(1)" :key="tag.rawName"
                              class="problem-list-tag-chip" type="button"
                              @click="filterByTag(tag.rawName)">
                        {{ tag.displayName }}
                      </button>
                    </div>
                  </el-popover>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </OjPanel>
        <Pagination :key="`problem-pagination-${total}-${query.page}`"
                    :total="total"
                    :page-size="limit"
                    @change="onPaginationChange"
                    v-model:current-page="query.page"
                    class="pagination-center"></Pagination>
      </div>

      <aside class="problem-list-sidebar">
        <div class="problem-list-sidebar-stack">
          <OjPanel :padding="10" class="tag-panel">
            <template #title><div class="tag-title">{{$t('m.Tags')}}</div></template>
            <div class="tag-panel-body">
              <el-button v-for="tag in tagList"
                      :key="tag.rawName"
                      @click="filterByTag(tag.rawName)"
                      plain
                      round
                      :class="['tag-btn', { 'tag-btn-active': query.tag === tag.rawName }]">{{tag.displayName}}
              </el-button>
            </div>
          </OjPanel>
          <OjPanel :padding="10" class="pick-one-panel">
            <template #title><div class="pick-one-title">随机一题</div></template>
            <el-button plain @click="pickone" class="pick-one-btn" style="width:100%">Pick One</el-button>
          </OjPanel>
        </div>
      </aside>
    </div>

    <CalibrationPanel
      :visible="showCalibration"
      :initial-question="calibrationQuestion"
      @calibration-done="handleCalibrationDone"
    />
  </div>
</template>

<script>
  import { mapGetters } from 'vuex'
  import { Search, Refresh } from '@element-plus/icons-vue'
  import api from '@oj/api'
  import utils from '@/utils/utils'
  import { useProblemList } from '@/composables/useProblemList'
  import { normalizeDisplayTags } from '@oj/utils/problemTagView'
  import { encodeRouteCtx, decodeRouteCtx } from '@/utils/urlCipher'
  import Pagination from '@/components/Pagination.vue'
  import CalibrationPanel from './CalibrationPanel'

  export default {
    name: 'ProblemList',
    setup () {
      const problemList = useProblemList()
      return { ...problemList }
    },
    components: {
      Pagination,
      CalibrationPanel,
      Search,
      Refresh
    },
    data () {
      return {
        tagList: [],
        languagePacks: [],
        languagePackChapters: [],
        problemList: [],
        limit: 10,
        total: 0,
        loadings: {
          table: true,
          tag: true
        },
        routeName: '',
        query: {
          keyword: '',
          difficulty: '',
          tag: '',
          chapter: '',
          question_type: '',
          language_pack_id: '',
          sort_by: 'latest',
          page: 1,
          limit: 10
        },
        showCalibration: false,
        calibrationQuestion: null
      }
    },
    mounted () {
      document.documentElement.classList.add('fullscreen-page')
      this.init()
      this.checkCalibration()
    },
    beforeUnmount () {
      document.documentElement.classList.remove('fullscreen-page')
    },
    methods: {
      normalizeProblemList (rawList) {
        if (!Array.isArray(rawList)) {
          return []
        }
        return rawList.map((item) => this.toProblemListItem(item))
      },
      isKnownDifficulty (difficulty) {
        return difficulty === 'Low' || difficulty === 'Mid' || difficulty === 'High'
      },
      difficultyClass (difficulty) {
        if (difficulty === 'Low') return 'problem-difficulty-pill-low'
        if (difficulty === 'Mid') return 'problem-difficulty-pill-mid'
        if (difficulty === 'High') return 'problem-difficulty-pill-high'
        return 'problem-difficulty-pill-unknown'
      },
      difficultyText (difficulty) {
        if (!this.isKnownDifficulty(difficulty)) {
          return '-'
        }
        return this.$t('m.' + difficulty)
      },
      toProblemListItem (item) {
        const raw = item && typeof item === 'object' ? item : {}
        const rawTags = Array.isArray(raw.tags) ? raw.tags : []
        return {
          id: raw.id,
          _id: raw._id,
          title: raw.title,
          difficulty: raw.difficulty,
          submission_number: raw.submission_number,
          accepted_number: raw.accepted_number,
          my_status: raw.my_status === undefined ? null : raw.my_status,
          tags: rawTags,
          displayTags: normalizeDisplayTags(rawTags)
        }
      },
      getCalibrationDismissKey () {
        return `oj_calibration_dismissed_date_${this.user.id}`
      },
      getTodayDateString () {
        const now = new Date()
        const year = now.getFullYear()
        const month = String(now.getMonth() + 1).padStart(2, '0')
        const day = String(now.getDate()).padStart(2, '0')
        return `${year}-${month}-${day}`
      },
      isCalibrationDismissedToday () {
        return window.localStorage.getItem(this.getCalibrationDismissKey()) === this.getTodayDateString()
      },
      markCalibrationDismissedToday () {
        window.localStorage.setItem(this.getCalibrationDismissKey(), this.getTodayDateString())
      },
      async init (simulate = false) {
        this.routeName = this.$route.name
        let query = this.$route.query
        this.query.difficulty = query.difficulty || ''
        this.query.keyword = query.keyword || ''
        this.query.tag = query.tag || ''
        this.query.chapter = query.chapter || ''
        this.query.question_type = query.question_type || ''
        this.query.language_pack_id = (query.lp_ctx ? decodeRouteCtx(query.lp_ctx).lp : query.language_pack_id) || ''
        this.query.sort_by = query.sort_by || 'latest'
        this.query.page = parseInt(query.page) || 1
        if (this.query.page < 1) {
          this.query.page = 1
        }
        this.query.limit = parseInt(query.limit) || 10
        if (!simulate) {
          await this.loadLanguagePacks()
          if (this.applyDefaultLanguagePack()) {
            return
          }
        } else if (this.languagePacks.length) {
          if (this.applyDefaultLanguagePack()) {
            return
          }
        }
        await this.loadLanguagePackChapters(this.query.language_pack_id)
        if (this.query.chapter && !this.languagePackChapters.some(ch => String(ch.chapter_index) === String(this.query.chapter))) {
          this.query.chapter = ''
          this.query.page = 1
          this.pushRouter()
          return
        }
        this.getTagList()
        this.getProblemList()
      },
      pushRouter (page) {
        if (typeof page === 'number' && !Number.isNaN(page)) {
          this.query.page = page
        }
        const routeQuery = Object.assign({}, this.query)
        if (routeQuery.language_pack_id) {
          routeQuery.lp_ctx = encodeRouteCtx({ lp: routeQuery.language_pack_id })
        }
        delete routeQuery.language_pack_id
        this.$router.push({
          name: 'problem-list',
          query: utils.filterEmptyValue(routeQuery)
        })
      },
      onPaginationChange (payload) {
        const page = payload && typeof payload.page === 'number' ? payload.page : undefined
        this.pushRouter(page)
      },
      getProblemList () {
        let offset = (this.query.page - 1) * this.query.limit
        this.loadings.table = true
        let params = Object.assign({}, this.query)
        api.getProblemList(offset, this.query.limit, params).then(res => {
          this.loadings.table = false
          this.total = res.data.data.total
          this.problemList = this.normalizeProblemList(res.data.data.results)
        }, res => {
          this.loadings.table = false
        })
      },
      getTagList () {
        this.loadings.tag = true
        this.tagList = []
        const params = {}
        if (this.query.language_pack_id) {
          params.language_pack_id = this.query.language_pack_id
        }
        api.getProblemTagList(params).then(res => {
          const rawTags = Array.isArray(res.data.data) ? res.data.data : []
          this.tagList = normalizeDisplayTags(rawTags.map(item => item && item.name))
          this.loadings.tag = false
        }, res => {
          this.tagList = []
          this.loadings.tag = false
        })
      },
      filterByTag (tagName) {
        this.query.tag = this.query.tag === tagName ? '' : tagName
        this.query.page = 1
        this.pushRouter()
      },
      filterByDifficulty (difficulty) {
        this.query.difficulty = difficulty
        this.query.page = 1
        this.pushRouter()
      },
      filterByKeyword () {
        this.query.page = 1
        this.pushRouter()
      },
      filterBySort (sortBy) {
        this.query.sort_by = sortBy
        this.query.page = 1
        this.pushRouter()
      },
      filterByQuestionType (questionType) {
        this.query.question_type = questionType
        this.query.page = 1
        this.pushRouter()
      },
      filterByChapter (chapter) {
        this.query.chapter = chapter
        this.query.page = 1
        this.pushRouter()
      },
      filterByLanguagePack (packId) {
        if (!packId) {
          return
        }
        this.query.language_pack_id = packId
        this.query.tag = ''
        this.query.chapter = ''
        this.query.page = 1
        this.pushRouter()
      },
      loadLanguagePacks () {
        return api.getVisibleLanguagePackList().then(res => {
          this.languagePacks = res.data.data || []
        }).catch(() => {
          this.languagePacks = []
          this.$error('加载课程包失败')
        })
      },
      loadLanguagePackChapters (languagePackId) {
        if (!languagePackId) {
          this.languagePackChapters = []
          return Promise.resolve()
        }
        return api.getLanguagePackChapters(languagePackId).then(res => {
          const raw = res && res.data ? res.data.data : []
          const chapters = Array.isArray(raw) ? raw : []
          this.languagePackChapters = chapters
            .filter(item => item && item.chapter_index !== undefined && item.chapter_index !== null)
            .map(item => ({
              chapter_index: Number(item.chapter_index),
              title: item.title || `第${item.chapter_index}章`
            }))
        }).catch(() => {
          this.languagePackChapters = []
        })
      },
      applyDefaultLanguagePack () {
        if (!this.languagePacks.length) {
          return false
        }
        if (this.query.language_pack_id) {
          const matched = this.languagePacks.some(lp => String(lp.id) === String(this.query.language_pack_id))
          if (matched) {
            return false
          }
        }
        this.query.language_pack_id = String(this.languagePacks[0].id)
        this.query.tag = ''
        this.query.page = 1
        this.pushRouter()
        return true
      },
      onReset () {
        this.$router.push({name: 'problem-list'})
      },
      pickone () {
        api.pickone().then(res => {
          this.$success('Good Luck')
          this.$router.push({name: 'problem-details', params: {problemID: res.data.data}})
        })
      },
      checkCalibration () {
        if (!this.isAuthenticated || this.isCalibrationDismissedToday()) return
        api.calibrationStatus().then(res => {
          const data = res.data && res.data.data !== undefined ? res.data.data : res.data
          if (data && data.needs_calibration && data.first_question) {
            this.calibrationQuestion = {
              question_index: data.first_question.index || 0,
              total_questions: data.total_questions || 3,
              prompt: data.first_question.prompt || '',
              kc_group: data.first_question.kc_group || []
            }
            this.showCalibration = true
          }
        }).catch(() => {})
      },
      handleCalibrationDone (payload = {}) {
        if (payload && (payload.skipped || payload.error)) {
          this.markCalibrationDismissedToday()
        }
        this.showCalibration = false
        this.calibrationQuestion = null
      }
    },
    computed: {
      ...mapGetters(['isAuthenticated', 'user']),
      showStatusColumn () {
        return this.isAuthenticated && this.problemList.some(item => item.my_status !== null && item.my_status !== undefined)
      },
      sortLabel () {
        const map = {
          latest: '最新',
          oldest: '最早',
          ac_rate: '通过率'
        }
        return map[this.query.sort_by] || '排序'
      },
      questionTypeLabel () {
        const map = {
          coding: '编程题',
          choice: '选择题',
          fill_blank: '填空题'
        }
        return map[this.query.question_type] || '题型'
      },
      chapterLabel () {
        if (!this.query.chapter) return '章节'
        const found = this.languagePackChapters.find(ch => String(ch.chapter_index) === String(this.query.chapter))
        return found ? found.title : '章节'
      },
      languagePackLabel () {
        if (!this.query.language_pack_id) return '课程内容包'
        const found = this.languagePacks.find(lp => String(lp.id) === this.query.language_pack_id)
        return found ? found.name : '课程内容包'
      }
    },
    watch: {
      '$route' (newVal, oldVal) {
        if (newVal !== oldVal) {
          this.init(true)
        }
      },
      'isAuthenticated' (newVal) {
        if (newVal === true) {
          this.init()
        }
      }
    }
  }
</script>

<style scoped lang="less">
  .problem-list-page {
    box-sizing: border-box;
    width: 100%;
    min-width: 0;
    height: calc(100vh - var(--oj-content-top-offset, 64px));
    height: calc(100dvh - var(--oj-content-top-offset, 64px));
    max-height: calc(100vh - var(--oj-content-top-offset, 64px));
    max-height: calc(100dvh - var(--oj-content-top-offset, 64px));
    overflow-y: auto;
    padding-top: 20px;

    :deep(.filter .el-button) {
      height: 32px;
      line-height: 30px;
      font-size: 14px;
      padding: 0 15px;
      border-radius: 4px;
    }

    :deep(.filter .el-input__inner) {
      height: 32px;
      min-height: 32px;
      font-size: 14px;
      line-height: 1.5;
    }

    :deep(.filter .el-input__suffix) {
      width: 32px;
      height: 32px;
      line-height: 32px;
      font-size: 16px;
    }

    :deep(.tag-panel .el-button) {
      height: 32px;
      line-height: 30px;
      font-size: 14px;
      padding: 0 12px;
      border-radius: 4px;
    }

    :deep(.tag-panel .el-button.is-round) {
      border-radius: 28px;
    }

    :deep(.pick-one-panel .el-button) {
      height: 32px;
      line-height: 30px;
      font-size: 14px;
      border-radius: 4px;
    }

    :deep(.problem-list-tag-chip) {
      height: 20px;
      line-height: 18px;
      padding: 0 6px;
      font-size: 12px;
    }

    :deep(.problem-link-btn) {
      border: 0;
      background: transparent;
      color: var(--primary-color);
      cursor: pointer;
      padding: 2px 0;
      font-size: 14px;
      font-weight: 600;
    }

    :deep(.problem-link-btn:hover) {
      color: var(--primary-hover, #1d4ed8);
    }

    :deep(.problem-link-btn-title) {
      width: 100%;
      overflow-x: auto;
      text-align: center;
    }

    :deep(.problem-difficulty-pill) {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      min-width: 56px;
      padding: 2px 10px;
      border-radius: 999px;
      font-size: 12px;
      font-weight: 600;
      white-space: nowrap;
    }

    :deep(.problem-difficulty-pill-low) {
      background: #ecfdf3;
      color: #15803d;
    }

    :deep(.problem-difficulty-pill-mid) {
      background: #eff6ff;
      color: #1d4ed8;
    }

    :deep(.problem-difficulty-pill-high) {
      background: #fef3c7;
      color: #b45309;
    }

    :deep(.problem-difficulty-pill-unknown) {
      background: #f1f5f9;
      color: #64748b;
    }

    :deep(.problem-difficulty-column .cell) {
      white-space: nowrap;
      overflow: visible;
      text-overflow: clip;
    }

    :deep(.problem-ac-rate-column .cell) {
      white-space: nowrap;
      overflow: visible;
      text-overflow: clip;
    }

    :deep(.problem-tags-column .cell) {
      white-space: nowrap;
      overflow: visible;
      text-overflow: clip;
      line-height: 1;
      padding-top: 8px;
      padding-bottom: 8px;
    }

    :deep(.problem-ac-rate-text) {
      display: inline-block;
      white-space: nowrap;
      word-break: keep-all;
    }

    :deep(.problem-tags-wrap) {
      display: inline-flex;
      flex-wrap: nowrap;
      align-items: center;
      justify-content: center;
      gap: 4px;
      max-width: 100%;
      overflow: hidden;
      vertical-align: middle;
    }

    :deep(.problem-list-tag-chip) {
      margin: 2px;
      padding: 0 8px;
      border: 1px solid #bfdbfe;
      border-radius: 999px;
      background: #eff6ff;
      color: #1d4ed8;
      cursor: pointer;
      font-size: 12px;
      line-height: 20px;
      white-space: nowrap;
      word-break: keep-all;
      max-width: 100%;
      overflow: hidden;
      text-overflow: ellipsis;
      display: inline-block;
      vertical-align: middle;
    }

    :deep(.problem-list-tag-chip:hover) {
      background: #dbeafe;
      border-color: #93c5fd;
    }

    :deep(.problem-status-indicator) {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 20px;
      height: 20px;
      border-radius: 999px;
      font-size: 12px;
      font-weight: 700;
      line-height: 1;
      flex-shrink: 0;
      overflow: visible;
    }

    :deep(.problem-status-indicator-pass) {
      background: #ecfdf3;
      color: #15803d;
    }

    :deep(.problem-status-indicator-fail) {
      background: #fef2f2;
      color: #dc2626;
    }

    :deep(.el-pager li),
    :deep(.el-pagination .btn-next),
    :deep(.el-pagination .btn-prev) {
      min-width: 32px;
      height: 32px;
      line-height: 30px;
      font-size: 14px;
      border-radius: 4px;
    }
  }

  .problem-list-layout {
    display: grid;
    grid-template-columns: minmax(0, 1fr) 340px;
    gap: 24px;
    align-items: start;
  }

  .problem-list-main {
    min-width: 0;
  }

  .problem-list-sidebar {
    min-width: 0;
  }

  .problem-list-sidebar-stack {
    position: sticky;
    top: var(--oj-content-top-offset, 64px);
    max-height: calc(100dvh - var(--oj-content-top-offset, 64px));
    display: flex;
    flex-direction: column;
    gap: 20px;
  }

  .problem-list-panel {
    border-radius: var(--border-radius-md);
    border: 1px solid var(--border-color);
    box-shadow: var(--shadow-sm);
    overflow: hidden;
    background: var(--bg-card);
    
    .panel-title {
        font-size: 14px;
        font-weight: 700;
        color: var(--text-primary);
        line-height: 1;
    }
  }

  :deep(.problem-list-panel .el-card__header) {
    padding: 8px 18px;
  }

  :deep(.problem-list-panel .panel-header) {
    min-height: 36px;
  }

  :deep(.problem-list-panel .panel-title) {
    padding: 0;
    display: inline-flex;
    align-items: center;
  }

  :deep(.problem-list-panel .panel-extra) {
    line-height: 1;
  }

  .problem-table-no-hover {
    :deep(.el-table__row:hover > td) {
      background-color: transparent;
    }
  }

  :deep(.problem-list-panel .panel-body) {
    word-break: normal;
    word-wrap: normal;
  }

  .skeleton-table {
    padding: 20px;
    
    .skeleton-row {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 15px;
      padding-bottom: 15px;
      border-bottom: 1px solid var(--border-color);
      
      &:last-child {
        border-bottom: none;
      }
    }
  }

  .tag-panel, .pick-one-panel {
    border-radius: var(--border-radius-md);
    border: 1px solid var(--border-color);
    box-shadow: var(--shadow-sm);
    background: var(--bg-card);
    margin-bottom: 20px;
    
    .tag-title, .pick-one-title {
        font-size: 16px;
        font-weight: 600;
        color: var(--text-primary);
    }
  }

  :deep(.tag-panel.el-card) {
    display: flex;
    flex-direction: column;
    min-height: 0;
  }

  :deep(.tag-panel .el-card__body) {
    display: flex;
    flex: 1;
    min-height: 0;
  }

  :deep(.tag-panel .panel-body) {
    display: flex;
    flex: 1;
    min-height: 0;
  }

  .tag-panel-body {
    display: flex;
    flex: 1;
    flex-wrap: wrap;
    align-content: flex-start;
    gap: 10px;
    min-height: 0;
    overflow-y: auto;
    padding-right: 4px;
  }

  .taglist-title {
    margin-left: -10px;
    margin-bottom: -10px;
  }

  .tag-btn {
    border-color: var(--border-color);
    color: var(--text-secondary);
    transition: all 0.2s;
    cursor: pointer;
    
    &:hover {
        border-color: var(--primary-color);
        color: var(--primary-color);
        background: rgba(37, 99, 235, 0.05);
    }
    
    &.tag-btn-active {
        background: var(--primary-color);
        color: white;
        border-color: var(--primary-color);
        
        &:hover {
            background: var(--primary-hover, #1d4ed8);
            border-color: var(--primary-hover, #1d4ed8);
            color: white;
        }
    }
  }

  #pick-one {
    margin-top: 10px;
  }
  
  .pick-one-btn {
    border-color: var(--primary-color);
    color: var(--primary-color);
    font-weight: 600;
    
    &:hover {
        background: var(--primary-color);
        color: white;
    }
  }
  
  .pagination-center {
      margin-top: 20px;
      display: flex;
      justify-content: center;
      
      :deep(.el-pager li.is-active) {
          background-color: var(--primary-color);
          border-color: var(--primary-color);
      }
  }
  
  .filter {
    display: flex;
    align-items: center;
    justify-content: flex-end;
    flex-wrap: nowrap;
    gap: 12px;

    .filter-trigger {
      display: inline-flex;
      align-items: center;
      height: 32px;
      font-size: 14px;
      font-weight: 700;
      color: #2f405f;
      cursor: pointer;
      line-height: 1;
    }

    .filter-caret {
      display: inline-block;
      width: 0;
      height: 0;
      margin-left: 6px;
      vertical-align: middle;
      border-left: 5px solid transparent;
      border-right: 5px solid transparent;
      border-top: 6px solid #2f405f;
    }
    
    li {
        display: inline-flex;
        align-items: center;
    }
    
    .el-button--primary {
        background-color: var(--primary-color);
        border-color: var(--primary-color);
        box-shadow: 0 2px 6px rgba(37, 99, 235, 0.2);
        
        &:hover {
            background-color: var(--primary-hover);
        }
    }
    
    .el-input__inner {
        border-radius: 20px;
        padding-left: 15px;
        border-color: var(--border-color);
        
        &:focus {
            border-color: var(--primary-color);
            box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.1);
        }
    }
  }

  :deep(.problem-list-panel .filter .el-input__inner),
  :deep(.problem-list-panel .filter .el-button),
  :deep(.problem-list-panel .filter .el-dropdown),
  :deep(.problem-list-panel .panel-title) {
    font-size: 14px;
  }

  :deep(.problem-list-tag-more) {
    margin: 2px;
    height: 22px;
    min-width: 28px;
    padding: 0 8px;
    border: 1px solid #bfdbfe;
    border-radius: 999px;
    background: #eff6ff;
    color: #1d4ed8;
    cursor: pointer;
    font-size: 12px;
    line-height: 20px;
    white-space: nowrap;
    font-weight: 700;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
  }

  :deep(.problem-list-tag-more:hover) {
    background: #dbeafe;
    border-color: #93c5fd;
  }

  :deep(.problem-tags-popover-list) {
    display: flex;
    flex-wrap: wrap;
    gap: 4px;
  }

  :deep(.problem-status-column .cell) {
    white-space: nowrap;
    overflow: visible;
    text-overflow: clip;
  }

  @media screen and (max-width: 1279px) {
    .problem-list-layout {
      grid-template-columns: 1fr;
      min-height: auto;
    }

    .problem-list-sidebar-stack {
      position: static;
      top: auto;
      max-height: none;
    }

    .tag-panel-body {
      overflow-y: visible;
      max-height: none;
      padding-right: 0;
    }

    .filter {
      flex-wrap: wrap;
      justify-content: flex-start;
    }
  }
</style>

<style lang="less">
  .problem-tags-popover-list {
    display: flex;
    flex-wrap: wrap;
    gap: 4px;
  }

  .problem-tags-popover .problem-list-tag-chip {
    margin: 2px;
    padding: 0 8px;
    border: 1px solid #bfdbfe;
    border-radius: 999px;
    background: #eff6ff;
    color: #1d4ed8;
    cursor: pointer;
    font-size: 12px;
    line-height: 20px;
    white-space: nowrap;
    word-break: keep-all;
    max-width: 100%;
    overflow: hidden;
    text-overflow: ellipsis;
    display: inline-block;
    vertical-align: middle;
    appearance: none;
    -webkit-appearance: none;
  }

  .problem-tags-popover .problem-list-tag-chip:hover {
    background: #dbeafe;
    border-color: #93c5fd;
  }
</style>
