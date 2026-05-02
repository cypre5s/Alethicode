<template>
  <div class="classroom-view">
    <div class="leetcode-card">
      <div class="card-header">
        <div class="header-title">我的班级</div>
        <div class="header-tools">
          <el-input v-model="query.keyword"
                    placeholder="搜索班级..."
                    @keyup.enter="getClassroomList"
                    class="search-input">
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>

          <el-select v-model="query.role" placeholder="角色筛选" class="role-select" @change="getClassroomList">
            <el-option value="" label="全部角色"></el-option>
            <el-option value="owner" label="我创建的"></el-option>
            <el-option value="ta" label="我助教的"></el-option>
            <el-option value="student" label="我参与的"></el-option>
          </el-select>

          <div class="action-buttons">
            <el-button class="action-btn join-btn" @click="$router.push({ name: 'classroom-join' })">
              加入班级
            </el-button>
            <el-button v-if="isTeacher" type="primary" class="action-btn create-btn" @click="showCreateModal = true">
              <el-icon><Plus /></el-icon> 创建班级
            </el-button>
          </div>
        </div>
      </div>

        <div class="table-container">
        <el-table :data="classrooms" v-loading="loading">
          <el-table-column label="班级名称" min-width="200" align="center">
            <template #default="scope">
              <div class="classroom-name-cell">
                <span class="classroom-name-link" @click="goToClassroom(scope.row.id)">{{ scope.row.name }}</span>
                <div v-if="scope.row.language_pack" class="pack-inline-meta">
                  <span class="pack-pill">{{ scope.row.language_pack.primary_language }}</span>
                  <span class="pack-inline-name">{{ formatLanguagePack(scope.row.language_pack) }}</span>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="课程代码" width="120" align="center">
            <template #default="scope">
              <span class="text-secondary">{{ scope.row.course_code }}</span>
            </template>
          </el-table-column>
          <el-table-column label="学期" width="120" align="center">
            <template #default="scope">
              <span class="text-secondary">{{ scope.row.semester }}</span>
            </template>
          </el-table-column>
          <el-table-column label="身份" width="100" align="center">
            <template #default="scope">
              <span :class="['leetcode-tag', getRoleClass(scope.row.my_role)]">{{ getRoleText(scope.row.my_role) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="成员" width="100" align="center">
            <template #default="scope">
              <div class="member-count">
                <el-icon style="margin-right: 4px"><User /></el-icon>
                <span>{{ scope.row.member_count || 0 }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="创建时间" width="160" align="center">
            <template #default="scope">
              <span class="text-secondary">{{ formatTime(scope.row.create_time) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="180" align="center">
            <template #default="scope">
              <div class="action-group">
                <el-button type="primary" link size="small" class="table-btn btn-enter" @click="goToClassroom(scope.row.id)">进入</el-button>
                <el-button v-if="scope.row.my_role === 'owner'" type="primary" link size="small" class="table-btn" @click="editClassroom(scope.row)">编辑</el-button>
                <el-button v-if="scope.row.my_role === 'owner'" type="primary" link size="small" class="table-btn" @click="showInviteModal(scope.row)">邀请</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="pagination-container">
        <el-pagination :total="total"
              :current-page="page"
              :page-size="limit"
              @current-change="onPageChange"
              layout="total, prev, pager, next"/>
      </div>
    </div>

    <!-- 创建班级 Dialog -->
    <el-dialog v-model="showCreateModal" title="创建班级" class="leetcode-modal">
        <el-form ref="createForm" :model="createForm" :rules="createRules" :label-width="100">
        <el-form-item label="班级名称" prop="name">
          <el-input v-model="createForm.name" placeholder="例如：Python程序设计2024春季"/>
        </el-form-item>
        <el-form-item label="课程内容包" prop="language_pack_id">
          <el-select
            v-model="createForm.language_pack_id"
            placeholder="请选择已发布课程内容包"
            class="language-pack-select"
            filterable
            :loading="languagePackLoading">
            <el-option
              v-for="pack in languagePackOptions"
              :key="pack.id"
              :label="formatLanguagePack(pack)"
              :value="pack.id">
              <div class="language-pack-option">
                <span class="language-pack-option-name">{{ pack.name }}</span>
                <span class="language-pack-option-meta">{{ pack.primary_language }} · v{{ pack.version }}</span>
              </div>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="课程代码" prop="course_code">
          <el-input v-model="createForm.course_code" placeholder="例如：CS101"/>
        </el-form-item>
        <el-form-item label="学期" prop="semester">
          <el-input v-model="createForm.semester" placeholder="例如：2024春季"/>
        </el-form-item>
        <el-form-item label="班级描述">
          <el-input v-model="createForm.description" type="textarea" :rows="4" placeholder="选填"/>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateModal = false">取消</el-button>
        <el-button type="primary" @click="createClassroom">确定</el-button>
      </template>
    </el-dialog>

    <!-- 编辑班级 Dialog -->
    <el-dialog v-model="showEditModal" title="编辑班级" class="leetcode-modal">
      <el-form ref="editForm" :model="editForm" :rules="createRules" :label-width="100">
        <el-form-item label="班级名称" prop="name">
          <el-input v-model="editForm.name" placeholder="班级名称"/>
        </el-form-item>
        <el-form-item label="课程代码" prop="course_code">
          <el-input v-model="editForm.course_code" placeholder="课程代码"/>
        </el-form-item>
        <el-form-item label="学期" prop="semester">
          <el-input v-model="editForm.semester" placeholder="学期"/>
        </el-form-item>
        <el-form-item label="班级描述">
          <el-input v-model="editForm.description" type="textarea" :rows="4" placeholder="选填"/>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEditModal = false">取消</el-button>
        <el-button type="primary" @click="updateClassroom">确定</el-button>
      </template>
    </el-dialog>

    <!-- 邀请码 Dialog -->
    <el-dialog v-model="showInviteModalVisible" title="邀请成员" width="600" class="leetcode-modal">
      <div v-if="currentInvitation" class="invite-content">
        <el-alert show-icon :closable="false" class="invite-alert">
          <span class="invite-label">邀请码：</span>
          <strong class="invite-code">{{ currentInvitation.code }}</strong>
          <el-button type="primary" link class="copy-btn" v-clipboard:copy="currentInvitation.code" v-clipboard:success="onCopySuccess">
            复制
          </el-button>
        </el-alert>
        <div class="invite-info">
          <p>有效期至：{{ formatTime(currentInvitation.expires_at) }}</p>
          <p>剩余次数：{{ currentInvitation.usage_limit - currentInvitation.usage_count }}</p>
        </div>
      </div>
      <el-button type="primary" style="width:100%" @click="generateInvitation" :loading="generatingInvite" class="generate-btn">
        生成新邀请码
      </el-button>
    </el-dialog>
  </div>
</template>

<script>
import api from '@oj/api'
import { Search, Plus, User } from '@element-plus/icons-vue'
import time from '@/utils/time'
import { encodeRouteCtx } from '@/utils/urlCipher'

export default {
  name: 'ClassroomList',
  components: {
    Search,
    Plus,
    User
  },
  data () {
    return {
      loading: false,
      classrooms: [],
      total: 0,
      page: 1,
      limit: 20,
      query: {
        keyword: '',
        role: ''
      },
      showCreateModal: false,
      createForm: {
        name: '',
        language_pack_id: '',
        course_code: '',
        semester: '',
        description: ''
      },
      createRules: {
        name: [
          { required: true, message: '请输入班级名称', trigger: 'blur' }
        ],
        language_pack_id: [
          { required: true, message: '请选择课程内容包', trigger: 'change' }
        ],
        course_code: [
          { required: true, message: '请输入课程代码', trigger: 'blur' }
        ]
      },
      languagePackOptions: [],
      languagePackLoading: false,
      showEditModal: false,
      editForm: {
        id: '',
        name: '',
        course_code: '',
        semester: '',
        description: ''
      },
      showInviteModalVisible: false,
      currentInvitation: null,
      currentClassroom: null,
      generatingInvite: false
    }
  },
  computed: {
    isTeacher () {
      const profile = this.$store.state.user.profile
      const user = this.$store.getters.user
      const profileRole = profile && profile.role
        ? String(profile.role).trim().toLowerCase()
        : ''
      const adminType = user && user.admin_type
        ? String(user.admin_type).trim().toLowerCase()
        : ''

      return ['teacher', 'admin'].includes(profileRole) ||
        ['teacher', 'admin'].includes(adminType)
    }
  },
  mounted () {
    this.getClassroomList()
    if (this.isTeacher) {
      this.loadLanguagePackOptions()
    }
  },
  methods: {
    getRoleClass (role) {
      if (role === 'owner') return 'tag-owner'
      if (role === 'ta') return 'tag-ta'
      return 'tag-student'
    },
    formatLanguagePack (pack) {
      if (!pack) return ''
      return `${pack.name} · v${pack.version}`
    },
    getRoleText (role) {
      if (role === 'owner') return '教师'
      if (role === 'ta') return '助教'
      return '学生'
    },
    loadLanguagePackOptions () {
      this.languagePackLoading = true
      api.getLanguagePackList().then(res => {
        const rows = (res.data && res.data.data) || []
        this.languagePackOptions = rows.filter(pack => !pack.status || pack.status === 'published')
        this.languagePackLoading = false
      }).catch(() => {
        this.languagePackLoading = false
        this.$error('课程内容包列表加载失败')
      })
    },
    getClassroomList () {
      this.loading = true
      const params = {
        page: this.page,
        limit: this.limit,
        ...this.query
      }
      api.getClassroomList(params).then(res => {
        if (res.data && res.data.data) {
          this.classrooms = Array.isArray(res.data.data.results) ? res.data.data.results : []
          this.total = res.data.data.total || 0
        } else {
          this.classrooms = []
          this.total = 0
        }
        this.loading = false
      }).catch(err => {
        this.loading = false
      })
    },
    createClassroom () {
      this.$refs.createForm.validate(valid => {
        if (valid) {
          api.createClassroom(this.createForm).then(res => {
            this.$success('创建成功')
            this.showCreateModal = false
            this.getClassroomList()
            this.createForm = {
              name: '',
              language_pack_id: '',
              course_code: '',
              semester: '',
              description: ''
            }
          })
        }
      })
    },
    editClassroom (classroom) {
      this.editForm = {
        id: classroom.id,
        name: classroom.name,
        course_code: classroom.course_code,
        semester: classroom.semester,
        description: classroom.description || ''
      }
      this.showEditModal = true
    },
    updateClassroom () {
      this.$refs.editForm.validate(valid => {
        if (valid) {
          const { id, ...updateData } = this.editForm
          api.updateClassroom(id, updateData).then(res => {
            this.$success('更新成功')
            this.showEditModal = false
            this.getClassroomList()
          })
        }
      })
    },
    showInviteModal (classroom) {
      this.currentClassroom = classroom
      this.showInviteModalVisible = true
      this.currentInvitation = null
    },
    generateInvitation () {
      if (!this.currentClassroom) return
      this.generatingInvite = true
      api.generateInvitation(this.currentClassroom.id, {}).then(res => {
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
    goToClassroom (id) {
      this.$router.push({ name: 'classroom-detail', query: { ctx: encodeRouteCtx({ id }) } })
    },
    onPageChange (page) {
      this.page = page
      this.getClassroomList()
    },
    formatTime (timeStr) {
      return time.utcToLocal(timeStr, 'YYYY-MM-DD')
    }
  }
}
</script>

<style lang="less" scoped>
@primary-color: #2d8cf0;
@success-color: #27ba6c;
@warning-color: #ff9900;
@bg-color: #f7f8fa;
@card-bg: #ffffff;
@text-main: #262626;
@text-light: #8c8c8c;
@border-color: #f0f0f0;

.classroom-view {
  min-height: 100vh;
  background-color: @bg-color;
  padding: 24px;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Helvetica, Arial, sans-serif;
}

.leetcode-card {
  background: @card-bg;
  border-radius: 12px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
  padding: 24px;
  max-width: 1200px;
  margin: 0 auto;
  border: none;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;

  .header-title {
    font-size: 20px;
    font-weight: 600;
    color: @text-main;
  }

  .header-tools {
    display: flex;
    align-items: center;
    gap: 12px;

    .search-input {
      width: 240px;
    }

    .role-select {
      width: 140px;
    }

    .action-buttons {
      display: flex;
      gap: 12px;
      margin-left: 12px;

      .action-btn {
        height: 32px;
        padding: 0 16px;
        border-radius: 6px;
        font-weight: 500;
        transition: all 0.2s ease;

        &:hover {
          transform: scale(1.02);
        }

        &.create-btn {
          background-color: @primary-color;
          border-color: @primary-color;
          box-shadow: 0 2px 6px rgba(45, 140, 240, 0.2);
        }

        &.join-btn {
          color: @success-color;
          border: 1px solid @success-color;
          background-color: #fff;

          &:hover {
            background-color: rgba(39, 186, 108, 0.05);
            color: @success-color;
          }
        }
      }
    }
  }
}

.table-container {
  .classroom-name-link {
    font-weight: 600;
    color: @text-main;
    cursor: pointer;
    transition: color 0.2s;

    &:hover {
      color: @primary-color;
    }
  }

  .classroom-name-cell {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 6px;
  }

  .pack-inline-meta {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
    justify-content: center;
  }

  .pack-pill {
    display: inline-flex;
    align-items: center;
    min-height: 24px;
    padding: 0 10px;
    border-radius: 999px;
    background: linear-gradient(135deg, rgba(45, 140, 240, 0.14), rgba(39, 186, 108, 0.12));
    color: @primary-color;
    font-size: 12px;
    font-weight: 600;
  }

  .pack-inline-name {
    color: @text-light;
    font-size: 12px;
    line-height: 1.5;
  }

  .text-secondary {
    color: @text-light;
  }

  .member-count {
    color: @text-light;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .leetcode-tag {
    display: inline-block;
    padding: 4px 12px;
    border-radius: 12px;
    font-size: 12px;
    font-weight: 500;
    white-space: nowrap;

    &.tag-owner {
      background-color: rgba(39, 186, 108, 0.1);
      color: #000;
    }

    &.tag-ta {
      background-color: rgba(64, 158, 255, 0.1);
      color: #000;
    }

    &.tag-student {
      background-color: rgba(144, 147, 153, 0.1);
      color: #909399;
    }
  }

  .action-group {
    display: flex;
    justify-content: center;
    align-items: center;

    .table-btn {
      margin: 0 6px;
      color: @text-light;

      &:hover {
        color: @primary-color;
      }

      &.btn-enter {
        color: @primary-color;
        font-weight: 500;
      }
    }
  }
}

.language-pack-select {
  width: 100%;
}

.language-pack-option {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
}

.language-pack-option-name {
  color: @text-main;
  font-weight: 600;
}

.language-pack-option-meta {
  color: @text-light;
  font-size: 12px;
}

.pagination-container {
  margin-top: 24px;
  display: flex;
  justify-content: flex-end;
}

.invite-content {
  text-align: center;
  margin-bottom: 20px;

  .invite-alert {
    background-color: rgba(45, 140, 240, 0.05);
    border: 1px solid rgba(45, 140, 240, 0.2);
    display: inline-block;
    padding: 12px 24px;

    .invite-label {
      color: @text-light;
    }

    .invite-code {
      font-size: 20px;
      color: @primary-color;
      margin: 0 12px;
      font-family: monospace;
    }

    .copy-btn {
      color: @primary-color;
    }
  }

  .invite-info {
    margin-top: 12px;
    color: @text-light;
    font-size: 13px;
    line-height: 1.6;
  }
}

.generate-btn {
  height: 40px;
  font-size: 15px;
}

@media (max-width: 768px) {
  .card-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 16px;

    .header-tools {
      width: 100%;
      flex-wrap: wrap;

      .search-input, .role-select {
        width: 100%;
      }

      .action-buttons {
        width: 100%;
        margin-left: 0;

        .action-btn {
          flex: 1;
        }
      }
    }
  }
}
</style>
