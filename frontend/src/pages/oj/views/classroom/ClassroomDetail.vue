<template>
  <div class="classroom-detail-container">
    <div class="classroom-header">
      <el-card>
        <h1>{{ classroom.name }}</h1>
        <p class="meta">
          <el-tag :style="getRoleTagStyle(classroom.my_role)">{{ getRoleText(classroom.my_role) }}</el-tag>
          <span class="divider">|</span>
          <span>课程代码：{{ classroom.course_code }}</span>
          <span class="divider">|</span>
          <span>学期：{{ classroom.semester }}</span>
          <span class="divider">|</span>
          <span>
            <el-icon><User /></el-icon>
            {{ classroom.member_count || 0 }} 人
          </span>
        </p>
        <div v-if="classroom.language_pack" class="language-pack-banner">
          <div class="language-pack-banner-label">绑定课程内容包</div>
          <div class="language-pack-banner-content">
            <span class="language-pack-banner-name">{{ classroom.language_pack.name }}</span>
            <span class="language-pack-banner-meta">{{ classroom.language_pack.primary_language }} · v{{ classroom.language_pack.version }}</span>
          </div>
        </div>
        <p v-if="classroom.description" class="description">{{ classroom.description }}</p>

        <div class="actions">
          <el-button v-if="isStaff" type="primary" @click="showInviteModal">
            <el-icon><Plus /></el-icon> 邀请成员
          </el-button>
          <el-button v-if="isStaff" @click="activeTab = 'problems'">
            <el-icon><Document /></el-icon> 题目管理
          </el-button>
          <el-button v-if="isStaff" @click="activeTab = 'monitor'">
            <el-icon><DataLine /></el-icon> 数据看板
          </el-button>
        </div>
      </el-card>
    </div>

    <div class="classroom-content">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="课件" name="lessons">
          <LessonManagement :classroom-id="classroomId" :is-staff="isStaff"/>
        </el-tab-pane>
        <el-tab-pane label="成员" name="members">
          <el-card>
            <el-table :data="pagedMembers" v-loading="memberLoading">
              <el-table-column label="用户" align="center">
                <template #default="scope">
                  <span style="color: #000">{{ (scope.row.user || {}).username || '' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="角色" width="120" align="center">
                <template #default="scope">
                  <el-tag
                    :type="{ owner: 'info', ta: 'primary', student: 'info' }[scope.row.role] || 'info'"
                    :style="{ color: '#000', fontWeight: scope.row.role === 'owner' ? 'bold' : 'normal' }">
                    {{ { owner: '教师', ta: '助教', student: '学生' }[scope.row.role] || scope.row.role }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="加入时间" width="200" align="center">
                <template #default="scope">
                  <span style="color: #000; white-space: nowrap;">{{ formatTime(scope.row.join_time) }}</span>
                </template>
              </el-table-column>
              <el-table-column v-if="!isStudent" label="操作" width="200" align="center">
                <template #default="scope">
                  <template v-if="isOwner && scope.row.role !== 'owner'">
                    <el-button v-if="scope.row.role === 'student'" type="primary" link size="small" @click="promoteMember(scope.row)">
                      提升为助教
                    </el-button>
                    <el-button v-if="scope.row.role === 'ta'" type="primary" link size="small" @click="demoteMember(scope.row)">
                      降为学生
                    </el-button>
                    <el-button type="danger" link size="small" @click="confirmRemoveMember(scope.row)">
                      移除
                    </el-button>
                  </template>
                </template>
              </el-table-column>
            </el-table>
            <Pagination
              :total="members.length"
              :current-page="memberPage"
              :page-size="memberPageSize"
              @update:currentPage="memberPage = $event"
              @update:pageSize="memberPageSize = $event"
            />
          </el-card>
        </el-tab-pane>
        <el-tab-pane label="题目" name="problems">
          <el-tabs v-model="problemActiveTab" type="card">
            <el-tab-pane label="班级题目" name="classroom-problems">
              <el-card class="classroom-problem-panel">
                <template #header>
                  <div class="panel-header">
                    <span class="panel-title">班级题目列表</span>
                    <div class="panel-actions">
                      <el-button v-if="isStaff" @click="triggerObjectiveImport">
                        <el-icon><Upload /></el-icon> 导入选择/填空JSON
                      </el-button>
                      <el-button v-if="isStaff" @click="exportObjectiveQuestions">
                        <el-icon><Download /></el-icon> 导出选择/填空JSON
                      </el-button>
                      <el-button v-if="isStaff" type="primary" @click="showAddProblemModal">
                        <el-icon><Plus /></el-icon> 添加题目
                      </el-button>
                    </div>
                  </div>
                </template>
                <el-table class="classroom-problem-table" :data="pagedProblems" v-loading="problemLoading">
                  <el-table-column label="#" width="130" align="center">
                    <template #default="scope">
                      <a style="cursor: pointer; color: #2d8cf0"
                        @click="goToProblem(scope.row._id || scope.row.problem_display_id || scope.row.problem_id)">
                        {{ scope.row._id || scope.row.problem_display_id || scope.row.problem_id }}
                      </a>
                    </template>
                  </el-table-column>
                  <el-table-column label="题目" min-width="520" align="center">
                    <template #default="scope">
                      <div>
                        <a style="cursor: pointer; color: #2d8cf0"
                          @click="goToProblem(scope.row._id || scope.row.problem_display_id || scope.row.problem_id)">
                          {{ scope.row.title || '' }}
                        </a>
                        <el-tag v-if="isStaff && !scope.row.is_visible" type="info">隐藏</el-tag>
                      </div>
                    </template>
                  </el-table-column>
                  <el-table-column label="难度" width="120" align="center">
                    <template #default="scope">
                      <el-tag :type="{ Low: 'success', Mid: 'primary', High: 'danger' }[scope.row.difficulty] || 'info'">
                        {{ { Low: '低', Mid: '中', High: '高' }[scope.row.difficulty] || '-' }}
                      </el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column label="总提交" width="130" align="center">
                    <template #default="scope">{{ scope.row.submission_number || 0 }}</template>
                  </el-table-column>
                  <el-table-column label="通过率" width="130" align="center">
                    <template #default="scope">{{ getACRateText(scope.row.accepted_number, scope.row.submission_number) }}</template>
                  </el-table-column>
                  <el-table-column label="状态" width="130" align="center">
                    <template #default="scope">
                      <el-tag v-if="scope.row.my_status === 'AC'" type="success">AC</el-tag>
                      <el-tag v-else-if="scope.row.my_status === 'WA'" type="warning">Tried</el-tag>
                      <el-tag v-else type="info">-</el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column v-if="isStaff" label="操作" width="220" align="center">
                    <template #default="scope">
                      <el-button type="primary" link size="small"
                        :style="{ color: scope.row.is_visible ? '#808695' : '#2d8cf0' }"
                        @click="toggleProblemVisibility(scope.row, !scope.row.is_visible)">
                        {{ scope.row.is_visible ? '隐藏' : '发布' }}
                      </el-button>
                      <el-button type="danger" link size="small" @click="confirmRemoveProblem(scope.row)">
                        移除
                      </el-button>
                    </template>
                  </el-table-column>
                </el-table>
                <Pagination
                  :total="problems.length"
                  :current-page="problemPage"
                  :page-size="problemPageSize"
                  @update:currentPage="problemPage = $event"
                  @update:pageSize="problemPageSize = $event"
                />
              </el-card>
              <input
                ref="objectiveImportInput"
                type="file"
                accept=".json,application/json"
                style="display: none;"
                @change="onObjectiveImportFileChange">
            </el-tab-pane>

            <el-tab-pane v-if="isStaff" label="AI 生成题目" name="ai-problems">
              <AIGeneratedProblems :classroom-id="classroomId" :is-staff="isStaff"/>
            </el-tab-pane>
          </el-tabs>
        </el-tab-pane>
        <el-tab-pane label="作业" name="assignments">
          <ClassroomAssignment :classroom-id="classroomId" :is-staff="isStaff"/>
        </el-tab-pane>
        <el-tab-pane v-if="isStaff" name="monitor">
          <template #label>
            <div class="monitor-tab-trigger" @mouseenter="monitorDropdownVisible = true" @mouseleave="monitorDropdownVisible = false">
              <span>数据看板</span>
              <el-icon class="monitor-caret" :class="{ rotated: monitorDropdownVisible }"><CaretBottom /></el-icon>
              <transition name="monitor-dropdown-fade">
                <div v-show="monitorDropdownVisible" class="monitor-dropdown">
                  <div v-for="item in monitorSections" :key="item.key"
                       class="monitor-dropdown-item"
                       @click.stop="scrollToMonitorSection(item.key)">
                    {{ item.label }}
                  </div>
                </div>
              </transition>
            </div>
          </template>
          <ClassroomAnalytics ref="analyticsRef" :classroom-id="classroomId"/>
        </el-tab-pane>
      </el-tabs>
    </div>
    <el-dialog v-model="inviteModalVisible" title="邀请成员" width="600px">
      <div v-if="currentInvitation">
        <el-alert show-icon :closable="false">
          <template #title>
            邀请码：<strong style="font-size: 18px; color: #2d8cf0;">{{ currentInvitation.code }}</strong>
            <el-button type="primary" link v-clipboard:copy="currentInvitation.code" v-clipboard:success="onCopySuccess">
              复制
            </el-button>
          </template>
        </el-alert>
        <p style="margin-top: 10px;">
          有效期至：{{ formatTime(currentInvitation.expires_at) }}<br/>
          <span v-if="currentInvitation.usage_limit > 0">
            剩余次数：{{ currentInvitation.usage_limit - currentInvitation.usage_count }}
          </span>
        </p>
      </div>
      <el-button v-if="!hasActiveInvitation" type="primary" style="width: 100%" @click="generateInvitation" :loading="generatingInvite">
        生成新邀请码
      </el-button>
      <el-alert v-else type="success" :closable="false" title="当前已有有效邀请码，过期前无需重复生成。" style="margin-top: 12px;" />
    </el-dialog>
    <el-dialog v-model="addProblemModalVisible" title="添加题目" width="800px">
      <div style="margin-bottom: 16px;">
        <el-input v-model="addProblemSearch" placeholder="输入题目ID或关键词搜索" :disabled="addProblemSearching" @keyup.enter="searchProblems">
          <template #append>
            <el-button @click="searchProblems">搜索</el-button>
          </template>
        </el-input>
      </div>
      <el-table :data="addProblemResults" v-loading="addProblemSearching" max-height="400">
        <el-table-column label="题号" width="140" align="center">
          <template #default="scope">{{ scope.row._id || scope.row.id }}</template>
        </el-table-column>
        <el-table-column label="题目" min-width="200" align="center">
          <template #default="scope">{{ scope.row.title || '' }}</template>
        </el-table-column>
        <el-table-column label="难度" width="100" align="center">
          <template #default="scope">
            <el-tag :type="{ Low: 'success', Mid: 'primary', High: 'danger' }[scope.row.difficulty] || 'info'">
              {{ { Low: '低', Mid: '中', High: '高' }[scope.row.difficulty] || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center">
          <template #default="scope">
            <el-tag v-if="problems.some(p => p.problem_id === scope.row.id)" type="success">已添加</el-tag>
            <el-button v-else type="primary" size="small"
              :loading="!!addProblemAdding[scope.row.id]"
              @click="addProblemToClassroom(scope.row)">
              {{ addProblemAdding[scope.row.id] ? '' : '添加' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="addProblemResults.length === 0 && !addProblemSearching" style="text-align: center; color: #808695; padding: 20px 0;">
        输入关键词搜索题库中的题目
      </div>
    </el-dialog>
    <el-dialog
      v-model="showCreateSessionModal"
      title="创建协作会话"
      custom-class="create-session-modal"
      width="680px">
      <el-form :model="createSessionForm" label-width="80px" class="session-form">
        <el-form-item label="会话名称" class="form-item-promax">
          <el-input v-model="createSessionForm.name" placeholder="例如：快速排序练习" size="large">
            <template #suffix>
              <el-icon><EditPen /></el-icon>
            </template>
          </el-input>
        </el-form-item>

        <el-form-item label="协作模式" class="form-item-promax">
          <div class="mode-selection-grid">
            <div
              v-for="mode in modes"
              :key="mode.value"
              class="mode-card"
              :class="{ active: createSessionForm.mode === mode.value }"
              @click="createSessionForm.mode = mode.value">
              <div class="mode-icon">
                <el-icon :size="24"><component :is="mode.icon" /></el-icon>
              </div>
              <div class="mode-info">
                <span class="mode-title">{{ mode.label }}</span>
                <span class="mode-desc">{{ mode.desc }}</span>
              </div>
              <div class="mode-check" v-if="createSessionForm.mode === mode.value">
                <el-icon :size="16"><Check /></el-icon>
              </div>
            </div>
          </div>
        </el-form-item>

        <el-row :gutter="24">
          <el-col :span="12">
            <el-form-item label="编程语言" class="form-item-promax">
              <el-select v-model="createSessionForm.language" size="large">
                <el-option value="python" label="Python" />
                <el-option value="cpp" label="C++" />
                <el-option value="java" label="Java" />
                <el-option value="javascript" label="JavaScript" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="关联题目" class="form-item-promax">
              <el-select v-model="createSessionForm.problem_id" clearable placeholder="可选：关联班级题目" size="large">
                <el-option-group label="班级题目">
                  <el-option v-for="problem in problems" :key="'cp-' + problem.id" :value="problem.id" :label="problem.title" />
                </el-option-group>
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="showCreateSessionModal = false">取消</el-button>
        <el-button type="primary" @click="createCollaborationSession">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import api from '@oj/api'
import { ElMessageBox } from 'element-plus'
import {
  User, Plus, Document, Upload, Download, DataLine,
  Unlock, Sort, EditPen, UserFilled, ArrowRight, Check, CaretBottom
} from '@element-plus/icons-vue'
import time from '@/utils/time'
import { encodeRouteCtx, decodeRouteCtx } from '@/utils/urlCipher'
import LessonManagement from './LessonManagement.vue'
import AIGeneratedProblems from './AIGeneratedProblems.vue'
import ClassroomAssignment from './ClassroomAssignment.vue'
import ClassroomAnalytics from './ClassroomAnalytics.vue'
import Pagination from '@/components/Pagination.vue'

export default {
  name: 'ClassroomDetail',
  components: {
    LessonManagement,
    AIGeneratedProblems,
    ClassroomAssignment,
    ClassroomAnalytics,
    User,
    Plus,
    Document,
    Upload,
    Download,
    DataLine,
    Unlock,
    Sort,
    EditPen,
    UserFilled,
    ArrowRight,
    Check,
    CaretBottom,
    Pagination
  },
  data () {
    return {
      classroomId: decodeRouteCtx(this.$route.query.ctx).id || '',
      classroom: {},
      activeTab: 'problems',
      problemActiveTab: 'classroom-problems',

      members: [],
      memberLoading: false,
      memberPage: 1,
      memberPageSize: 10,

      problems: [],
      problemLoading: false,
      problemPage: 1,
      problemPageSize: 10,
      aiCodingProblems: [],

      stats: {},

      sessions: [],
      sessionLoading: false,
      showCreateSessionModal: false,
      createSessionForm: {
        name: '',
        mode: 'free',
        language: 'python',
        problem_id: null
      },
      modes: [
        { value: 'free', label: '自由协作', desc: '所有成员可同时编辑', icon: 'Unlock' },
        { value: 'relay', label: '代码接力', desc: '令牌持有者编辑，轮流接力', icon: 'Sort' },
        { value: 'scaffolding', label: '编程填空', desc: '基于模板的约束编辑', icon: 'EditPen' },
        { value: 'pair', label: '结对编程', desc: '两人协作（兼容映射）', icon: 'UserFilled' },
        { value: 'group', label: '小组协作', desc: '多人协作（兼容映射）', icon: 'UserFilled' },
        { value: 'teacher_demo', label: '教师演示', desc: '教师主导演示（兼容映射）', icon: 'User' }
      ],

      monitorDropdownVisible: false,
      monitorSections: [
        { key: 'report', label: '班级学情周报' },
        { key: 'pulse', label: '班级学习脉搏' },
        { key: 'weak', label: '薄弱知识点 TOP3' },
        { key: 'risk', label: '风险学生预警' },
        { key: 'courseware', label: '课件使用分析' }
      ],

      inviteModalVisible: false,
      currentInvitation: null,
      generatingInvite: false,

      addProblemModalVisible: false,
      addProblemSearch: '',
      addProblemSearching: false,
      addProblemResults: [],
      addProblemAdding: {}
    }
  },
  computed: {
    isOwner () {
      return this.classroom.my_role === 'owner'
    },
    isStaff () {
      return ['owner', 'ta'].includes(this.classroom.my_role)
    },
    isStudent () {
      return this.classroom.my_role === 'student'
    },
    hasActiveInvitation () {
      return this.isInvitationReusable(this.currentInvitation)
    },
    pagedMembers () {
      const start = (this.memberPage - 1) * this.memberPageSize
      return this.members.slice(start, start + this.memberPageSize)
    },
    pagedProblems () {
      const start = (this.problemPage - 1) * this.problemPageSize
      return this.problems.slice(start, start + this.problemPageSize)
    }
  },
  mounted () {
    if (!this.classroomId) {
      this.$error('班级ID无效')
      this.$router.push({ name: 'classroom-list' })
      return
    }
    this.getClassroomDetail()
    this.getMembers()
    this.loadAICodingProblems()
  },
  methods: {
    scrollToMonitorSection (sectionKey) {
      this.activeTab = 'monitor'
      this.$nextTick(() => {
        this.$refs.analyticsRef?.scrollToSection(sectionKey)
      })
    },

    getClassroomDetail () {
      api.getClassroom(this.classroomId).then(res => {
        if (res.data && res.data.data) {
          this.classroom = res.data.data
          this.loadProblems()
        } else {
          this.$error('获取班级信息失败')
        }
      }).catch(() => {
        this.$error('获取班级信息失败')
      })
    },
    getMembers () {
      if (!this.classroomId) return
      this.memberLoading = true
      api.getClassroomMembers(this.classroomId).then(res => {
        this.members = (res.data && res.data.data && res.data.data.results) || []
        this.memberLoading = false
      }).catch(() => {
        this.memberLoading = false
        this.$error('获取成员列表失败')
      })
    },
    promoteMember (member) {
      api.promoteClassroomMember(this.classroomId, member.id).then(() => {
        this.$success('提升成功')
        this.getMembers()
      })
    },
    demoteMember (member) {
      api.demoteClassroomMember(this.classroomId, member.id).then(() => {
        this.$success('降级成功')
        this.getMembers()
      })
    },
    removeMember (member) {
      api.removeClassroomMember(this.classroomId, member.id).then(() => {
        this.$success('移除成功')
        this.getMembers()
      })
    },
    confirmRemoveMember (member) {
      ElMessageBox.confirm(
        `<p>确定要移除成员 <b>${member.user.username}</b> 吗？</p><p style="color: #ed4014; margin-top: 8px;">此操作不可撤销。</p>`,
        '确认移除成员',
        { confirmButtonText: '移除', cancelButtonText: '取消', dangerouslyUseHTMLString: true, type: 'warning' }
      ).then(() => {
        api.removeClassroomMember(this.classroomId, member.id).then(() => {
          this.$success('移除成功')
          this.getMembers()
        })
      }).catch(() => {})
    },
    showInviteModal () {
      this.inviteModalVisible = true
      this.currentInvitation = null
      api.getInvitationList(this.classroomId).then(res => {
        const invitationData = res.data && res.data.data
        const invitations = Array.isArray(invitationData) ? invitationData : (invitationData && invitationData.results) || []
        const active = invitations.find(inv => this.isInvitationReusable(inv))
        if (active) {
          this.currentInvitation = active
        }
      }).catch(() => {})
    },
    generateInvitation () {
      if (this.hasActiveInvitation) {
        this.$info('当前已有有效邀请码，无需重新生成')
        return
      }
      this.generatingInvite = true
      api.generateInvitation(this.classroomId, { max_uses: 100 }).then(res => {
        this.currentInvitation = res.data.data
        this.generatingInvite = false
        this.$success('邀请码生成成功')
      }).catch(() => {
        this.generatingInvite = false
      })
    },
    onCopySuccess () {
      this.$success('已复制到剪贴板')
    },
    isInvitationReusable (invitation) {
      if (!invitation || !invitation.is_active) return false
      const withinUsageLimit = invitation.usage_limit === 0 || invitation.usage_count < invitation.usage_limit
      if (!withinUsageLimit) return false
      if (!invitation.expires_at) return true
      return new Date(invitation.expires_at).getTime() > Date.now()
    },
    showAddProblemModal () {
      this.addProblemModalVisible = true
      this.addProblemSearch = ''
      this.addProblemResults = []
      this.searchProblems()
    },
    searchProblems () {
      const kw = (this.addProblemSearch || '').trim()
      this.addProblemSearching = true
      const params = { classroom_search: true }
      if (kw) params.keyword = kw
      if (this.classroom && this.classroom.language_pack && this.classroom.language_pack.id) {
        params.language_pack_id = this.classroom.language_pack.id
      }
      api.getProblemList(0, 50, params).then(res => {
        this.addProblemResults = (res.data && res.data.data && res.data.data.results) || []
        this.addProblemSearching = false
      }).catch(() => {
        this.addProblemSearching = false
        this.$error('搜索失败')
      })
    },
    addProblemToClassroom (problem) {
      this.addProblemAdding[problem.id] = true
      api.addClassroomProblem(this.classroomId, {
        problem_id: problem.id,
        is_visible: false,
        is_private: true
      }).then(() => {
        this.$success('题目已添加到班级（默认隐藏，点击"发布"后学生可见）')
        this.addProblemAdding[problem.id] = false
        this.loadProblems()
      }).catch(() => {
        this.addProblemAdding[problem.id] = false
        this.$error('添加失败')
      })
    },
    toggleProblemVisibility (row, visible) {
      api.updateClassroomProblem(this.classroomId, row.id, { is_visible: visible }).then(() => {
        this.$success(visible ? '题目已发布，学生现在可见' : '题目已隐藏')
        this.loadProblems()
      }).catch(err => {
        const msg = (err.response && err.response.data && err.response.data.error) || '操作失败'
        this.$error(msg)
      })
    },
    removeProblem (row) {
      api.removeClassroomProblem(this.classroomId, row.id).then(() => {
        this.$success('题目已移除')
        this.loadProblems()
      }).catch(err => {
        const msg = (err.response && err.response.data && err.response.data.error) || '移除失败'
        this.$error(msg)
      })
    },
    confirmRemoveProblem (row) {
      ElMessageBox.confirm(
        `<p>确定要从班级中移除题目 <b>${row.title}</b> 吗？</p><p style="color: #ed4014; margin-top: 8px;">移除后，学生将无法看到此题目及其提交记录。</p>`,
        '确认移除题目',
        { confirmButtonText: '移除', cancelButtonText: '取消', dangerouslyUseHTMLString: true, type: 'warning' }
      ).then(() => {
        api.removeClassroomProblem(this.classroomId, row.id).then(() => {
          this.$success('题目已移除')
          this.loadProblems()
        }).catch(err => {
          const msg = (err.response && err.response.data && err.response.data.error) || '移除失败'
          this.$error(msg)
        })
      }).catch(() => {})
    },
    goToProblem (problemId) {
      this.$router.push({ name: 'problem-details', params: { problemID: problemId } })
    },
    loadSessions () {
      this.sessionLoading = true
      api.getCollaborationSessions(this.classroomId).then(res => {
        const payload = (res && res.data) || {}
        this.sessions = (payload.data && payload.data.results) || payload.results || []
        this.sessionLoading = false
      }).catch(() => {
        this.sessionLoading = false
      })
    },
    createCollaborationSession () {
      const modeMap = {
        free: 'free',
        relay: 'relay',
        scaffolding: 'scaffolding',
        pair: 'free',
        group: 'free',
        teacher_demo: 'free'
      }
      const selectedMode = this.createSessionForm.mode
      const payload = {
        title: (this.createSessionForm.name || '').trim(),
        mode: modeMap[selectedMode] || 'free',
        classroom_problem: this.createSessionForm.problem_id || null,
        description: [
          this.createSessionForm.language ? `language:${this.createSessionForm.language}` : '',
          (selectedMode && ['pair', 'group', 'teacher_demo'].includes(selectedMode)) ? `ui_mode:${selectedMode}` : ''
        ].filter(Boolean).join(';')
      }
      if (!payload.title) {
        this.$error('请填写会话名称')
        return
      }
      api.createCollaborationSession(this.classroomId, payload).then(res => {
        this.$success('会话创建成功')
        this.showCreateSessionModal = false
        this.loadSessions()
        const sessionId = res.data.data.id
        this.joinSession({ id: sessionId })
      }).catch(err => {
        const resp = err && err.response && err.response.data
        let msg = (resp && (resp.error || resp.detail)) || '创建失败'
        if (resp && typeof resp === 'object' && !resp.error && !resp.detail) {
          const firstKey = Object.keys(resp)[0]
          if (firstKey) {
            const val = Array.isArray(resp[firstKey]) ? resp[firstKey][0] : resp[firstKey]
            msg = `${firstKey}: ${val}`
          }
        }
        this.$error(msg)
      })
    },
    joinSession (session) {
      this.$router.push({
        name: 'collaborative-coding',
        query: { ctx: encodeRouteCtx({ cid: this.classroomId, sid: session.id }) }
      })
    },
    viewSession () {
      this.$info('会话详情查看功能开发中')
    },
    confirmDeleteSession (session) {
      ElMessageBox.confirm(
        `<p>确定要删除会话 <b>${session.title}</b> 吗？</p><p style="color: #ed4014; margin-top: 8px;">此操作不可撤销。</p>`,
        '确认删除会话',
        { confirmButtonText: '删除', cancelButtonText: '取消', dangerouslyUseHTMLString: true, type: 'warning' }
      ).then(() => {
        api.deleteCollaborationSession(this.classroomId, session.id).then(() => {
          this.$success('会话已删除')
          this.loadSessions()
        }).catch(err => {
          const msg = (err.response && err.response.data && err.response.data.error) || '删除失败'
          this.$error(msg)
        })
      }).catch(() => {})
    },
    getSessionModeType (mode) {
      const types = {
        FREE: 'info',
        free: 'info',
        RELAY: 'primary',
        relay: 'primary',
        SCAFFOLDING: 'info',
        scaffolding: 'info'
      }
      return types[mode] || 'info'
    },
    getSessionModeText (mode) {
      const texts = {
        FREE: '自由协作',
        RELAY: '代码接力',
        SCAFFOLDING: '编程填空',
        free: '自由协作',
        relay: '代码接力',
        scaffolding: '编程填空'
      }
      return texts[mode] || mode
    },
    getRoleColor (role) {
      const colors = { owner: 'success', ta: 'primary', student: 'default' }
      return colors[role] || 'default'
    },
    getRoleTagStyle (role) {
      const map = {
        owner: { color: '#155724', background: '#d4edda', border: '#c3e6cb' },
        ta: { color: '#0f4c81', background: '#d6e9ff', border: '#b8daff' },
        student: { color: '#495057', background: '#e9ecef', border: '#ced4da' }
      }
      const s = map[role] || map.student
      return {
        color: s.color,
        backgroundColor: s.background,
        borderColor: s.border
      }
    },
    getRoleText (role) {
      const texts = { owner: '教师', ta: '助教', student: '学生' }
      return texts[role] || role
    },
    translateDifficulty (difficulty) {
      const map = { Low: '低', Mid: '中', High: '高' }
      return map[difficulty] || (difficulty || '-')
    },
    getACRateText (accepted, total) {
      const t = Number(total || 0)
      if (!t) return '0.00%'
      const a = Number(accepted || 0)
      return `${((a / t) * 100).toFixed(2)}%`
    },
    getDifficultyColor (difficulty) {
      const colors = { Easy: 'success', Medium: 'warning', Hard: 'error' }
      return colors[difficulty] || 'default'
    },
    loadProblems () {
      this.problemLoading = true
      api.getClassroomProblems(this.classroomId).then(res => {
        this.problems = (res.data && res.data.data && res.data.data.results) || []
        this.problemLoading = false
      }).catch(() => {
        this.problemLoading = false
      })
    },
    loadAICodingProblems () {
      api.getAIGeneratedProblems(this.classroomId, { limit: 100 }).then(res => {
        const all = (res.data && res.data.data && res.data.data.results) || []
        this.aiCodingProblems = all.filter(p => p.question_type === 'coding' && p.status === 'passed' && p.problem_id)
      }).catch(() => {})
    },
    triggerObjectiveImport () {
      if (this.$refs.objectiveImportInput) {
        this.$refs.objectiveImportInput.value = ''
        this.$refs.objectiveImportInput.click()
      }
    },
    onObjectiveImportFileChange (e) {
      const file = e && e.target && e.target.files && e.target.files[0]
      if (!file) return
      const form = new FormData()
      form.append('file', file)
      api.importObjectiveQuestions(this.classroomId, form).then(res => {
        const count = (res.data && res.data.data && res.data.data.imported_count) || 0
        this.$success(`导入成功：${count} 道题`)
        this.loadProblems()
      })
    },
    exportObjectiveQuestions () {
      api.exportObjectiveQuestions(this.classroomId).then(res => {
        const payload = (res.data && res.data.data) || {}
        const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json;charset=utf-8' })
        const url = window.URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = url
        a.download = `classroom-objective-questions-${this.classroomId}.json`
        a.click()
        window.URL.revokeObjectURL(url)
      })
    },
    formatTime (timeStr) {
      return time.utcToLocal(timeStr, 'YYYY-MM-DD HH:mm')
    },
    loadStats () {
      const baseStats = {
        member_count: this.members.length || this.classroom.member_count || 0,
        problem_count: 0,
        submission_count: 0,
        avg_ac_rate: 0
      }
      Promise.all([
        api.getClassroomStats(this.classroomId).catch(() => null),
        api.getClassroomProblems(this.classroomId).catch(() => null)
      ]).then(([classroomStatsRes, problemsRes]) => {
        const classroomStats = classroomStatsRes && classroomStatsRes.data && classroomStatsRes.data.data
        const problems = (problemsRes && problemsRes.data && problemsRes.data.data && problemsRes.data.data.results) || this.problems || []
        const problemCount = problems.length
        const submissionCount = problems.reduce((sum, p) => sum + Number(p.submission_number || 0), 0)
        const acceptedCount = problems.reduce((sum, p) => sum + Number(p.accepted_number || 0), 0)
        const avgAcRate = submissionCount > 0 ? Number(((acceptedCount / submissionCount) * 100).toFixed(2)) : 0

        this.stats = {
          member_count: (classroomStats && classroomStats.member_count) || baseStats.member_count,
          problem_count: problemCount,
          submission_count: submissionCount,
          avg_ac_rate: avgAcRate
        }
      }).catch(() => {
        this.stats = baseStats
      })
    }
  },
  watch: {
    activeTab (newVal) {
      if (newVal === 'problems') {
        this.loadProblems()
      }
    }
  }
}
</script>

<style lang="less" scoped>
.classroom-detail-container {
  margin: 20px auto;
  max-width: 1400px;

  .classroom-header {
    margin-bottom: 20px;

    h1 {
      font-size: 28px;
      font-weight: 600;
      margin-bottom: 10px;
    }

    .meta {
      color: #808695;
      margin-bottom: 10px;

      .divider {
        margin: 0 10px;
      }
    }

    .description {
      color: #515a6e;
      margin: 15px 0;
    }

    .language-pack-banner {
      display: inline-flex;
      align-items: center;
      gap: 12px;
      flex-wrap: wrap;
      margin-top: 8px;
      padding: 10px 14px;
      border-radius: 14px;
      background: linear-gradient(135deg, rgba(45, 140, 240, 0.12), rgba(39, 186, 108, 0.08));
      border: 1px solid rgba(45, 140, 240, 0.14);
    }

    .language-pack-banner-label {
      color: #2d8cf0;
      font-size: 12px;
      font-weight: 700;
      letter-spacing: 0.08em;
    }

    .language-pack-banner-content {
      display: flex;
      align-items: center;
      gap: 10px;
      flex-wrap: wrap;
    }

    .language-pack-banner-name {
      color: #17233d;
      font-size: 14px;
      font-weight: 700;
    }

    .language-pack-banner-meta {
      color: #515a6e;
      font-size: 13px;
    }

    .actions {
      margin-top: 15px;

      button {
        margin-right: 10px;
      }
    }
  }

  .stat-label {
    color: #808695;
    font-size: 14px;
    margin-bottom: 5px;
  }

  .stat-value {
    font-size: 32px;
    font-weight: 600;
    color: #2d8cf0;
  }

  .session-created-at-text {
    display: inline-block;
    white-space: nowrap;
  }

  :deep(.classroom-problem-panel > .el-card__header),
  :deep(.session-panel > .el-card__header) {
    padding: 10px 16px;
  }

  .panel-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .panel-title {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    font-size: 14px;
    font-weight: 600;
    color: #17233d;
  }

  .panel-actions {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  @media (max-width: 768px) {
    .classroom-header {
      .meta {
        display: flex;
        flex-wrap: wrap;
        gap: 8px;

        .divider {
          display: none;
        }
      }

      .actions {
        display: flex;
        flex-direction: column;
        align-items: stretch;

        button {
          margin-right: 0;
          margin-bottom: 10px;
        }
      }
    }
  }

}
</style>

<style lang="less">
.create-session-modal {
  .el-dialog__header {
    border-bottom: none;
    padding: 24px 24px 0;

    .el-dialog__title {
      font-size: 20px;
      font-weight: 600;
      color: #17233d;
    }
  }

  .el-dialog__body {
    padding: 24px;
  }

  .el-dialog__footer {
    border-top: none;
    padding: 12px 24px 24px;
  }

  .session-form {
    .form-item-promax {
      margin-bottom: 24px;

      .el-form-item__label {
        font-size: 14px;
        font-weight: 600;
        color: #17233d;
        padding-bottom: 10px;
      }
    }

    .mode-selection-grid {
      display: grid;
      grid-template-columns: repeat(2, 1fr);
      gap: 16px;

      .mode-card {
        position: relative;
        display: flex;
        align-items: center;
        padding: 16px;
        border: 1px solid #dcdee2;
        border-radius: 8px;
        cursor: pointer;
        transition: all 0.2s ease;
        background: #fff;

        &:hover {
          border-color: #57a3f3;
          box-shadow: 0 2px 8px rgba(45, 140, 240, 0.1);
        }

        &.active {
          border-color: #2d8cf0;
          background-color: #f0faff;

          .mode-icon {
            color: #2d8cf0;
            background: #fff;
          }

          .mode-title {
            color: #2d8cf0;
          }
        }

        .mode-icon {
          width: 40px;
          height: 40px;
          display: flex;
          align-items: center;
          justify-content: center;
          background: #f8f8f9;
          border-radius: 8px;
          margin-right: 12px;
          color: #515a6e;
          transition: all 0.2s;
        }

        .mode-info {
          flex: 1;
          display: flex;
          flex-direction: column;

          .mode-title {
            font-size: 14px;
            font-weight: 600;
            color: #17233d;
            margin-bottom: 4px;
          }

          .mode-desc {
            font-size: 12px;
            color: #808695;
          }
        }

        .mode-check {
          position: absolute;
          top: 8px;
          right: 8px;
          color: #2d8cf0;
        }
      }
    }

    .lang-option {
      display: flex;
      align-items: center;

      i {
        font-size: 16px;
        margin-right: 8px;
      }
    }
  }
}

.classroom-detail-container .el-tabs__nav-wrap {
  overflow: visible !important;
}
.classroom-detail-container .el-tabs__nav-scroll {
  overflow: visible !important;
}
.classroom-detail-container .el-tabs__nav {
  overflow: visible !important;
}

.monitor-tab-trigger {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
}

.monitor-caret {
  font-size: 12px;
  color: #909399;
  transition: transform 0.2s;

  &.rotated {
    transform: rotate(180deg);
  }
}

.monitor-dropdown {
  position: absolute;
  top: 100%;
  left: 50%;
  transform: translateX(-50%);
  z-index: 2000;
  min-width: 160px;
  padding: 6px 0;
  margin-top: 8px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
  border: 1px solid #e8e8e8;
}

.monitor-dropdown-item {
  padding: 8px 16px;
  font-size: 13px;
  color: #303133;
  white-space: nowrap;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;

  &:hover {
    background: #ecf5ff;
    color: #409eff;
  }
}

.monitor-dropdown-fade-enter-active,
.monitor-dropdown-fade-leave-active {
  transition: opacity 0.15s, transform 0.15s;
}
.monitor-dropdown-fade-enter-from,
.monitor-dropdown-fade-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(-4px);
}
</style>
