<template>
  <div class="assignment-detail">
    <el-card v-if="assignment">
      <template #header>
        <div>
          <el-button text @click="$emit('back')" style="margin-right: 8px;">
            <el-icon><ArrowLeft /></el-icon>
            返回
          </el-button>
          <span style="font-size: 18px; font-weight: 600;">{{ assignment.title }}</span>
          <p style="color: #808695; margin-top: 6px;">
            截止时间：{{ formatTime(assignment.end_time) }}
            <span v-if="isExpired" class="assignment-status assignment-status-ended">已截止</span>
            <span v-else class="assignment-status assignment-status-ongoing">进行中</span>
          </p>
        </div>
      </template>

      <div v-if="assignment.description" class="assignment-desc">
        {{ assignment.description }}
      </div>

      <div v-for="section in sections" :key="section.id" class="section-block">
        <h3 class="section-title">{{ section.title }}</h3>
        <p v-if="section.description" class="section-desc">{{ section.description }}</p>

        <div v-for="(prob, idx) in section.problems" :key="prob.id" class="problem-item">
          <div class="problem-header">
            <span class="problem-index">{{ idx + 1 }}.</span>
            <span class="problem-title">{{ prob.title || '未知题目' }}</span>
            <el-tag :type="getTypeColor(prob.question_type)" :style="getTypeTagStyle(prob.question_type)">{{ getTypeLabel(prob.question_type) }}</el-tag>
            <span class="problem-score">{{ prob.score }} 分</span>
          </div>

          <div v-if="isCodingProblem(prob)" class="problem-body">
            <el-button :type="viewOnly ? '' : 'primary'" @click="openOJProblem(prob)">
              <el-icon style="margin-right: 4px;"><Link /></el-icon>
              {{ viewOnly ? '查看题目' : '前往 OJ 提交代码' }}
            </el-button>
            <span
              v-if="!viewOnly"
              class="assignment-submission-status"
              :style="getSubmissionStatusStyle(getAITutorStatus(prob).label)">
              AI：{{ getAITutorStatus(prob).text }}
            </span>
            <span
              v-if="!viewOnly && getMySubmissionStatus(prob)"
              class="assignment-submission-status"
              :style="getSubmissionStatusStyle(getMySubmissionStatus(prob))">
              {{ getMySubmissionStatus(prob) }}
            </span>
          </div>

          <div v-else-if="prob.question_type === 'choice'" class="problem-body">
            <div v-html="sanitize(prob.description)" class="problem-description"></div>
            <el-radio-group v-model="answers[String(prob.id)]" style="display: flex; flex-direction: column; gap: 8px;">
              <el-radio v-for="opt in (prob.options || [])" :key="opt.label" :value="opt.label" :disabled="viewOnly">
                {{ opt.label }}. {{ opt.text }}
              </el-radio>
            </el-radio-group>
          </div>

          <div v-else-if="prob.question_type === 'fill_blank'" class="problem-body">
            <div v-html="sanitize(prob.description)" class="problem-description"></div>
            <div v-for="(blank, bi) in (prob.blanks || [''])" :key="bi" style="margin-bottom: 8px;">
              <span>空{{ bi + 1 }}：</span>
              <el-input v-model="fillAnswers[String(prob.id) + '_' + bi]" :readonly="viewOnly" :placeholder="viewOnly ? '教师端仅查看，不可作答' : '输入答案'" style="width: 300px;"/>
            </div>
          </div>

          <div v-else class="problem-body">
            <div v-html="sanitize(prob.description)" class="problem-description"></div>
            <el-input v-model="answers[String(prob.id)]" type="textarea" :rows="3" :readonly="viewOnly" :placeholder="viewOnly ? '教师端仅查看，不可作答' : '输入答案'"/>
          </div>
        </div>
      </div>

      <div v-if="!viewOnly" class="submit-bar">
        <el-button type="primary" size="large" :loading="submitting" @click="submitAssignment">
          提交作业
        </el-button>
      </div>
    </el-card>

    <div v-else-if="loading" v-loading="true" style="min-height: 200px;"></div>
    <div v-else style="text-align: center; padding: 60px; color: #808695;">
      作业不存在或无权查看
    </div>
  </div>
