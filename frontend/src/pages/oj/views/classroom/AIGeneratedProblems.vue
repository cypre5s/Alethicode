<template>
  <div class="ai-problems">
    <el-card>
      <template #header><div class="card-header">
        <span class="card-title">
          <el-icon><MagicStick /></el-icon>
          AI 生成的题目
        </span>
        <div>
          <el-button v-if="isStaff" style="margin-right: 8px;" @click="exportReviewedAiProblems">
            <el-icon><Download /></el-icon>
            导出已审核选择/填空
          </el-button>
          <el-button v-if="isStaff" type="primary" @click="showGenerateModal = true">
            <el-icon><Plus /></el-icon>
            基于课件生成
          </el-button>
        </div>
      </div></template>
      
      <el-alert v-if="generating" type="info" show-icon closable>
        <template #title>
          <el-icon class="spin-icon" style="margin-right: 6px;"><Refresh /></el-icon>
          AI 正在生成题目，请稍候... {{ taskProgress }}
        </template>
      </el-alert>

      <el-alert v-if="taskError" type="error" show-icon closable @close="taskError = ''">
        <template #title>{{ taskError }}</template>
      </el-alert>

      <div v-if="!loading && problems.length === 0 && !generating" class="empty-state">
        <el-icon :size="60" color="#c5c8ce"><Document /></el-icon>
        <p style="margin-top: 15px; color: #808695;">暂无AI生成的题目</p>
        <p style="color: #c5c8ce; font-size: 13px;">点击"基于课件生成"按钮开始创建</p>
      </div>

      <el-table
        :data="problems"
        v-loading="loading"
        v-show="loading || problems.length > 0"
        :border="false"
        class="ai-table">
        <el-table-column label="题目标题" min-width="140" align="center">
          <template #default="scope">
            <a style="cursor: pointer; color: #2d8cf0" @click="viewProblem(scope.row)">{{ scope.row.title || 'AI题目' }}</a>
          </template>
        </el-table-column>
        <el-table-column label="题型" width="90" align="center">
          <template #default="scope">
            <el-tag :type="getTypeTagType(scope.row.question_type)">
              {{ getTypeLabel(scope.row.question_type || 'coding') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="难度" width="100" align="center">
          <template #default="scope">
            <el-tag :type="getDiffTagType(scope.row.difficulty)">
              {{ getDiffLabel(scope.row.difficulty || 'Mid') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="200" align="center">
          <template #default="scope">
            <span style="color: #808695;">
              {{ (scope.row.lesson_title || '') + (scope.row.page_start ? ` (第${scope.row.page_start}-${scope.row.page_end}页)` : '') }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110" align="center">
          <template #default="scope">
            <el-tag
              :style="getStatusStyle(scope.row)"
              disable-transitions>
              {{ getStatusText(scope.row) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="生成时间" width="150" align="center">
          <template #default="scope">{{ formatTime(scope.row.create_time) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="400" align="center">
          <template #default="scope">
            <el-button v-if="isStaff && canEditAiGeneratedProblem(scope.row)"
                       type="primary" link size="small"
                       @click="openEditProblem(scope.row)">编辑</el-button>
            <el-button v-if="isStaff && canReviewPassAiGeneratedProblem(scope.row)"
                       link size="small" style="color: #19be6b;"
                       @click="reviewPassProblem(scope.row)">审查通过</el-button>
            <el-button v-if="isStaff && canReviewRejectAiGeneratedProblem(scope.row)"
                       link size="small" style="color: #fa8c16;"
                       @click="reviewRejectProblem(scope.row)">审查驳回</el-button>
            <el-button v-if="isStaff && canValidateAiGeneratedProblem(scope.row)"
                       link size="small" style="color: #722ed1;"
                       @click="validateProblem(scope.row)">验证</el-button>
            <el-button v-if="isStaff && canPublishAiGeneratedProblem(scope.row)"
                       link size="small" style="color: #19be6b;"
                       @click="publishProblem(scope.row)">发布</el-button>
            <el-button v-if="isStaff && canDeleteAiGeneratedProblem(scope.row)"
                       type="danger" link size="small"
                       @click="deleteProblem(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination :total="total" 
              :current-page="page" 
              :page-size="limit"
              @current-change="onPageChange" 
              layout="prev, pager, next, jumper"/>
      </div>
    </el-card>
    <el-dialog v-model="showGenerateModal" title="基于课件生成题目" width="600">
      <el-form :model="generateForm" :label-width="100">
        <el-form-item label="选择课件">
          <el-select v-model="generateForm.lesson_id" placeholder="请选择课件" @change="onLessonSelect">
            <el-option v-for="lesson in lessons" :key="lesson.id" :value="lesson.id" :disabled="!isLessonSupportedForAiGeneration(lesson)">
              {{ lesson.title }} ({{ (lesson.lesson_type || lesson.file_type || '').toUpperCase() }})
            </el-option>
          </el-select>
        </el-form-item>
        
        <el-form-item label="页码范围">
          <el-input-number :min="1" :max="Math.max(1, selectedLessonMaxPages)" v-model="generateForm.page_start" placeholder="起始页"></el-input-number>
          -
          <el-input-number :min="1" :max="Math.max(1, selectedLessonMaxPages)" v-model="generateForm.page_end" placeholder="结束页"></el-input-number>
          <span v-if="selectedLessonMaxPages > 0" style="margin-left: 8px; color: #808695; font-size: 12px;">
            共 {{ selectedLessonMaxPages }} 页
          </span>
        </el-form-item>
        
        <el-form-item label="题目配置">
          <div v-for="type in questionTypes" :key="type.value" style="margin-bottom: 10px; display: flex; align-items: center; width: 100%;">
            <el-checkbox v-model="generateForm.types[type.value].checked" style="width: 140px; flex-shrink: 0;">
              {{ type.label }}
            </el-checkbox>
            <div v-if="generateForm.types[type.value].checked" style="display: flex; align-items: center;">
              <span style="margin-right: 10px;">数量:</span>
              <el-input-number :max="10" :min="1" v-model="generateForm.types[type.value].count" size="small"></el-input-number>
            </div>
          </div>
        </el-form-item>

        <div class="generate-section">
          <div class="generate-section-title">
            <el-icon><Connection /></el-icon>
            <span>知识点 & 策略</span>
          </div>
          <el-form-item label="目标知识点">
            <el-cascader
              v-model="selectedKcCascade"
              :options="kcCascadeOptions"
              :props="kcCascaderProps"
              collapse-tags
              collapse-tags-tooltip
              clearable
              filterable
              placeholder="按章节选择本班语言包内的 KC（留空走纯 LLM）"
              style="width: 100%">
            </el-cascader>
            <div class="form-hint">
              数据来自班级绑定的语言包真实 KC 树。留空时仅走 LLM 兜底分支。
            </div>
          </el-form-item>

          <el-form-item label="出题策略">
            <el-radio-group v-model="generateForm.prefer_strategy" class="strategy-radio-group">
              <el-radio value="lp_first">LP 优先（推荐）</el-radio>
              <el-radio value="llm_first">LLM 优先</el-radio>
              <el-radio value="lp_only">仅 LP 命中</el-radio>
              <el-radio value="llm_only">仅 LLM 生成</el-radio>
            </el-radio-group>
          </el-form-item>

          <el-form-item label="目标难度">
            <el-radio-group v-model="generateForm.target_difficulty">
              <el-radio label="">自动</el-radio>
              <el-radio label="Low">简单</el-radio>
              <el-radio label="Mid">中等</el-radio>
              <el-radio label="High">较难</el-radio>
            </el-radio-group>
          </el-form-item>
        </div>
      </el-form>
      <template #footer><div>
        <el-button @click="showGenerateModal = false">取消</el-button>
        <el-button type="primary" :loading="submitGenerating" @click="startGenerate">开始生成</el-button>
      </div></template>
    </el-dialog>
    <el-dialog v-model="showKcLabelModal" title="发布前请标注题目 KC" width="520" class="kc-label-dialog">
      <el-alert type="warning" show-icon :closable="false" style="margin-bottom: 14px;">
        <template #title>
          为了让自适应推荐 / 智能组卷选到这道题，发布前必须把它和班级语言包内的至少 1 个 KC 关联。
        </template>
      </el-alert>
      <div class="kc-label-form-block">
        <div class="form-hint" style="margin-bottom: 6px;">从下方章节中按需选择，可多选</div>
        <el-cascader
          v-model="kcLabelSelected"
          :options="kcCascadeOptions"
          :props="kcCascaderProps"
          collapse-tags
          collapse-tags-tooltip
          clearable
          filterable
          placeholder="按章节选择 KC"
          style="width: 100%">
        </el-cascader>
      </div>
      <template #footer><div>
        <el-button @click="cancelKcLabel">取消</el-button>
        <el-button type="primary" :loading="kcLabelSaving" @click="confirmKcLabel">
          <el-icon style="margin-right: 4px;"><Promotion /></el-icon>
          保存并发布
        </el-button>
      </div></template>
    </el-dialog>
    <el-dialog 
      v-model="showDetailModal" 
      :title="currentProblem.title" 
      width="900" 
      class="ai-problem-modal"
      top="20px">
      <div class="problem-detail-container">
        <el-tabs model-value="description">
          <el-tab-pane name="description">
            <template #label>
              <span><el-icon><Document /></el-icon> 题目描述</span>
            </template>
            <div class="detail-content">
              <div class="section-card">
                <div class="section-header">
                  <el-icon class="section-icon"><Document /></el-icon> 描述
                </div>
                <div class="section-body description-text" v-html="sanitize(currentProblem.description)"></div>
              </div>

              <div class="section-card" v-if="currentProblem.question_type === 'choice' && currentProblem.options && currentProblem.options.length">
                <div class="section-header">
                  <el-icon class="section-icon"><List /></el-icon> 选项
                </div>
                <div class="section-body options-list">
                  <div v-for="(opt, idx) in currentProblem.options" :key="idx" class="option-row">
                    <div class="option-label">{{ opt.label || String.fromCharCode(65 + idx) }}</div>
                    <div class="option-content">{{ opt.text }}</div>
                  </div>
                </div>
              </div>

              <div class="section-card" v-if="currentProblem.question_type === 'fill_blank' && currentProblem.blanks && currentProblem.blanks.length">
                <div class="section-header">
                  <el-icon class="section-icon"><Edit /></el-icon> 参考答案
                </div>
                <div class="section-body">
                  <el-tag v-for="(blank, idx) in currentProblem.blanks" :key="idx" type="success" style="margin-bottom: 6px; margin-right: 6px;">
                    第{{ idx + 1 }}空: {{ blank }}
                  </el-tag>
                </div>
              </div>

              <div class="section-card" v-if="currentProblem.explanation">
                <div class="section-header">
                  <el-icon class="section-icon"><Sunny /></el-icon> 解析
                </div>
                <div class="section-body description-text" v-html="sanitize(currentProblem.explanation)"></div>
              </div>
              
              <div class="section-card" v-if="currentProblem.input_description">
                <div class="section-header">
                  <el-icon class="section-icon"><Right /></el-icon> 输入格式
                </div>
                <div class="section-body description-text" v-html="sanitize(currentProblem.input_description)"></div>
              </div>
              
              <div class="section-card" v-if="currentProblem.output_description">
                <div class="section-header">
                  <el-icon class="section-icon"><Back /></el-icon> 输出格式
                </div>
                <div class="section-body description-text" v-html="sanitize(currentProblem.output_description)"></div>
              </div>
              
              <div class="section-card" v-if="currentProblem.samples && currentProblem.samples.length">
                <div class="section-header">
                  <el-icon class="section-icon"><Monitor /></el-icon> 样例
                </div>
                <div class="section-body">
                  <div v-for="(sample, index) in currentProblem.samples" :key="index" class="sample-box">
                    <div class="sample-header">Sample #{{ index + 1 }}</div>
                    <el-row :gutter="16">
                      <el-col :span="12">
                        <div class="io-title">Input</div>
                        <pre class="io-content">{{ sample.input }}</pre>
                      </el-col>
                      <el-col :span="12">
                        <div class="io-title">Output</div>
                        <pre class="io-content">{{ sample.output }}</pre>
                      </el-col>
                    </el-row>
                  </div>
                </div>
              </div>
            </div>
          </el-tab-pane>
          
          <el-tab-pane name="testcases">
            <template #label>
              <span><el-icon><Aim /></el-icon> 测试用例</span>
            </template>
            <div class="detail-content">
              <div class="section-card">
                <div class="section-header">
                  <el-icon class="section-icon"><Aim /></el-icon> 测试用例 ({{ currentProblem.test_case_count || 0 }})
                </div>
                <div class="section-body">
                  <div v-if="currentProblem.test_cases && currentProblem.test_cases.length">
                    <div v-for="(tc, index) in currentProblem.test_cases" :key="index" class="test-case-box">
                      <div class="tc-header">Case #{{ index + 1 }}</div>
                      <el-row :gutter="16">
                        <el-col :span="12">
                          <div class="io-title">Input</div>
                          <pre class="io-content">{{ tc.input || tc.stdin || '' }}</pre>
                        </el-col>
                        <el-col :span="12">
                          <div class="io-title">Expected Output</div>
                          <pre class="io-content">{{ tc.output || tc.expected_output || '' }}</pre>
                        </el-col>
                      </el-row>
                    </div>
                  </div>
                  <div v-else class="empty-tc">
                    <el-icon :size="24"><InfoFilled /></el-icon>
                    <p>暂无详细测试用例数据</p>
                  </div>
                </div>
              </div>
            </div>
          </el-tab-pane>
          
          <el-tab-pane name="metadata">
            <template #label>
              <span><el-icon><InfoFilled /></el-icon> 元数据</span>
            </template>
            <div class="detail-content">
              <div class="section-card">
                <div class="section-header">
                  <el-icon class="section-icon"><InfoFilled /></el-icon> 生成信息
                </div>
                <div class="section-body metadata-list">
                  <div class="meta-item">
                    <span class="label">来源课件</span>
                    <span class="value">{{ currentProblem.lesson_title }}</span>
                  </div>
                  <div class="meta-item">
                    <span class="label">页码范围</span>
                    <span class="value">{{ currentProblem.page_start }} - {{ currentProblem.page_end }}</span>
                  </div>
                  <div class="meta-item">
                    <span class="label">生成时间</span>
                    <span class="value">{{ formatTime(currentProblem.created_at) }}</span>
                  </div>
                  <div class="meta-item">
                    <span class="label">出题策略</span>
                    <el-tag :type="sourceStrategyTagType(currentProblem.source_strategy)">
                      {{ sourceStrategyLabel(currentProblem.source_strategy) }}
                    </el-tag>
                  </div>
                  <div class="meta-item">
                    <span class="label">关联 KC</span>
                    <div class="value tags-wrapper">
                      <el-tag v-for="kc in resolveLabelledKcs(currentProblem)" :key="kc.id" type="primary">{{ kc.name }}</el-tag>
                      <span v-if="!resolveLabelledKcs(currentProblem).length" style="color: #909399; font-size: 12px;">未标注</span>
                    </div>
                  </div>
                  <div class="meta-item" v-if="currentProblem.tags && currentProblem.tags.length">
                    <span class="label">提取的概念</span>
                    <div class="value tags-wrapper">
                      <el-tag v-for="tag in currentProblem.tags" :key="tag">{{ tag }}</el-tag>
                    </div>
                  </div>
                </div>
              </div>

              <div class="section-card" v-if="currentProblem.validation_log">
                <div class="section-header">
                  <el-icon class="section-icon"><Aim /></el-icon> 沙箱验证日志
                </div>
                <div class="section-body">
                  <el-alert v-if="currentProblem.status === 'failed'" type="error" show-icon style="margin-bottom: 12px;">
                    <template #title>当前题目验证状态：失败</template>
                  </el-alert>
                  <pre class="validation-log">{{ formatValidationLog(currentProblem.validation_log) }}</pre>
                </div>
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
      
      <template #footer><div>
        <el-button size="large" @click="showDetailModal = false">关闭</el-button>
        <el-button v-if="isStaff && currentProblem.id && canEditAiGeneratedProblem(currentProblem)"
                size="large"
                @click="openEditProblem(currentProblem)">
          编辑
        </el-button>
        <el-button v-if="isStaff && currentProblem.id && canReviewPassAiGeneratedProblem(currentProblem)"
                size="large"
                type="success"
                @click="reviewPassProblem(currentProblem)">
          审查通过
        </el-button>
        <el-button v-if="isStaff && currentProblem.id && canReviewRejectAiGeneratedProblem(currentProblem)"
                size="large"
                type="warning"
                @click="reviewRejectProblem(currentProblem)">
          审查驳回
        </el-button>
        <el-button v-if="isStaff && currentProblem.id && canValidateAiGeneratedProblem(currentProblem)"
                size="large"
                @click="validateProblem(currentProblem)">
          沙箱验证
        </el-button>
        <el-button v-if="isStaff && currentProblem.id && canPublishAiGeneratedProblem(currentProblem)" 
                type="primary" 
                size="large"
                @click="publishProblem(currentProblem)">
          发布到班级
        </el-button>
        <el-button v-if="isStaff && currentProblem.id && canDeleteAiGeneratedProblem(currentProblem)"
                size="large"
                type="danger"
                @click="deleteProblem(currentProblem)">
          删除
        </el-button>
      </div></template>
    </el-dialog>
    <el-dialog
      v-model="showEditModal"
      title="手动调整 AI 题目"
      width="760"
      :close-on-click-modal="false">
      <el-form :model="editForm" :label-width="90">
        <el-form-item label="题目标题">
          <el-input v-model="editForm.title" placeholder="请输入题目标题"/>
        </el-form-item>
        <el-form-item label="难度">
          <el-select v-model="editForm.difficulty">
            <el-option value="Easy" label="简单"/>
            <el-option value="Medium" label="中等"/>
            <el-option value="Hard" label="困难"/>
          </el-select>
        </el-form-item>
        <el-form-item label="题目描述">
          <el-input v-model="editForm.description" type="textarea" :rows="5" placeholder="请输入题目描述"/>
        </el-form-item>
        <el-form-item label="输入格式" v-if="editForm.question_type === 'coding'">
          <el-input v-model="editForm.input_description" type="textarea" :rows="3" placeholder="请输入输入格式"/>
        </el-form-item>
        <el-form-item label="输出格式" v-if="editForm.question_type === 'coding'">
          <el-input v-model="editForm.output_description" type="textarea" :rows="3" placeholder="请输入输出格式"/>
        </el-form-item>
        <el-form-item label="选项" v-if="editForm.question_type === 'choice'">
          <el-input v-model="editForm.options_text" type="textarea" :rows="6" placeholder="每行一个选项，例如：A. 选项内容"/>
        </el-form-item>
        <el-form-item label="答案" v-if="editForm.question_type === 'choice'">
          <el-input v-model="editForm.answer" placeholder="例如：A 或 A,C"/>
        </el-form-item>
        <el-form-item label="填空答案" v-if="editForm.question_type === 'fill_blank'">
          <el-input v-model="editForm.blanks_text" type="textarea" :rows="4" placeholder="每行一个填空答案"/>
        </el-form-item>
        <el-form-item label="解析">
          <el-input v-model="editForm.explanation" type="textarea" :rows="4" placeholder="请输入题目解析"/>
        </el-form-item>
        <el-form-item label="知识点">
          <el-input v-model="editForm.tags_text" placeholder="多个知识点用逗号分隔"/>
        </el-form-item>
      </el-form>
      <template #footer><div>
        <el-button @click="showEditModal = false">取消</el-button>
        <el-button type="primary" :loading="savingEdit" @click="saveEditProblem">保存调整</el-button>
      </div></template>
    </el-dialog>
  </div>
</template>

<script>
import api from '@oj/api'
import { ElMessageBox } from 'element-plus'
import {
  MagicStick, Download, Plus, Refresh, Document, List, Edit,
  Sunny, Right, Back, Monitor, Aim, InfoFilled, Connection, Promotion
} from '@element-plus/icons-vue'
import time from '@/utils/time'
import { sanitize } from '@/utils/sanitize'
import {
  isLessonSupportedForAiGeneration,
  canEditAiGeneratedProblem,
  canDeleteAiGeneratedProblem,
  canReviewPassAiGeneratedProblem,
  canReviewRejectAiGeneratedProblem,
  canValidateAiGeneratedProblem,
  canPublishAiGeneratedProblem
} from './aiGeneratedProblemActions'
import { getLessonPageCount, resolveSelectedLessonPages } from './lessonDetailSync'

const DIFF_LABEL = { Easy: '低', Low: '低', Mid: '中', Medium: '中', Hard: '高', High: '高' }
const DIFF_TAG_TYPE = { Easy: 'success', Low: 'success', Mid: 'info', Medium: 'info', Hard: 'danger', High: 'danger' }
const TYPE_TAG_TYPE = { choice: 'primary', fill_blank: 'success', coding: 'warning' }
const TYPE_LABEL = { choice: '选择', fill_blank: '填空', coding: '编程' }
const STATUS_MAP = {
  pending: { text: '待验证', bg: '#fff7e6', border: '#ffd591', color: '#d48806' },
  validating: { text: '验证中', bg: '#e6f7ff', border: '#91d5ff', color: '#096dd9' },
  passed: { text: '已完成', bg: '#f6ffed', border: '#b7eb8f', color: '#389e0d' },
  failed: { text: '失败', bg: '#fff1f0', border: '#ffa39e', color: '#cf1322' }
}

export default {
  name: 'AIGeneratedProblems',
  components: {
    MagicStick, Download, Plus, Refresh, Document, List, Edit,
    Sunny, Right, Back, Monitor, Aim, InfoFilled, Connection, Promotion
  },
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
      problems: [],
      loading: false,
      total: 0,
      page: 1,
      limit: 20,
      generating: false,
      taskId: null,
      taskError: '',
      taskGenerated: 0,
      taskTotal: 0,

      showDetailModal: false,
      currentProblem: {},
      showEditModal: false,
      savingEdit: false,
      editForm: {
        id: '',
        question_type: 'coding',
        title: '',
        difficulty: 'Medium',
        description: '',
        input_description: '',
        output_description: '',
        options_text: '',
        answer: '',
        blanks_text: '',
        explanation: '',
        tags_text: ''
      },

      showGenerateModal: false,
      lessons: [],
      submitGenerating: false,
      questionTypes: [
        { label: '选择题', value: 'choice' },
        { label: '填空题', value: 'fill_blank' },
        { label: '编程简答题', value: 'coding' }
      ],
      generateForm: {
        lesson_id: '',
        page_start: 1,
        page_end: 1,
        types: {
          choice: { checked: true, count: 3 },
          fill_blank: { checked: true, count: 3 },
          coding: { checked: true, count: 2 }
        },
        prefer_strategy: 'lp_first',
        target_difficulty: ''
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
      selectedKcCascade: [],
      kcIdNameMap: {},

      showKcLabelModal: false,
      kcLabelSelected: [],
      kcLabelSaving: false,
      pendingPublishProblem: null,

      pollTimer: null
    }
  },
  computed: {
    taskProgress () {
      if (this.taskTotal > 0) {
        return `(${this.taskGenerated}/${this.taskTotal})`
      }
      return ''
    },
    selectedLessonMaxPages () {
      const lesson = this.lessons.find(l => l.id === this.generateForm.lesson_id)
      return getLessonPageCount(lesson)
    }
  },
  mounted () {
    this.loadProblems()
    this.loadLessons()
    this.loadKcOptions()
    this.startPolling()
  },
  beforeUnmount () {
    if (this.pollTimer) {
      clearInterval(this.pollTimer)
    }
  },
  methods: {
    sanitize,
    isLessonSupportedForAiGeneration,
    canEditAiGeneratedProblem,
    canDeleteAiGeneratedProblem,
    canReviewPassAiGeneratedProblem,
    canReviewRejectAiGeneratedProblem,
    canValidateAiGeneratedProblem,
    canPublishAiGeneratedProblem,
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

    getDiffLabel (d) {
      return DIFF_LABEL[d] || d
    },
    getDiffTagType (d) {
      return DIFF_TAG_TYPE[d] || 'info'
    },
    getTypeTagType (qt) {
      return TYPE_TAG_TYPE[qt] || 'info'
    },
    getTypeLabel (qt) {
      return TYPE_LABEL[qt] || qt
    },
    getStatusText (row) {
      const s = row.status || 'pending'
      const info = Object.assign({}, STATUS_MAP[s] || STATUS_MAP.pending)
      if (s === 'pending' && row.question_type !== 'coding') {
        info.text = '待审核'
      }
      return info.text
    },
    getStatusStyle (row) {
      const s = row.status || 'pending'
      const info = STATUS_MAP[s] || STATUS_MAP.pending
      return {
        backgroundColor: info.bg,
        borderColor: info.border,
        color: info.color,
        padding: '2px 10px',
        borderRadius: '12px',
        fontSize: '12px'
      }
    },

    loadProblems () {
      this.loading = true
      const params = { page: this.page, limit: this.limit }

      api.getAIGeneratedProblems(this.classroomId, params).then(res => {
        this.problems = Array.isArray(res.data.data.results) ? res.data.data.results : []
        this.total = res.data.data.total || 0
        this.loading = false
      }).catch(() => {
        this.loading = false
      })
    },

    loadLessons () {
      api.getLessonList(this.classroomId).then(res => {
        const payload = (res && res.data && res.data.data) || {}
        const rawList = Array.isArray(payload)
          ? payload
          : (Array.isArray(payload.results) ? payload.results : (Array.isArray(res.data.results) ? res.data.results : []))
        this.lessons = rawList.map(item => this.normalizeLesson(item))
      })
    },

    loadKcOptions () {
      if (!this.isStaff) return
      api.getAIGeneratedKcOptions(this.classroomId).then(res => {
        const data = (res && res.data && res.data.data) || {}
        const chapters = Array.isArray(data.chapters) ? data.chapters : []
        this.kcCascadeOptions = chapters.map(chapter => ({
          id: 'chapter_' + (chapter.chapter_id || 'none'),
          name: chapter.chapter_title || '未分组',
          kcs: (chapter.kcs || []).map(kc => ({
            id: kc.id,
            name: kc.name
          }))
        }))
        const map = {}
        chapters.forEach(chapter => {
          (chapter.kcs || []).forEach(kc => {
            if (kc && kc.id != null) {
              map[String(kc.id)] = kc.name
            }
          })
        })
        this.kcIdNameMap = map
      }).catch(() => {
        this.kcCascadeOptions = []
        this.kcIdNameMap = {}
      })
    },

    sourceStrategyLabel (strategy) {
      const map = {
        lp_kc_pick: '语言包命中',
        lesson_llm: 'LLM 课件兜底',
        hybrid: '混合策略'
      }
      return map[strategy] || strategy || '—'
    },
    sourceStrategyTagType (strategy) {
      const map = {
        lp_kc_pick: 'success',
        lesson_llm: 'warning',
        hybrid: 'info'
      }
      return map[strategy] || 'info'
    },
    resolveLabelledKcs (problem) {
      if (!problem) return []
      const ids = Array.isArray(problem.target_kc_ids) ? problem.target_kc_ids : []
      return ids.map(id => ({ id, name: this.kcIdNameMap[String(id)] || ('KC #' + id) }))
    },
    exportReviewedAiProblems () {
      api.exportReviewedAIGeneratedProblems(this.classroomId).then(res => {
        const payload = (res.data && res.data.data) || {}
        const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json;charset=utf-8' })
        const url = window.URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = url
        a.download = `ai-reviewed-objective-questions-${this.classroomId}.json`
        a.click()
        window.URL.revokeObjectURL(url)
      })
    },

    async onLessonSelect (lessonId) {
      this.generateForm.page_start = 1
      if (!lessonId) {
        this.generateForm.page_end = 1
        return
      }

      try {
        const { lessons, maxPages } = await resolveSelectedLessonPages({
          api,
          classroomId: this.classroomId,
          lessonId,
          lessons: this.lessons,
          normalizeLesson: this.normalizeLesson
        })
        this.lessons = lessons
        this.generateForm.page_end = maxPages || 1
      } catch (error) {
        this.$error('课件详情加载失败，请刷新后重试')
        this.generateForm.page_end = 1
      }
    },

    startGenerate () {
      if (!this.generateForm.lesson_id) {
        this.$error('请选择课件')
        return
      }
      const selectedLesson = this.lessons.find(lesson => lesson.id === this.generateForm.lesson_id)
      if (!this.isLessonSupportedForAiGeneration(selectedLesson)) {
        this.$error('仅支持基于 PPT/PDF 课件生成题目')
        return
      }

      const selectedTypes = []
      const counts = {}

      for (const key in this.generateForm.types) {
        if (this.generateForm.types[key].checked) {
          selectedTypes.push(key)
          counts[key] = this.generateForm.types[key].count
        }
      }

      if (selectedTypes.length === 0) {
        this.$error('请至少选择一种题目类型')
        return
      }

      const maxPages = this.selectedLessonMaxPages
      const pageStart = Math.max(1, Math.min(this.generateForm.page_start, maxPages || this.generateForm.page_start))
      const pageEnd = Math.max(pageStart, Math.min(this.generateForm.page_end, maxPages || this.generateForm.page_end))

      const targetKcIds = (this.selectedKcCascade || []).filter(v => typeof v === 'number' || /^\d+$/.test(String(v)))
        .map(v => Number(v))

      const payload = {
        lesson_id: this.generateForm.lesson_id,
        page_start: pageStart,
        page_end: pageEnd,
        question_types: selectedTypes,
        counts: counts,
        target_kc_ids: targetKcIds.length ? targetKcIds : undefined,
        prefer_strategy: this.generateForm.prefer_strategy || undefined,
        target_difficulty: this.generateForm.target_difficulty || undefined
      }

      this.submitGenerating = true
      this.taskError = ''
      api.generateProblemFromLesson(this.classroomId, payload).then(res => {
        const data = res.data.data
        this.taskId = data.task_id
        this.taskTotal = data.total_requested || 0
        this.taskGenerated = 0
        this.$success('任务提交成功，AI 正在生成题目')
        this.showGenerateModal = false
        this.submitGenerating = false
        this.generating = true
        this.startTaskPolling()
      }).catch(err => {
        this.submitGenerating = false
        const msg = (err.response && err.response.data && err.response.data.error) || 'AI 生成请求失败，请重试'
        this.$error(msg)
        this.taskError = msg
      })
    },

    startTaskPolling () {
      if (this.pollTimer) clearInterval(this.pollTimer)
      this.pollTimer = setInterval(() => {
        if (!this.generating || !this.taskId) {
          clearInterval(this.pollTimer)
          return
        }
        this.pollTaskStatus()
      }, 3000)
    },

    pollTaskStatus () {
      api.getAITaskStatus(this.classroomId, this.taskId).then(res => {
        const data = res.data.data
        this.taskGenerated = data.generated_count || 0
        this.taskTotal = data.total_requested || this.taskTotal

        if (data.status === 'completed') {
          this.generating = false
          this.taskId = null
          clearInterval(this.pollTimer)
          this.$success(`生成完成！成功生成 ${data.generated_count} 道题目`)
          this.loadProblems()
        } else if (data.status === 'completed_with_errors') {
          this.generating = false
          this.taskId = null
          clearInterval(this.pollTimer)
          this.$warning(`成功 ${data.generated_count} 道，失败 ${data.error_count} 道`)
          if (data.error_message) this.taskError = data.error_message
          this.loadProblems()
        } else if (data.status === 'failed') {
          this.generating = false
          this.taskId = null
          clearInterval(this.pollTimer)
          this.taskError = data.error_message || 'AI 生成失败，请重试'
          this.$error('AI 生成失败: ' + (data.error_message || '未知错误'))
        }
      }).catch(() => {})
    },

    startPolling () {
      if (this.pollTimer) clearInterval(this.pollTimer)
    },

    viewProblem (problem) {
      this.currentProblem = problem
      this.showDetailModal = true

      api.getAIGeneratedProblem(this.classroomId, problem.id).then(res => {
        this.currentProblem = res.data.data
      })
    },

    normalizeOptionsText (options) {
      return (options || []).map((opt, idx) => {
        const label = (opt && opt.label) || String.fromCharCode(65 + idx)
        const text = (opt && opt.text) || ''
        return `${label}. ${text}`.trim()
      }).join('\n')
    },

    parseOptionsText (text) {
      const lines = (text || '').split('\n').map(s => s.trim()).filter(Boolean)
      return lines.map((line, idx) => {
        const m = line.match(/^([A-Za-z])[\.\、\)]\s*(.+)$/)
        if (m) return { label: m[1].toUpperCase(), text: m[2] }
        return { label: String.fromCharCode(65 + idx), text: line }
      })
    },

    parseTagsText (text) {
      return (text || '')
        .split(/[,，]/)
        .map(t => t.trim())
        .filter(Boolean)
    },

    openEditProblem (problem) {
      api.getAIGeneratedProblem(this.classroomId, problem.id).then(res => {
        const p = res.data.data || {}
        const pj = p.generated_problem_json || {}
        const questionType = p.question_type || 'coding'
        this.currentProblem = p
        this.editForm = {
          id: p.id,
          question_type: questionType,
          title: pj.title || p.title || '',
          difficulty: pj.difficulty || p.difficulty || 'Medium',
          description: pj.description || p.description || '',
          input_description: pj.input_description || p.input_description || '',
          output_description: pj.output_description || p.output_description || '',
          options_text: this.normalizeOptionsText(pj.options || p.options || []),
          answer: pj.answer || p.answer || '',
          blanks_text: (pj.blanks || p.blanks || []).join('\n'),
          explanation: pj.explanation || p.explanation || '',
          tags_text: (p.tags || []).join(', ')
        }
        this.showEditModal = true
      })
    },

    saveEditProblem () {
      if (!this.editForm.id) return
      if (!this.editForm.title || !this.editForm.title.trim()) {
        this.$error('题目标题不能为空')
        return
      }

      const baseJson = Object.assign({}, (this.currentProblem && this.currentProblem.generated_problem_json) || {})
      const payloadJson = Object.assign(baseJson, {
        title: (this.editForm.title || '').trim(),
        difficulty: this.editForm.difficulty || 'Medium',
        description: this.editForm.description || '',
        input_description: this.editForm.input_description || '',
        output_description: this.editForm.output_description || '',
        explanation: this.editForm.explanation || ''
      })

      if (this.editForm.question_type === 'choice') {
        payloadJson.options = this.parseOptionsText(this.editForm.options_text)
        payloadJson.answer = (this.editForm.answer || '').trim()
      }
      if (this.editForm.question_type === 'fill_blank') {
        payloadJson.blanks = (this.editForm.blanks_text || '').split('\n').map(s => s.trim()).filter(Boolean)
      }

      const payload = {
        generated_problem_json: payloadJson,
        extracted_concepts: this.parseTagsText(this.editForm.tags_text),
        difficulty_estimation: this.editForm.difficulty || 'Medium'
      }

      this.savingEdit = true
      api.updateAIGeneratedProblem(this.classroomId, this.editForm.id, payload).then(res => {
        this.savingEdit = false
        this.showEditModal = false
        this.$success('题目已更新')
        const updated = res.data && res.data.data
        if (updated && this.currentProblem && this.currentProblem.id === updated.id) {
          this.currentProblem = updated
        }
        this.loadProblems()
      }).catch(() => {
        this.savingEdit = false
      })
    },

    publishProblem (problem) {
      const targetKcIds = Array.isArray(problem.target_kc_ids) ? problem.target_kc_ids : []
      if (!targetKcIds.length) {
        this.pendingPublishProblem = problem
        this.kcLabelSelected = []
        this.showKcLabelModal = true
        return
      }
      ElMessageBox.confirm(
        `确定将题目"${problem.title}"发布到班级吗？`,
        '发布题目',
        { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
      ).then(() => {
        api.publishAIGeneratedProblem(this.classroomId, problem.id).then(() => {
          this.$success('发布成功')
          this.loadProblems()
        })
      }).catch(() => {})
    },

    cancelKcLabel () {
      this.showKcLabelModal = false
      this.pendingPublishProblem = null
      this.kcLabelSelected = []
    },

    confirmKcLabel () {
      const problem = this.pendingPublishProblem
      if (!problem) {
        this.cancelKcLabel()
        return
      }
      const ids = (this.kcLabelSelected || []).map(v => Number(v)).filter(v => Number.isFinite(v))
      if (!ids.length) {
        this.$error('请至少选择 1 个 KC')
        return
      }
      this.kcLabelSaving = true
      const baseJson = Object.assign({}, problem.generated_problem_json || {})
      api.updateAIGeneratedProblem(this.classroomId, problem.id, {
        generated_problem_json: baseJson,
        target_kc_ids: ids
      }).then(() => {
        return api.publishAIGeneratedProblem(this.classroomId, problem.id)
      }).then(() => {
        this.$success('已标注 KC 并发布')
        this.kcLabelSaving = false
        this.cancelKcLabel()
        this.loadProblems()
      }).catch(err => {
        this.kcLabelSaving = false
        const msg = (err && err.response && err.response.data && err.response.data.error) || '保存或发布失败'
        this.$error(msg)
      })
    },

    validateProblem (problem) {
      ElMessageBox.confirm(
        `确定对题目"${problem.title}"执行 Validator Agent 沙箱验证吗？`,
        '触发沙箱验证',
        { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
      ).then(() => {
        api.validateAIGeneratedProblem(this.classroomId, problem.id).then(res => {
          const updated = res.data.data && res.data.data.problem
          if (updated) {
            this.problems = this.problems.map(p => (p.id === updated.id ? updated : p))
            if (this.currentProblem && this.currentProblem.id === updated.id) {
              this.currentProblem = Object.assign({}, this.currentProblem, updated)
            }
          }
          const status = (res.data.data && res.data.data.result && res.data.data.result.status) || (updated && updated.status) || ''
          if (status === 'passed') {
            this.$success('沙箱验证通过')
          } else if (status === 'failed') {
            this.$error('沙箱验证失败，请查看验证日志')
          } else {
            this.$success('已触发沙箱验证')
          }
        })
      }).catch(() => {})
    },

    reviewPassProblem (problem) {
      ElMessageBox.confirm(
        `确认将题目"${problem.title}"标记为人工审查通过吗？`,
        '人工审查通过',
        { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
      ).then(() => {
        api.reviewPassAIGeneratedProblem(this.classroomId, problem.id).then(res => {
          const updated = res.data.data
          if (updated) {
            this.problems = this.problems.map(p => (p.id === updated.id ? updated : p))
            if (this.currentProblem && this.currentProblem.id === updated.id) {
              this.currentProblem = Object.assign({}, this.currentProblem, updated)
            }
          }
          this.$success('审查通过，可发布到班级')
        })
      }).catch(() => {})
    },

    reviewRejectProblem (problem) {
      ElMessageBox.confirm(
        `确认将题目"${problem.title}"标记为人工审查驳回吗？`,
        '人工审查驳回',
        { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
      ).then(() => {
        api.reviewRejectAIGeneratedProblem(this.classroomId, problem.id).then(res => {
          const updated = res.data.data
          if (updated) {
            this.problems = this.problems.map(p => (p.id === updated.id ? updated : p))
            if (this.currentProblem && this.currentProblem.id === updated.id) {
              this.currentProblem = Object.assign({}, this.currentProblem, updated)
            }
          }
          this.$warning('已标记为审查驳回')
        })
      }).catch(() => {})
    },

    deleteProblem (problem) {
      ElMessageBox.confirm(
        `确定删除题目"${problem.title}"吗？此操作不可恢复。`,
        '确认删除',
        { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
      ).then(() => {
        api.deleteAIGeneratedProblem(this.classroomId, problem.id).then(() => {
          this.$success('删除成功')
          this.problems = this.problems.filter(p => p.id !== problem.id)
          this.total = Math.max(0, this.total - 1)
          if (this.currentProblem && this.currentProblem.id === problem.id) {
            this.showDetailModal = false
            this.currentProblem = {}
          }
          const maxPage = Math.max(1, Math.ceil(this.total / this.limit))
          if (this.page > maxPage) this.page = maxPage
          this.loadProblems()
        })
      }).catch(() => {})
    },

    onPageChange (page) {
      this.page = page
      this.loadProblems()
    },

    getDifficultyColor (difficulty) {
      const colors = {
        Easy: 'success',
        Medium: 'warning',
        Hard: 'error'
      }
      return colors[difficulty] || 'default'
    },

    getTypeColor (qt) {
      return { choice: 'blue', fill_blank: 'green', coding: 'orange' }[qt] || 'default'
    },

    formatTime (timeStr) {
      return time.utcToLocal(timeStr, 'YYYY-MM-DD HH:mm')
    },
    formatValidationLog (log) {
      if (!log) return ''
      if (typeof log === 'object') {
        try {
          return JSON.stringify(log, null, 2)
        } catch (e) {
          return String(log)
        }
      }
      try {
        const parsed = JSON.parse(log)
        return JSON.stringify(parsed, null, 2)
      } catch (e) {
        return String(log)
      }
    }
  }
}
</script>

<style lang="less" scoped>
.generate-section {
  border: 1px solid #e6ebf1;
  border-radius: 10px;
  padding: 16px 18px 4px 18px;
  margin-top: 8px;
  margin-bottom: 8px;
  background: #fbfcfe;

  .generate-section-title {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 13px;
    font-weight: 600;
    color: #1f2937;
    margin: -6px 0 10px;

    .el-icon {
      color: #2d8cf0;
    }
  }
}

.form-hint {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
  line-height: 1.5;
}

.strategy-radio-group {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
}

.kc-label-dialog {
  .kc-label-form-block {
    background: #f7faff;
    border: 1px solid #e3edff;
    border-radius: 8px;
    padding: 14px 16px;
  }
}

.ai-problems {
  .card-header {
    display: flex;
    align-items: center;
    justify-content: space-between;

    .card-title {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      font-size: 16px;
      font-weight: 600;
      color: #17233d;
    }
  }

  .empty-state {
    text-align: center;
    padding: 80px 20px;
    
    p {
      margin: 0;
    }
  }

  :deep(.ai-table) {
    margin-top: 8px;
  }
  
  .pagination {
    margin-top: 20px;
    text-align: right;
  }
  
    .problem-detail-container {
    background-color: #f8f9fa;
    padding: 24px;
    min-height: 500px;
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;

    .detail-content {
      max-width: 100%;
    }

    .section-card {
      background: #ffffff;
      border-radius: 12px;
      box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05), 0 2px 4px -1px rgba(0, 0, 0, 0.03);
      margin-bottom: 24px;
      overflow: hidden;
      border: none;
      transition: transform 0.2s ease, box-shadow 0.2s ease;

      &:hover {
        transform: translateY(-2px);
        box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.05), 0 4px 6px -2px rgba(0, 0, 0, 0.025);
      }

      .section-header {
        padding: 16px 24px;
        border-bottom: 1px solid #f0f0f0;
        font-size: 16px;
        font-weight: 700;
        color: #1f2937;
        display: flex;
        align-items: center;
        background-color: #ffffff;

        .section-icon {
          margin-right: 10px;
          color: #3b82f6;
          font-size: 18px;
          background: #eff6ff;
          padding: 6px;
          border-radius: 6px;
        }
      }

      .section-body {
        padding: 24px;
        font-size: 15px;
        line-height: 1.75;
        color: #374151;

        &.description-text {
          img {
            max-width: 100%;
            border-radius: 8px;
            box-shadow: 0 4px 6px rgba(0,0,0,0.1);
          }
          pre {
            background: #1e293b;
            color: #e2e8f0;
            padding: 16px;
            border-radius: 8px;
            font-family: 'JetBrains Mono', Consolas, Menlo, monospace;
            overflow-x: auto;
            margin: 16px 0;
          }
          p {
            margin-bottom: 1em;
          }
        }
      }
    }

    .options-list {
      display: flex;
      flex-direction: column;
      gap: 12px;
    }

    .option-row {
      display: flex;
      align-items: flex-start;
      padding: 16px;
      border: 1px solid #e5e7eb;
      border-radius: 8px;
      transition: all 0.2s ease;
      background-color: #fff;
      cursor: default;

      &:hover {
        border-color: #3b82f6;
        background-color: #eff6ff;
        transform: translateX(4px);
      }

      .option-label {
        flex-shrink: 0;
        width: 32px;
        height: 32px;
        background-color: #f3f4f6;
        color: #4b5563;
        border-radius: 50%;
        display: flex;
        align-items: center;
        justify-content: center;
        font-weight: 700;
        margin-right: 16px;
        font-size: 14px;
        transition: all 0.2s;
      }

      &:hover .option-label {
        background-color: #3b82f6;
        color: #fff;
      }

      .option-content {
        padding-top: 4px;
        font-weight: 500;
        color: #1f2937;
      }
    }

    .sample-box, .test-case-box {
      background: #ffffff;
      border: 1px solid #e5e7eb;
      border-radius: 8px;
      margin-bottom: 20px;
      overflow: hidden;
      box-shadow: 0 1px 2px rgba(0,0,0,0.05);

      .sample-header, .tc-header {
        padding: 10px 20px;
        background: #f9fafb;
        border-bottom: 1px solid #e5e7eb;
        font-weight: 600;
        font-size: 13px;
        color: #6b7280;
        text-transform: uppercase;
        letter-spacing: 0.05em;
      }

      .el-row {
        padding: 20px;
      }

      .io-title {
        font-size: 12px;
        color: #9ca3af;
        margin-bottom: 8px;
        text-transform: uppercase;
        font-weight: 700;
        letter-spacing: 0.05em;
      }

      .io-content {
        background: #1f293b;
        border: 1px solid #374151;
        border-radius: 6px;
        padding: 12px;
        margin: 0;
        font-family: 'JetBrains Mono', Consolas, monospace;
        font-size: 13px;
        white-space: pre-wrap;
        word-wrap: break-word;
        color: #e2e8f0;
        min-height: 48px;
        line-height: 1.5;
      }
    }
    
    .empty-tc {
      text-align: center;
      padding: 60px 0;
      color: #9ca3af;
      
      i {
        font-size: 48px;
        margin-bottom: 16px;
        opacity: 0.5;
      }
      
      p {
        font-size: 15px;
      }
    }

    .metadata-list {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
      gap: 20px;

      .meta-item {
        display: flex;
        flex-direction: column;
        background: #f9fafb;
        padding: 16px;
        border-radius: 8px;
        border: 1px solid #f3f4f6;
        
        .label {
          font-size: 12px;
          text-transform: uppercase;
          color: #9ca3af;
          font-weight: 600;
          margin-bottom: 8px;
          letter-spacing: 0.05em;
        }

        .value {
          color: #1f2937;
          font-weight: 600;
          font-size: 15px;
          
          &.tags-wrapper {
            display: flex;
            flex-wrap: wrap;
            gap: 8px;
          }
        }
      }
    }

    .validation-log {
      background: #111827;
      color: #e5e7eb;
      border: 1px solid #374151;
      border-radius: 8px;
      padding: 14px;
      margin: 0;
      max-height: 320px;
      overflow: auto;
      white-space: pre-wrap;
      word-break: break-word;
      font-family: 'JetBrains Mono', Consolas, monospace;
      font-size: 12px;
      line-height: 1.55;
    }
  }
}

.spin-icon {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
