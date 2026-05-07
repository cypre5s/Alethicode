<template>
  <div class="assignment-grading">
    <el-card>
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <div>
            <el-icon><Edit /></el-icon>
            助教评分 - {{ assignment.title || '' }}
          </div>
          <div>
            <el-button text @click="$emit('back')">
              <el-icon><ArrowLeft /></el-icon>
              返回
            </el-button>
          </div>
        </div>
      </template>

      <el-alert v-if="!submissions.length && !loading" type="info" title="暂无学生提交" show-icon :closable="false" />

      <el-table :data="submissions" v-loading="loading">
        <el-table-column label="学生" prop="username" min-width="120" align="center" />
        <el-table-column label="提交时间" width="180" align="center">
          <template #default="scope">
            {{ scope.row.submit_time ? formatTime(scope.row.submit_time) : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="迟交" width="80" align="center">
          <template #default="scope">
            <el-tag v-if="scope.row.is_late" type="warning">迟交</el-tag>
            <el-tag v-else type="success">正常</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="总分" width="100" align="center">
          <template #default="scope">
            <span style="font-weight: bold; color: #2d8cf0">{{ scope.row.total_score }}</span>
          </template>
        </el-table-column>
        <el-table-column label="已评阅" width="100" align="center">
          <template #default="scope">
            <el-tag v-if="scope.row.is_graded" type="success">是</el-tag>
            <el-tag v-else type="info">否</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center">
          <template #default="scope">
            <el-button type="primary" size="small" @click="openGrading(scope.row)">评分</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    <el-dialog v-model="showGradeModal" :title="'评分 - ' + (currentStudent.username || '')" width="800px">
      <div v-if="currentDetails.length === 0" style="text-align: center; color: #808695; padding: 40px;">
        该学生暂无详细提交记录
      </div>
      <div v-for="detail in currentDetails" :key="detail.id" class="grade-item">
        <div class="grade-item-header">
          <span>题目 ID: {{ detail.problem_id }}</span>
          <el-tag v-if="detail.judge_status" :type="detail.judge_status === 'AC' ? 'success' : 'danger'">
            裁判机: {{ detail.judge_status }} ({{ detail.judge_score != null ? detail.judge_score : '-' }})
          </el-tag>
          <el-tag v-if="detail.error_taxonomy" type="warning" style="margin-left: 6px;">
            错误类型: {{ detail.error_taxonomy }}
          </el-tag>
        </div>

        <div v-if="detail.code" class="code-block">
          <p><strong>代码：</strong></p>
          <pre>{{ detail.code }}</pre>
        </div>
        <div v-if="detail.answer" class="answer-block">
          <p><strong>答案：</strong> {{ detail.answer }}</p>
        </div>

        <MisconceptionTagCloud
          v-if="detail.recent_misconceptions && detail.recent_misconceptions.length"
          :misconceptions="formatMisconceptions(detail.recent_misconceptions)"
        />

        <div v-if="detail.linked_review_package" class="review-package-card">
          <div class="rpc-title">
            <el-icon><Notebook /></el-icon>
            自动创建的错题复习包
          </div>
          <div class="rpc-grid">
            <div class="rpc-cell">
              <span class="rpc-label">错题分类</span>
              <span class="rpc-value">{{ detail.linked_review_package.error_taxonomy || '—' }}</span>
            </div>
            <div class="rpc-cell">
              <span class="rpc-label">是否掌握</span>
              <el-tag :type="detail.linked_review_package.mastery_reached ? 'success' : 'info'" size="small">
                {{ detail.linked_review_package.mastery_reached ? '已掌握' : '未掌握' }}
              </el-tag>
            </div>
            <div class="rpc-cell">
              <span class="rpc-label">下次复习</span>
              <span class="rpc-value">{{ detail.linked_review_package.due_at || '—' }}</span>
            </div>
          </div>
          <a class="rpc-link" :href="reviewPackageHref(detail.linked_review_package.id, currentStudent.user_id)" target="_blank">
            查看复习包详情
            <el-icon><ArrowRight /></el-icon>
          </a>
        </div>

        <div class="grade-inputs">
          <span>TA 评分：</span>
          <el-input-number v-model="gradeForm[detail.id]" :min="0" :max="100" style="width: 120px;"/>
          <span style="margin-left: 12px;">评语：</span>
          <el-input v-model="commentForm[detail.id]" placeholder="选填" style="width: 250px;"/>
          <el-button type="primary" size="small" style="margin-left: 12px;"
                  :loading="!!gradingIds[detail.id]"
                  @click="gradeDetail(detail)">
            确认评分
          </el-button>
          <el-tag v-if="detail.ta_score != null" type="primary" style="margin-left: 8px;">
            已评: {{ detail.ta_score }}
          </el-tag>
        </div>
      </div>

      <template #footer>
        <el-button @click="showGradeModal = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import api from '@oj/api'
import time from '@/utils/time'
import { Edit, ArrowLeft, ArrowRight, Notebook } from '@element-plus/icons-vue'
import MisconceptionTagCloud from '@/pages/oj/views/user/notebook/MisconceptionTagCloud.vue'

export default {
  name: 'AssignmentGrading',
  components: { Edit, ArrowLeft, ArrowRight, Notebook, MisconceptionTagCloud },
  props: {
    classroomId: { type: String, required: true },
    assignmentId: { type: String, required: true }
  },
  data () {
    return {
      assignment: {},
      submissions: [],
      loading: false,
      showGradeModal: false,
      currentStudent: {},
      currentDetails: [],
      gradeForm: {},
      commentForm: {},
      gradingIds: {}
    }
  },
  mounted () {
    this.loadSubmissions()
    this.loadAssignment()
  },
  methods: {
    formatTime (t) {
      return time.utcToLocal(t, 'YYYY-MM-DD HH:mm')
    },
    loadAssignment () {
      api.getClassroomAssignment(this.classroomId, this.assignmentId).then(res => {
        this.assignment = res.data.data || {}
      }).catch(() => {})
    },

    loadSubmissions () {
      this.loading = true
      api.getAssignmentSubmissions(this.classroomId, this.assignmentId).then(res => {
        this.submissions = (res.data.data && res.data.data.results) || []
        this.loading = false
      }).catch(() => {
        this.loading = false
      })
    },

    openGrading (row) {
      this.currentStudent = row
      this.currentDetails = row.details || []
      this.gradeForm = {}
      this.commentForm = {}
      this.currentDetails.forEach(d => {
        this.gradeForm[d.id] = d.ta_score != null ? d.ta_score : (d.judge_score != null ? d.judge_score : 0)
        this.commentForm[d.id] = d.ta_comment || ''
      })
      this.showGradeModal = true
    },

    gradeDetail (detail) {
      this.gradingIds[detail.id] = true
      api.gradeAssignmentProblem(
        this.classroomId,
        this.assignmentId,
        detail.id,
        {
          ta_score: this.gradeForm[detail.id],
          ta_comment: this.commentForm[detail.id] || ''
        }
      ).then(res => {
        this.$success('评分成功')
        detail.ta_score = this.gradeForm[detail.id]
        this.gradingIds[detail.id] = false
        this.loadSubmissions()
      }).catch(() => {
        this.$error('评分失败')
        this.gradingIds[detail.id] = false
      })
    },

    formatMisconceptions (raw) {
      if (!Array.isArray(raw)) return []
      return raw.map(item => ({
        id: item.taxonomy,
        name: item.label || item.taxonomy,
        description: '最近一次：' + (item.last_at || '—'),
        trigger_count: item.count || 0
      }))
    },

    reviewPackageHref (packageId, userId) {
      if (!packageId) return '#'
      const path = `/user-home/${userId || ''}/notebook/review/${packageId}`
      return path
    }
  }
}
</script>

<style lang="less" scoped>
.assignment-grading {
  .grade-item {
    border: 1px solid #e8eaec;
    border-radius: 6px;
    padding: 16px;
    margin-bottom: 16px;

    .grade-item-header {
      display: flex;
      align-items: center;
      gap: 12px;
      margin-bottom: 12px;
      font-weight: 500;
    }

    .code-block {
      pre {
        background: #f8f8f9;
        padding: 12px;
        border-radius: 4px;
        font-size: 13px;
        max-height: 300px;
        overflow-y: auto;
        border: 1px solid #e8eaec;
      }
      margin-bottom: 12px;
    }

    .answer-block {
      margin-bottom: 12px;
      padding: 8px 12px;
      background: #f8f8f9;
      border-radius: 4px;
    }

    .grade-inputs {
      display: flex;
      align-items: center;
      margin-top: 12px;
      padding-top: 12px;
      border-top: 1px dashed #e8eaec;
    }

    .review-package-card {
      margin: 12px 0;
      padding: 14px 18px;
      border: 1px solid #faecd8;
      border-radius: 10px;
      background: linear-gradient(180deg, #fefaf0 0%, #fff7e6 100%);
      transition: box-shadow 0.2s, transform 0.2s;

      &:hover {
        box-shadow: 0 4px 14px rgba(212, 136, 6, 0.12);
        transform: translateY(-1px);
      }

      .rpc-title {
        display: flex;
        align-items: center;
        gap: 6px;
        font-weight: 600;
        font-size: 13px;
        color: #d48806;
        margin-bottom: 10px;
      }

      .rpc-grid {
        display: grid;
        grid-template-columns: repeat(3, 1fr);
        gap: 10px;
        margin-bottom: 6px;

        .rpc-cell {
          display: flex;
          flex-direction: column;
          gap: 4px;

          .rpc-label {
            font-size: 11px;
            text-transform: uppercase;
            letter-spacing: 0.4px;
            color: #b8860b;
          }

          .rpc-value {
            font-size: 13px;
            color: #2c2c2c;
            font-weight: 500;
          }
        }
      }

      .rpc-link {
        display: inline-flex;
        align-items: center;
        gap: 4px;
        margin-top: 6px;
        font-size: 12px;
        color: #2d8cf0;
        text-decoration: none;
        cursor: pointer;

        &:hover {
          text-decoration: underline;
        }
      }
    }
  }
}
</style>