</template>

<script>
import api from '@oj/api'
import time from '@/utils/time'
import { sanitize } from '@/utils/sanitize'
import { ArrowLeft, Link } from '@element-plus/icons-vue'

export default {
  name: 'AssignmentDetail',
  components: { ArrowLeft, Link },
  props: {
    classroomId: { type: String, required: true },
    assignmentId: { type: String, required: true },
    isStaff: { type: Boolean, default: false },
    viewOnly: { type: Boolean, default: false }
  },
  data () {
    return {
      assignment: null,
      sections: [],
      loading: true,
      submitting: false,
      answers: {},
      fillAnswers: {},
      mySubmissions: {}
    }
  },
  computed: {
    isExpired () {
      if (!this.assignment || !this.assignment.end_time) return false
      return new Date(this.assignment.end_time) < new Date()
    }
  },
  mounted () {
    this.loadAssignment()
  },
  methods: {
    sanitize,
    loadAssignment () {
      this.loading = true
      api.getClassroomAssignment(this.classroomId, this.assignmentId).then(res => {
        const data = res.data.data
        this.assignment = data
        this.sections = this.normalizeSections(data.sections || [])
        this._syncSubmissionStatus()
        this.loading = false
      }).catch(() => {
        this.loading = false
      })
    },
    normalizeSections (sections) {
      return (sections || []).map(section => {
        const normalizedProblems = (section.problems || []).map(prob => {
          const cp = prob.problem || {}
          return {
            ...prob,
            title: prob.title || cp.title || '未知题目',
            _id: prob._id || cp._id || cp.problem_display_id,
            problem_display_id: prob.problem_display_id || cp.problem_display_id,
            problem_id: prob.problem_id || cp.problem_id,
            question_type: prob.question_type || cp.question_type || 'coding',
            description: prob.description || cp.description || '',
            options: prob.options || cp.options || [],
            blanks: prob.blanks || cp.blanks || []
          }
        })
        return {
          ...section,
          problems: normalizedProblems
        }
      })
    },
    _syncSubmissionStatus () {
      for (const section of this.sections) {
        for (const prob of (section.problems || [])) {
          if (prob.my_status) {
            this.mySubmissions[prob.id] = prob.my_status
          }
        }
      }
    },

    isCodingProblem (prob) {
      const qt = prob.question_type || ''
      return qt === 'coding' || qt === '' || qt === 'ACM' || qt === 'OI'
    },

    openOJProblem (prob) {
      const displayId = prob._id || prob.problem_display_id || prob.problem_id
      if (displayId) {
        if (this.viewOnly) {
          window.open(`/problem/${displayId}`, '_blank')
          return
        }
        const aiAllowed = prob.ai_tutor_allowed !== false
        const aiReason = prob.ai_tutor_disabled_reason || ''
        const antiCheating = (this.assignment && this.assignment.anti_cheating_enabled) ? '1' : '0'
        const query = [
          `from=assignment`,
          `classroom_id=${encodeURIComponent(this.classroomId)}`,
          `assignment_id=${encodeURIComponent(this.assignmentId)}`,
          `ai_tutor_allowed=${aiAllowed ? '1' : '0'}`,
          `anti_cheating=${antiCheating}`
        ]
        if (aiReason) {
          query.push(`ai_tutor_reason=${encodeURIComponent(aiReason)}`)
        }
        window.open(`/problem/${displayId}?${query.join('&')}`, '_blank')
      } else {
        this.$Message.warning('题目链接暂不可用')
      }
    },
    getAITutorStatus (prob) {
      if (prob.ai_tutor_allowed === false) {
        const reason = String(prob.ai_tutor_disabled_reason || '')
        const textMap = {
          disabled_by_teacher: '教师已关闭',
          assignment_ended: '作业已截止',
          already_ac: '已 AC 自动关闭'
        }
        return { label: 'WA', text: textMap[reason] || '当前不可用' }
      }
      return { label: 'AC', text: '可用' }
    },

    getMySubmissionStatus (prob) {
      return this.mySubmissions[prob.id] || null
    },

    submitAssignment () {
      if (this.viewOnly) {
        this.$Message.warning('教师端仅支持查看作业，不可提交')
        return
      }
      this.submitting = true
      const payload = {
        answers: this.answers,
        fill_answers: this.fillAnswers
      }

      api.submitAssignment(this.classroomId, this.assignmentId, payload).then(() => {
        this.$success('提交成功')
        this.$emit('submitted')
        this.$emit('back')
        this.submitting = false
      }).catch(() => {
        this.$error('提交失败')
        this.submitting = false
      })
    },

    normalizeQuestionType (qt) {
      return String(qt || '').toLowerCase()
    },
    getTypeColor (qt) {
      const t = this.normalizeQuestionType(qt)
      return { choice: 'primary', fill_blank: 'success', coding: 'warning', scaffolding: 'info' }[t] || 'info'
    },
    getTypeLabel (qt) {
      const t = this.normalizeQuestionType(qt)
      return { choice: '选择', fill_blank: '填空', coding: '编程', scaffolding: '编程填空' }[t] || '编程'
    },
    getTypeTagStyle (qt) {
      const t = this.normalizeQuestionType(qt)
      if (t === 'scaffolding') {
        return { color: '#000000', borderColor: '#ffe7ba', backgroundColor: '#fff7e6' }
      }
      return {}
    },
    getStatusColor (s) {
      if (s === 'AC') return 'success'
      if (s === 'Pending') return 'warning'
      return 'danger'
    },
    getSubmissionStatusStyle (s) {
      const status = String(s || '').toUpperCase()
      if (status === 'AC') {
        return { color: '#000000', backgroundColor: '#d4edda', borderColor: '#c3e6cb' }
      }
      if (status === 'PENDING') {
        return { color: '#000000', backgroundColor: '#fff7e6', borderColor: '#ffd591' }
      }
      return { color: '#000000', backgroundColor: '#fff1f0', borderColor: '#ffa39e' }
    },
    formatTime (t) {
      return time.utcToLocal(t, 'YYYY-MM-DD HH:mm')
    }
  }
}
</script>

