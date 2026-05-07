<template>
  <div class="classroom-assignment">
    <AssignmentDetail
      v-if="viewingAssignmentId"
      :classroom-id="classroomId"
      :assignment-id="viewingAssignmentId"
      :is-staff="isStaff"
      :view-only="isStaff"
      @submitted="loadAssignments"
      @back="viewingAssignmentId = null"
      @grading="openGradingFromView"
    />
    <AssignmentGrading
      v-else-if="gradingAssignmentId"
      :classroom-id="classroomId"
      :assignment-id="gradingAssignmentId"
      @back="gradingAssignmentId = null"
    />
    <el-card v-show="!gradingAssignmentId && !viewingAssignmentId">
      <template #header>
        <div class="panel-header">
          <span class="panel-title">
            <el-icon><Reading /></el-icon>
            作业列表
          </span>
          <el-button v-if="isStaff" type="primary" @click="showCreateModal = true">
            <el-icon><Plus /></el-icon>
            发布作业
          </el-button>
        </div>
      </template>

      <el-table :data="pagedAssignments" v-loading="loading">
        <el-table-column label="标题" min-width="200" align="center">
          <template #default="scope">
            <a style="cursor: pointer; color: #2d8cf0" @click="viewingAssignmentId = String(scope.row.id)">{{ scope.row.title }}</a>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="scope">
            <el-tag :style="getStatusStyle(scope.row.status)">{{ getStatusText(scope.row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="时间" min-width="340" align="center">
          <template #default="scope">
            <span style="white-space: nowrap">{{ formatTime(scope.row.start_time) }} ~ {{ formatTime(scope.row.end_time) }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="!isStaff" label="得分" width="120" align="center">
          <template #default="scope">
            <span v-if="scope.row.my_score == null" style="color: #c5c8ce">未提交</span>
            <span v-else style="font-weight: 600; color: #19be6b">{{ scope.row.my_score }} 分</span>
          </template>
        </el-table-column>
        <el-table-column v-if="!isStaff" label="操作" width="120" align="center">
          <template #default="scope">
            <el-button type="primary" size="small" @click="viewingAssignmentId = String(scope.row.id)">
              <el-icon style="margin-right: 4px;"><Link /></el-icon>
              做题
            </el-button>
          </template>
        </el-table-column>
        <el-table-column v-if="isStaff" label="操作" min-width="260" align="center">
          <template #default="scope">
            <div style="white-space: nowrap">
              <el-button text size="small" style="color: #2d8cf0" @click="openStatsDialog(scope.row)">
                <el-icon><View /></el-icon> 查看
              </el-button>
              <el-button text size="small" style="color: #19be6b" @click="editAssignment(scope.row)">
                <el-icon><Edit /></el-icon> 更改
              </el-button>
              <el-button text size="small" style="color: #ed4014" @click="confirmDeleteAssignment(scope.row)">
                <el-icon><DeleteIcon /></el-icon> 删除
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <Pagination
        :total="assignments.length"
        :current-page="assignmentPage"
        :page-size="assignmentPageSize"
        @update:currentPage="assignmentPage = $event"
        @update:pageSize="assignmentPageSize = $event"
      />
    </el-card>
    <el-dialog v-model="showCreateModal" :title="formMode === 'create' ? '发布新作业' : '编辑作业'" width="800px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="作业标题">
          <el-input v-model="form.title" placeholder="例如：期中考试编程题"/>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="作业说明..."/>
        </el-form-item>
        <el-form-item label="时间安排">
          <el-date-picker type="datetimerange"
                      v-model="form.time_range"
                      format="YYYY-MM-DD HH:mm"
                      start-placeholder="开始时间"
                      end-placeholder="截止时间"
                      style="width: 400px">
          </el-date-picker>
        </el-form-item>
        <el-form-item label="补交策略">
          <el-checkbox v-model="form.allow_late_submission">允许逾期补交</el-checkbox>
          <div v-if="form.allow_late_submission" style="margin-top: 5px;">
            逾期扣分系数：
            <el-input-number :max="1" :min="0" :step="0.1" v-model="form.late_penalty"></el-input-number>
            <span style="color: #808695; margin-left: 10px;">(0.8 表示打8折)</span>
          </div>
        </el-form-item>
        <el-form-item label="AI 导学">
          <el-checkbox v-model="form.allow_ai_tutor">允许学生使用 AI 助手（未 AC 且未截止时生效）</el-checkbox>
        </el-form-item>

        <el-form-item label="组卷模式">
          <el-radio-group v-model="form.compose_strategy" @change="onComposeStrategyChange">
            <el-radio value="manual">手动选题</el-radio>
            <el-radio value="smart_kc">智能组卷（按薄弱 KC 自动选题）</el-radio>
          </el-radio-group>
        </el-form-item>

        <div v-if="form.compose_strategy === 'smart_kc'" class="smart-compose-panel">
          <div class="smart-compose-header">
            <el-icon><MagicStick /></el-icon>
            <span>智能组卷</span>
            <el-tag size="small" type="info" effect="plain" style="margin-left: 8px;">基于班级薄弱 KC + BSP</el-tag>
          </div>
          <el-form-item label="目标 KC">
            <el-cascader
              v-model="form.target_kc_ids"
              :options="kcCascadeOptions"
              :props="kcCascaderProps"
              collapse-tags
              collapse-tags-tooltip
              clearable
              filterable
              placeholder="可留空，留空时按班级薄弱 KC 自动推断"
              style="width: 100%">
            </el-cascader>
          </el-form-item>
          <el-form-item label="每生题数">
            <el-input-number v-model="form.per_student_budget" :min="1" :max="5" />
            <span class="smart-compose-hint">每个 KC 在代表生路径上最多取 N 张卡</span>
          </el-form-item>
          <el-form-item label="总题数上限">
            <el-input-number v-model="form.total_problem_budget" :min="1" :max="30" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" plain :loading="smartComposePreviewing" @click="previewSmartCompose">
              <el-icon><MagicStick /></el-icon>
              预览拟选题
            </el-button>
            <el-button v-if="smartComposeResult" type="success" plain @click="applySmartComposeAsSections">
              <el-icon><DocumentAdd /></el-icon>
              应用为作业板块
            </el-button>
          </el-form-item>
          <div v-if="smartComposeResult" class="smart-compose-result">
            <div class="smart-compose-summary">
              <span class="summary-pill primary">已选 {{ smartComposeResult.total_picked }} 题</span>
              <span class="summary-label">覆盖 KC：</span>
              <el-tag
                v-for="kc in smartComposeResult.kc_names"
                :key="kc"
                size="small"
                effect="light"
                type="info"
                style="margin-right: 6px; margin-bottom: 4px;">
                {{ kc }}
              </el-tag>
            </div>
            <el-table :data="flatSmartComposePreview" size="small" max-height="240" class="smart-compose-table">
              <el-table-column label="KC" prop="kc_name" width="160" />
              <el-table-column label="题目" prop="title" show-overflow-tooltip />
              <el-table-column label="难度" prop="difficulty" width="90" align="center">
                <template #default="{ row }">
                  <el-tag size="small" :type="difficultyTagType(row.difficulty)">{{ row.difficulty || '—' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="题型" prop="card_type" width="120" align="center">
                <template #default="{ row }">{{ cardTypeLabel(row.card_type) }}</template>
              </el-table-column>
            </el-table>
          </div>
        </div>
        <div class="sections-manager">
          <h4>作业板块</h4>
          <div v-for="(section, index) in form.sections" :key="index" class="section-item">
            <div class="section-header">
              <el-input v-model="section.title" placeholder="板块标题" style="width: 200px; margin-right: 10px;"/>
              <el-button type="danger" size="small" @click="removeSection(index)">
                <el-icon><DeleteIcon /></el-icon>
              </el-button>
            </div>
            <div class="section-problems">
              <div v-for="(problem, pIndex) in section.problems" :key="pIndex" class="problem-item">
                <span class="problem-link" @click="openProblemPage(problem)">
                  {{ problem.title || '未知题目' }}
                  <span style="color: #808695;">({{ problem._id || problem.problem_display_id || '—' }})</span>
                </span>
                <div class="problem-config">
                  分值: <el-input-number v-model="problem.score" size="small" style="width: 60px;"></el-input-number>
                  <el-button text size="small" @click="removeProblem(index, pIndex)">
                    <el-icon><Close /></el-icon>
                  </el-button>
                </div>
              </div>
              <el-button plain size="small" @click="openProblemSelector(index)">
                <el-icon><Plus /></el-icon>
                添加题目
              </el-button>
            </div>
          </div>
          <el-button type="primary" @click="addSection" style="width: 100%; margin-top: 10px;">
            <el-icon><Plus /></el-icon>
            添加板块
          </el-button>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="showCreateModal = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitAssignment">确定</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="showProblemSelector" title="选择题目" width="800px">
      <div style="margin-bottom: 15px;">
        <el-input v-model="searchProblemKeyword" placeholder="输入题目 ID 或标题进行搜索" style="width: 300px" @keyup.enter="loadClassroomProblems">
          <template #append>
            <el-button @click="loadClassroomProblems">
              <el-icon><Search /></el-icon>
            </el-button>
          </template>
        </el-input>
      </div>
      <el-table :data="problemSearchResults" v-loading="searchProblemLoading" :max-height="400" @selection-change="handleProblemSelection">
        <el-table-column type="selection" width="60" align="center" />
        <el-table-column label="ID" prop="_id" width="150" />
        <el-table-column label="标题" prop="title" />
        <el-table-column label="难度" prop="difficulty" width="100" />
      </el-table>
      <div style="margin-top: 15px; text-align: right;">
        <el-pagination :total="searchProblemTotal" :page-size="10" @current-change="handleSearchPageChange" layout="total, prev, pager, next" small />
      </div>
      <template #footer>
        <el-button @click="showProblemSelector = false">取消</el-button>
        <el-button type="primary" @click="confirmAddProblems">确定添加</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="showStatsDialog" :title="statsTitle" width="860px" top="4vh" destroy-on-close>
      <div v-loading="statsLoading" class="stats-dialog-body">
        <template v-if="stats">
          <div class="stats-overview">
            <div class="stats-card">
              <div class="stats-card-value">{{ stats.submitted_count }}<span class="stats-card-sub">/{{ stats.total_students }}</span></div>
              <div class="stats-card-label">已提交</div>
              <div class="stats-card-extra">{{ submitRate }}%</div>
            </div>
            <div class="stats-card">
              <div class="stats-card-value">{{ stats.avg_score }}</div>
              <div class="stats-card-label">平均分</div>
              <div class="stats-card-extra">满分 {{ stats.full_score }}</div>
            </div>
            <div class="stats-card">
              <div class="stats-card-value">{{ stats.max_score }}</div>
              <div class="stats-card-label">最高分</div>
            </div>
            <div class="stats-card">
              <div class="stats-card-value">{{ stats.min_score }}</div>
              <div class="stats-card-label">最低分</div>
            </div>
            <div class="stats-card">
              <div class="stats-card-value">{{ stats.late_count }}</div>
              <div class="stats-card-label">迟交</div>
            </div>
            <div class="stats-card">
              <div class="stats-card-value">{{ stats.ungraded_count }}</div>
              <div class="stats-card-label">待批阅</div>
            </div>
          </div>
          <div class="stats-section">
            <div class="stats-section-title">逐题得分率</div>
            <div v-for="(p, idx) in stats.problem_stats" :key="idx" class="problem-stat-row">
              <span class="problem-stat-section" v-if="idx === 0 || p.section_title !== stats.problem_stats[idx - 1].section_title">{{ p.section_title }}</span>
              <div class="problem-stat-bar">
                <span class="problem-stat-name">{{ p.problem_title }}</span>
                <span class="problem-stat-score">{{ p.full_score }}分</span>
                <el-progress :percentage="Math.round((p.avg_score_rate || 0) * 100)" :stroke-width="14" :text-inside="true" style="flex: 1" />
                <span class="problem-stat-rate">{{ p.attempt_count > 0 ? Math.round((p.correct_count / p.attempt_count) * 100) : 0 }}% AC</span>
              </div>
            </div>
            <el-empty v-if="!stats.problem_stats || !stats.problem_stats.length" description="暂无题目数据" :image-size="40" />
          </div>
          <div class="stats-section">
            <div class="stats-section-title">成绩分布</div>
            <div ref="scoreDistChart" class="score-dist-chart"></div>
          </div>
          <div class="stats-section">
            <div class="stats-section-title">学生提交明细</div>
            <el-table :data="stats.submissions" size="small" max-height="300">
              <el-table-column label="学生" prop="username" min-width="100" align="center" />
              <el-table-column label="提交时间" min-width="160" align="center">
                <template #default="{ row }">{{ formatTime(row.submit_time) }}</template>
              </el-table-column>
              <el-table-column label="总分" width="80" align="center">
                <template #default="{ row }">
                  <span style="font-weight: 600; color: #2d8cf0">{{ row.total_score }}</span>
                </template>
              </el-table-column>
              <el-table-column label="迟交" width="70" align="center">
                <template #default="{ row }">
                  <el-tag v-if="row.is_late" type="warning" size="small">迟交</el-tag>
                  <el-tag v-else type="success" size="small">正常</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="批阅" width="70" align="center">
                <template #default="{ row }">
                  <el-tag v-if="row.is_graded" type="success" size="small">已阅</el-tag>
                  <el-tag v-else type="info" size="small">待阅</el-tag>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </template>
      </div>
      <template #footer>
        <el-button @click="showStatsDialog = false">关闭</el-button>
        <el-button type="primary" @click="goToGradingFromStats">前往评分</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import api from '@oj/api'
import echarts from '@/utils/echarts'
import { ElMessageBox } from 'element-plus'
import time from '@/utils/time'
import AssignmentGrading from './AssignmentGrading.vue'
import AssignmentDetail from './AssignmentDetail.vue'
import { Plus, Delete as DeleteIcon, Search, Close, Reading, View, Edit, Link, MagicStick, DocumentAdd } from '@element-plus/icons-vue'
import Pagination from '@/components/Pagination.vue'

export default {
  name: 'ClassroomAssignment',
  components: { AssignmentGrading, AssignmentDetail, Plus, DeleteIcon, Search, Close, Reading, View, Edit, Link, MagicStick, DocumentAdd, Pagination },
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
  data () {
    return {
      assignments: [],
      loading: false,
      assignmentPage: 1,
      assignmentPageSize: 10,

      showCreateModal: false,
      formMode: 'create',
      submitting: false,
      gradingAssignmentId: null,
      viewingAssignmentId: null,
      form: {
        title: '',
        description: '',
        time_range: [],
        allow_late_submission: false,
        late_penalty: 0.8,
        allow_ai_tutor: true,
        sections: [],
        compose_strategy: 'manual',
        target_kc_ids: [],
        per_student_budget: 3,
        total_problem_budget: 8
      },

      kcCascadeOptions: [],
      kcCascaderProps: {
        multiple: true,
        emitPath: false,
        value: 'id',
        label: 'name',
        children: 'kcs',
        checkStrictly: false
      },
      smartComposePreviewing: false,
      smartComposeResult: null,

      showProblemSelector: false,
      currentSectionIndex: -1,
      searchProblemKeyword: '',
      problemSearchResults: [],
      searchProblemLoading: false,
      searchProblemTotal: 0,
      searchProblemPage: 1,
      selectedProblems: [],

      showStatsDialog: false,
      statsLoading: false,
      statsTitle: '',
      statsAssignmentId: null,
      stats: null,
      scoreDistChartInstance: null
    }
  },
  computed: {
    pagedAssignments () {
      const start = (this.assignmentPage - 1) * this.assignmentPageSize
      return this.assignments.slice(start, start + this.assignmentPageSize)
    },
    submitRate () {
      if (!this.stats || !this.stats.total_students) return 0
      return Math.round((this.stats.submitted_count / this.stats.total_students) * 100)
    },
    flatSmartComposePreview () {
      if (!this.smartComposeResult || !Array.isArray(this.smartComposeResult.sections)) return []
      const rows = []
      for (const section of this.smartComposeResult.sections) {
        for (const problem of (section.problems || [])) {
          rows.push({
            kc_name: section.title,
            title: problem.title,
            difficulty: problem.difficulty,
            card_type: problem.card_type
          })
        }
      }
      return rows
    }
  },
  mounted () {
    this.loadAssignments()
    this.loadKcOptions()
  },
  beforeUnmount () {
    if (this.scoreDistChartInstance) {
      this.scoreDistChartInstance.dispose()
      this.scoreDistChartInstance = null
    }
  },
  methods: {
    getStatusText (status) {
      const map = { scheduled: '未开始', ongoing: '进行中', ended: '已截止' }
      return map[status] || '已截止'
    },
    getStatusStyle (status) {
      const map = {
        scheduled: { color: '#e6a23c', backgroundColor: '#fdf6ec', borderColor: '#faecd8' },
        ongoing: { color: '#000000', backgroundColor: '#d4edda', borderColor: '#c3e6cb' },
        ended: { color: '#495057', backgroundColor: '#e9ecef', borderColor: '#ced4da' }
      }
      return map[status] || map.ended
    },
    loadAssignments () {
      this.loading = true
      api.getClassroomAssignments(this.classroomId).then(res => {
        this.assignments = res.data.data.results
        this.loading = false
      }).catch(() => {
        this.loading = false
      })
    },

    loadKcOptions () {
      if (!this.isStaff || typeof api.getAIGeneratedKcOptions !== 'function') return
      api.getAIGeneratedKcOptions(this.classroomId).then(res => {
        const data = (res && res.data && res.data.data) || {}
        const chapters = Array.isArray(data.chapters) ? data.chapters : []
        this.kcCascadeOptions = chapters.map(chapter => ({
          id: 'chapter_' + (chapter.chapter_id || 'none'),
          name: chapter.chapter_title || '未分组',
          kcs: (chapter.kcs || []).map(kc => ({ id: kc.id, name: kc.name }))
        }))
      }).catch(() => {
        this.kcCascadeOptions = []
      })
    },

    onComposeStrategyChange () {
      if (this.form.compose_strategy !== 'smart_kc') {
        this.smartComposeResult = null
      }
    },

    previewSmartCompose () {
      const targetKcIds = (this.form.target_kc_ids || []).map(v => Number(v)).filter(v => Number.isFinite(v))
      this.smartComposePreviewing = true
      this.smartComposeResult = null
      api.previewClassroomAssignmentSmartCompose(this.classroomId, {
        target_kc_ids: targetKcIds.length ? targetKcIds : undefined,
        per_student_budget: this.form.per_student_budget,
        total_problem_budget: this.form.total_problem_budget
      }).then(res => {
        this.smartComposeResult = (res && res.data && res.data.data) || null
        this.smartComposePreviewing = false
        if (this.smartComposeResult && this.smartComposeResult.total_picked === 0) {
          this.$warning('未选出题，请调整 KC 或预算')
        }
      }).catch(err => {
        this.smartComposePreviewing = false
        const msg = (err && err.response && err.response.data && err.response.data.error) || '智能组卷预览失败'
        this.$error(msg)
      })
    },

    difficultyTagType (level) {
      const map = { Low: 'success', Easy: 'success', Mid: 'warning', Medium: 'warning', High: 'danger', Hard: 'danger' }
      return map[level] || 'info'
    },
    cardTypeLabel (cardType) {
      const map = { coding_problem: '编程', objective_problem: '客观题' }
      return map[cardType] || cardType || '—'
    },

    applySmartComposeAsSections () {
      if (!this.smartComposeResult || !Array.isArray(this.smartComposeResult.sections)) return
      const sections = this.smartComposeResult.sections
        .filter(section => Array.isArray(section.problems) && section.problems.length > 0)
        .map((section, idx) => ({
          title: section.title || `KC ${idx + 1}`,
          description: '',
          order: idx,
          problems: section.problems.map((p, pIdx) => ({
            problem_id: p.problem_id,
            title: p.title,
            _id: p.problem_key,
            problem_display_id: p.problem_key,
            score: 10,
            order: pIdx
          }))
        }))
      this.form.sections = sections
      this.$success('已写入到作业板块，可继续手动调整')
    },
    editAssignment (assignment) {
      this.formMode = 'edit'
      const rawSections = JSON.parse(JSON.stringify(assignment.sections || []))
      const sections = rawSections.map(s => ({
        title: s.title,
        description: s.description || '',
        order: s.order || 0,
        problems: (s.problems || []).map(p => this.normalizeProblem(p))
      }))
      this.form = {
        id: assignment.id,
        title: assignment.title,
        description: assignment.description,
        time_range: [new Date(assignment.start_time), new Date(assignment.end_time)],
        allow_late_submission: assignment.allow_late_submission,
        late_penalty: assignment.late_penalty,
        allow_ai_tutor: assignment.allow_ai_tutor !== false,
        sections: sections,
        compose_strategy: assignment.compose_strategy || 'manual',
        target_kc_ids: Array.isArray(assignment.target_kc_ids) ? assignment.target_kc_ids.map(v => Number(v)).filter(v => Number.isFinite(v)) : [],
        per_student_budget: 3,
        total_problem_budget: 8
      }
      this.smartComposeResult = null
      this.showCreateModal = true
    },
    normalizeProblem (p) {
      if (p.problem && typeof p.problem === 'object') {
        return {
          problem_id: p.problem.id,
          title: p.problem.title,
          _id: p.problem._id || p.problem.problem_display_id,
          problem_display_id: p.problem._id || p.problem.problem_display_id,
          score: p.score || 10,
          order: p.order || 0
        }
      }
      return p
    },
    openProblemPage (problem) {
      const displayId = problem._id || problem.problem_display_id
      if (displayId) {
        window.open(`/problem/${displayId}`, '_blank')
      }
    },
    openGrading (assignment) {
      this.gradingAssignmentId = assignment.id
    },
    openGradingFromView (assignmentId) {
      this.viewingAssignmentId = null
      this.gradingAssignmentId = assignmentId
    },
    confirmDeleteAssignment (assignment) {
      ElMessageBox.confirm(
        `<p>确定要删除作业 <b>${assignment.title}</b> 吗？</p><p style="color: #ed4014; margin-top: 8px;">删除后，该作业的所有板块、题目配置及提交记录将被永久移除，此操作不可撤销。</p>`,
        '确认删除作业',
        {
          confirmButtonText: '删除',
          cancelButtonText: '取消',
          dangerouslyUseHTMLString: true,
          type: 'warning'
        }
      ).then(() => {
        api.deleteClassroomAssignment(this.classroomId, assignment.id).then(() => {
          this.$success('删除成功')
          this.loadAssignments()
        })
      }).catch(() => {})
    },
    submitAssignment () {
      if (!this.form.title || this.form.time_range.length !== 2) {
        this.$error('请填写完整信息')
        return
      }

      const payload = {
        title: this.form.title,
        description: this.form.description,
        start_time: this.form.time_range[0],
        end_time: this.form.time_range[1],
        allow_late_submission: this.form.allow_late_submission,
        late_penalty: this.form.late_penalty,
        allow_ai_tutor: this.form.allow_ai_tutor,
        sections: this.form.sections,
        compose_strategy: this.form.compose_strategy || 'manual'
      }
      if (this.form.compose_strategy === 'smart_kc') {
        payload.target_kc_ids = (this.form.target_kc_ids || []).map(v => Number(v)).filter(v => Number.isFinite(v))
        payload.per_student_budget = this.form.per_student_budget
        payload.total_problem_budget = this.form.total_problem_budget
      }

      this.submitting = true
      const request = this.formMode === 'create'
        ? api.createClassroomAssignment(this.classroomId, payload)
        : api.updateClassroomAssignment(this.classroomId, this.form.id, payload)

      request.then(() => {
        this.$success('操作成功')
        this.showCreateModal = false
        this.submitting = false
        this.loadAssignments()
      }).catch(() => {
        this.submitting = false
      })
    },

    addSection () {
      this.form.sections.push({
        title: `板块 ${this.form.sections.length + 1}`,
        description: '',
        order: this.form.sections.length,
        problems: []
      })
    },
    removeSection (index) {
      this.form.sections.splice(index, 1)
    },

    openProblemSelector (sectionIndex) {
      this.currentSectionIndex = sectionIndex
      this.showProblemSelector = true
      this.searchProblemKeyword = ''
      this.selectedProblems = []
      this.loadClassroomProblems()
    },
    removeProblem (sectionIndex, problemIndex) {
      this.form.sections[sectionIndex].problems.splice(problemIndex, 1)
    },
    loadClassroomProblems () {
      this.searchProblemLoading = true
      api.getClassroomProblems(this.classroomId, {
        offset: (this.searchProblemPage - 1) * 10,
        limit: 10,
        keyword: this.searchProblemKeyword
      }).then(res => {
        this.problemSearchResults = res.data.data.results
        this.searchProblemTotal = res.data.data.total
        this.searchProblemLoading = false
      }).catch(() => {
        this.searchProblemLoading = false
      })
    },
    handleSearchPageChange (page) {
      this.searchProblemPage = page
      this.loadClassroomProblems()
    },
    handleProblemSelection (selection) {
      this.selectedProblems = selection
    },
    confirmAddProblems () {
      if (this.selectedProblems.length === 0) {
        this.$error('请至少选择一道题目')
        return
      }

      const newProblems = this.selectedProblems.map(p => ({
        problem_id: p.id,
        title: p.title,
        score: 10,
        _id: p._id,
        problem_display_id: p._id
      }))

      this.form.sections[this.currentSectionIndex].problems.push(...newProblems)
      this.showProblemSelector = false
    },

    openStatsDialog (assignment) {
      this.statsTitle = assignment.title + ' — 数据统计'
      this.statsAssignmentId = String(assignment.id)
      this.stats = null
      this.showStatsDialog = true
      this.statsLoading = true
      api.getAssignmentStats(this.classroomId, assignment.id).then(res => {
        this.stats = res.data.data
        this.statsLoading = false
        this.$nextTick(() => this.renderScoreDistChart())
      }).catch(() => {
        this.statsLoading = false
      })
    },
    renderScoreDistChart () {
      const el = this.$refs.scoreDistChart
      if (!el || !this.stats) return
      if (this.scoreDistChartInstance) this.scoreDistChartInstance.dispose()
      const chart = echarts.init(el)
      this.scoreDistChartInstance = chart
      const dist = this.stats.score_distribution || []
      chart.setOption({
        tooltip: { trigger: 'axis' },
        grid: { top: 16, right: 16, bottom: 28, left: 40 },
        xAxis: { type: 'category', data: dist.map(d => d.range), axisLabel: { fontSize: 12 } },
        yAxis: { type: 'value', minInterval: 1 },
        series: [{
          type: 'bar',
          data: dist.map(d => d.count),
          itemStyle: { color: '#409eff', borderRadius: [4, 4, 0, 0] },
          barMaxWidth: 48,
          label: { show: true, position: 'top', fontSize: 12 }
        }]
      })
    },
    goToGradingFromStats () {
      this.showStatsDialog = false
      this.gradingAssignmentId = this.statsAssignmentId
    },

    formatTime (timeStr) {
      return time.utcToLocal(timeStr, 'YYYY-MM-DD HH:mm')
    }
  }
}
</script>

<style lang="less" scoped>
.smart-compose-panel {
  border: 1px solid #d9ecff;
  border-radius: 12px;
  padding: 14px 18px 4px;
  margin: 4px 0 12px;
  background: linear-gradient(180deg, #f5faff 0%, #fbfdff 100%);

  .smart-compose-header {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 13px;
    font-weight: 600;
    color: #1f2937;
    margin: -4px 0 10px;

    .el-icon {
      color: #2d8cf0;
    }
  }

  .smart-compose-hint {
    margin-left: 12px;
    color: #909399;
    font-size: 12px;
  }

  .smart-compose-result {
    margin-top: 8px;
    border-top: 1px dashed #d9ecff;
    padding-top: 10px;

    .smart-compose-summary {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 4px 8px;
      margin-bottom: 10px;

      .summary-pill {
        display: inline-flex;
        align-items: center;
        padding: 2px 10px;
        border-radius: 999px;
        font-size: 12px;
        font-weight: 600;

        &.primary {
          color: #2d8cf0;
          background: rgba(45, 140, 240, 0.08);
        }
      }

      .summary-label {
        font-size: 12px;
        color: #606266;
      }
    }

    .smart-compose-table {
      :deep(.el-table__inner-wrapper) {
        border-radius: 8px;
        overflow: hidden;
      }
    }
  }
}

.classroom-assignment {
  :deep(.el-card__header) {
    padding: 10px 16px;
  }

  .panel-header {
    display: flex;
    justify-content: space-between;
    align-items: center;

    .panel-title {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      font-size: 14px;
      font-weight: 600;
      color: #17233d;
    }
  }
}

.sections-manager {
  margin-top: 20px;
  padding: 10px;
  background: #f8f8f9;
  border-radius: 4px;

  .section-item {
    background: #fff;
    padding: 10px;
    margin-bottom: 10px;
    border: 1px solid #dcdee2;
    border-radius: 4px;

    .section-header {
      display: flex;
      justify-content: space-between;
      margin-bottom: 10px;
    }

    .problem-item {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 5px 0;
      border-bottom: 1px dashed #e8eaec;

      .problem-link {
        cursor: pointer;
        color: #2d8cf0;
        &:hover {
          text-decoration: underline;
        }
      }

      .problem-config {
        display: flex;
        align-items: center;
        gap: 10px;
      }
    }
  }
}

.stats-dialog-body {
  min-height: 200px;
}

.stats-overview {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 20px;
}

.stats-card {
  flex: 1;
  min-width: 100px;
  padding: 14px 16px;
  border-radius: 8px;
  background: #f5f7fa;
  text-align: center;
}

.stats-card-value {
  font-size: 24px;
  font-weight: 700;
  color: #303133;
  line-height: 1.2;
}

.stats-card-sub {
  font-size: 14px;
  font-weight: 400;
  color: #909399;
}

.stats-card-label {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.stats-card-extra {
  font-size: 12px;
  color: #409eff;
  margin-top: 2px;
  font-weight: 500;
}

.stats-section {
  margin-bottom: 20px;
}

.stats-section-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 10px;
  padding-bottom: 6px;
  border-bottom: 1px solid #ebeef5;
}

.problem-stat-section {
  display: block;
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
  margin-top: 8px;
}

.problem-stat-row {
  margin-bottom: 6px;
}

.problem-stat-bar {
  display: flex;
  align-items: center;
  gap: 10px;
}

.problem-stat-name {
  width: 140px;
  font-size: 13px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex-shrink: 0;
}

.problem-stat-score {
  width: 40px;
  font-size: 12px;
  color: #909399;
  flex-shrink: 0;
  text-align: right;
}

.problem-stat-rate {
  width: 56px;
  font-size: 12px;
  color: #67c23a;
  font-weight: 500;
  flex-shrink: 0;
  text-align: right;
}

.score-dist-chart {
  height: 200px;
  width: 100%;
}
</style>