<style lang="less" scoped>
.assignment-detail {
  max-width: 900px;
  margin: 0 auto;

  .assignment-status {
    display: inline-block;
    margin-left: 8px;
    padding: 1px 8px;
    border-radius: 4px;
    border: 1px solid transparent;
    font-size: 12px;
    line-height: 20px;
    vertical-align: middle;
  }

  .assignment-status-ended {
    color: #a8071a;
    background: #fff1f0;
    border-color: #ffa39e;
  }

  .assignment-status-ongoing {
    color: #000000;
    background: #f6ffed;
    border-color: #b7eb8f;
  }

  .assignment-submission-status {
    display: inline-block;
    margin-left: 12px;
    padding: 1px 8px;
    border-radius: 4px;
    border: 1px solid transparent;
    font-size: 12px;
    line-height: 20px;
    vertical-align: middle;
    font-weight: 600;
  }

  .assignment-desc {
    color: #515a6e;
    margin-bottom: 20px;
    padding: 12px;
    background: #f8f8f9;
    border-radius: 4px;
  }

  .section-block {
    margin-bottom: 24px;

    .section-title {
      font-size: 18px;
      font-weight: 600;
      color: #2c3e50;
      margin-bottom: 8px;
      padding-bottom: 8px;
      border-bottom: 2px solid #2d8cf0;
    }

    .section-desc {
      color: #808695;
      margin-bottom: 12px;
    }
  }

  .problem-item {
    margin-bottom: 20px;
    padding: 16px;
    border: 1px solid #e8eaec;
    border-radius: 6px;
    background: #fff;

    .problem-header {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 12px;

      .problem-index {
        font-weight: 600;
        color: #2d8cf0;
      }

      .problem-title {
        font-weight: 500;
        flex: 1;
      }

      .problem-score {
        color: #808695;
        font-size: 13px;
      }
    }

    .problem-description {
      margin-bottom: 12px;
      line-height: 1.6;
    }
  }

  .submit-bar {
    text-align: center;
    padding: 20px 0;
    border-top: 1px solid #e8eaec;
    margin-top: 20px;
  }
}
</style>
