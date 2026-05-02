<template>
  <div class="view">
    <Panel :title="$t('m.User_User') ">
      <template #header><div>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-button v-show="selectedUsers.length"
                       type="warning" icon="el-icon-fa-trash"
                       @click="deleteUsers(selectedUserIDs)">批量删除
            </el-button>
          </el-col>
          <el-col :span="selectedUsers.length ? 16: 24">
            <el-input v-model="keyword" prefix-icon="el-icon-search" placeholder="搜索用户名、邮箱或姓名"></el-input>
          </el-col>
        </el-row>
      </div></template>
      <el-table
        v-loading="loadingTable"
        element-loading-text="加载中"
        @selection-change="handleSelectionChange"
        ref="table"
        :data="userList"
        style="width: 100%">
        <el-table-column type="selection" width="55"></el-table-column>

        <el-table-column prop="id" label="ID" width="80" align="center"></el-table-column>

        <el-table-column prop="username" label="用户名" align="center"></el-table-column>

        <el-table-column prop="create_time" label="创建时间" align="center">
          <template #default="scope">
            {{ localtime(scope.row.create_time) }}
          </template>
        </el-table-column>

        <el-table-column prop="last_login" label="最近登录" align="center">
          <template #default="scope">
            {{ localtime(scope.row.last_login) }}
          </template>
        </el-table-column>

        <el-table-column prop="real_name" label="真实姓名" align="center"></el-table-column>

        <el-table-column prop="email" label="邮箱" align="center"></el-table-column>

        <el-table-column prop="admin_type" label="用户角色" align="center">
          <template #default="scope">
            {{ scope.row.admin_type }}
          </template>
        </el-table-column>

        <el-table-column fixed="right" label="操作" width="200" align="center">
          <template #default="{row}">
            <icon-btn name="编辑用户" icon="edit" @click="openUserDialog(row.id)"></icon-btn>
            <icon-btn name="删除用户" icon="trash" @click="deleteUsers([row.id])"></icon-btn>
          </template>
        </el-table-column>
      </el-table>
      <div class="panel-options">
        <AdminPagination
          :total="total"
          :current-page="currentPage"
          :page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          @update:currentPage="currentPage = $event"
          @update:pageSize="pageSize = $event"
          @change="handleUserPaginationChange">
        </AdminPagination>
      </div>
    </Panel>

    <Panel>
      <template #title><span>{{$t('m.Import_User')}}
        <el-tooltip content="查看导入格式说明" placement="top">
          <el-button text circle size="small" class="import-help-btn" @click="showImportHelp = true">?</el-button>
        </el-tooltip>
      </span></template>
      <el-upload v-if="!uploadUsers.length"
                 action=""
                 :show-file-list="false"
                 accept=".csv"
                 :before-upload="handleUsersCSV">
        <el-button size="small" icon="el-icon-fa-upload" type="primary">选择文件</el-button>
      </el-upload>
      <template v-else>
        <el-table :data="uploadUsersPage">
          <el-table-column label="用户名">
            <template #default="{row}">
              {{row[0]}}
            </template>
          </el-table-column>
          <el-table-column label="密码">
            <template #default="{row}">
              {{row[1]}}
            </template>
          </el-table-column>
          <el-table-column label="邮箱">
            <template #default="{row}">
              {{row[2]}}
            </template>
          </el-table-column>
          <el-table-column label="真实姓名">
            <template #default="{row}">
              {{row[3]}}
            </template>
          </el-table-column>
        </el-table>
        <div class="panel-options">
          <el-button type="primary" size="small"
                     icon="el-icon-fa-upload"
                     @click="handleUsersUpload">导入全部
          </el-button>
          <el-button type="warning" size="small"
                     icon="el-icon-fa-undo"
                     @click="handleResetData">清空数据
          </el-button>
          <AdminPagination
            :total="uploadUsers.length"
            :current-page="uploadUsersCurrentPage"
            :page-size="uploadUsersPageSize"
            :page-sizes="[15, 30, 50, 100]"
            @update:currentPage="uploadUsersCurrentPage = $event"
            @update:pageSize="uploadUsersPageSize = $event"
            @change="handleUploadPaginationChange">
          </AdminPagination>
        </div>
      </template>
    </Panel>

    <Panel :title="$t('m.Generate_User')">
      <el-form :model="formGenerateUser" ref="formGenerateUser">
        <el-row type="flex" justify="space-between">
          <el-col :span="4">
            <el-form-item label="前缀" prop="prefix">
              <el-input v-model="formGenerateUser.prefix" placeholder="前缀"></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="4">
            <el-form-item label="后缀" prop="suffix">
              <el-input v-model="formGenerateUser.suffix" placeholder="后缀"></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="4">
            <el-form-item label="起始编号" prop="number_from" required>
              <el-input-number v-model="formGenerateUser.number_from" style="width: 100%"></el-input-number>
            </el-form-item>
          </el-col>
          <el-col :span="4">
            <el-form-item label="结束编号" prop="number_to" required>
              <el-input-number v-model="formGenerateUser.number_to" style="width: 100%"></el-input-number>
            </el-form-item>
          </el-col>
          <el-col :span="4">
            <el-form-item label="密码长度" prop="password_length" required>
              <el-input v-model="formGenerateUser.password_length"
                        placeholder="密码长度"></el-input>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item>
          <el-button type="primary" @click="generateUser" icon="el-icon-fa-users" :loading="loadingGenerate">生成并导出
          </el-button>
          <span class="userPreview" v-if="formGenerateUser.number_from && formGenerateUser.number_to &&
                                          formGenerateUser.number_from <= formGenerateUser.number_to">
            将生成用户名：{{formGenerateUser.prefix + formGenerateUser.number_from + formGenerateUser.suffix}}，
            <span v-if="formGenerateUser.number_from + 1 < formGenerateUser.number_to">
              {{formGenerateUser.prefix + (formGenerateUser.number_from + 1) + formGenerateUser.suffix + '...'}}
            </span>
            <span v-if="formGenerateUser.number_from + 1 <= formGenerateUser.number_to">
              {{formGenerateUser.prefix + formGenerateUser.number_to + formGenerateUser.suffix}}
            </span>
          </span>
        </el-form-item>
      </el-form>
    </Panel>
    <!--对话框-->
    <el-dialog :title="$t('m.User_Info')" v-model="showUserDialog" :close-on-click-modal="false">
      <el-form :model="user" label-width="120px" label-position="left">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item :label="$t('m.User_Username')" required>
              <el-input v-model="user.username"></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$t('m.User_Real_Name')" required>
              <el-input v-model="user.real_name"></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$t('m.User_Email')" required>
              <el-input v-model="user.email"></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$t('m.User_New_Password')">
              <el-input v-model="user.password"></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$t('m.User_Type')">
              <el-select v-model="user.admin_type">
                <el-option label="普通用户" value="Regular User"></el-option>
                <el-option label="教师" value="Teacher"></el-option>
                <el-option label="管理员" value="Admin"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$t('m.Problem_Permission')">
              <el-select v-model="user.problem_permission" :disabled="user.admin_type !== 'Teacher'">
                <el-option label="无" value="None"></el-option>
                <el-option label="仅本人" value="Own"></el-option>
                <el-option label="全部题目" value="All"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$t('m.Two_Factor_Auth')">
              <el-switch
                v-model="user.two_factor_auth"
                :disabled="!user.real_tfa"
                active-color="#13ce66"
                inactive-color="#ff4949">
              </el-switch>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="开放 API">
              <el-switch
                v-model="user.open_api"
                active-color="#13ce66"
                inactive-color="#ff4949">
              </el-switch>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$t('m.Is_Disabled')">
              <el-switch
                v-model="user.is_disabled">
              </el-switch>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <cancel @click="showUserDialog = false">取消</cancel>
          <save @click="saveUser()"></save>
        </span>
      </template>
    </el-dialog>

    <el-dialog v-model="showImportHelp" title="用户导入格式说明" width="600px">
      <div class="import-help-content">
        <h4>支持格式</h4>
        <p>CSV 文件（不带表头），每行一个用户，字段用逗号分隔。</p>
        <h4>字段顺序</h4>
        <table class="help-table">
          <thead>
            <tr><th>顺序</th><th>字段</th><th>必须</th><th>说明</th></tr>
          </thead>
          <tbody>
            <tr><td>1</td><td>用户名</td><td>是</td><td>登录账号</td></tr>
            <tr><td>2</td><td>密码</td><td>是</td><td>初始密码</td></tr>
            <tr><td>3</td><td>邮箱</td><td>是</td><td>用户邮箱</td></tr>
            <tr><td>4</td><td>真实姓名</td><td>是</td><td>用户真实姓名</td></tr>
          </tbody>
        </table>
        <h4>示例</h4>
        <pre><code>student01,pass123,s01@example.com,张三
student02,pass456,s02@example.com,李四</code></pre>
        <p style="color: #909399; font-size: 12px; margin-top: 12px;">四个字段均为必填，任一字段为空的行将被自动过滤。</p>
      </div>
    </el-dialog>
  </div>
</template>

<script>
  import papa from 'papaparse'
  import api from '../../api.js'
  import utils from '@/utils/utils'
  import { utcToLocal } from '@/utils/time'

  export default {
    name: 'User',
    data () {
      return {
        // 一页显示的用户数
        pageSize: 10,
        // 用户总数
        total: 0,
        // 用户列表
        userList: [],
        uploadUsers: [],
        uploadUsersPage: [],
        uploadUsersCurrentPage: 1,
        uploadUsersPageSize: 15,
        // 搜索关键字
        keyword: '',
        showUserDialog: false,
        showImportHelp: false,
        // 当前用户model
        user: {},
        loadingTable: false,
        loadingGenerate: false,
        // 当前页码
        currentPage: 1,
        selectedUsers: [],
        formGenerateUser: {
          prefix: '',
          suffix: '',
          number_from: 0,
          number_to: 0,
          password_length: 8
        }
      }
    },
    mounted () {
      this.getUserList(1)
    },
    methods: {
      localtime: utcToLocal,
      handleUserPaginationChange ({ page, pageSize }) {
        this.currentPage = page
        this.pageSize = pageSize
        this.getUserList(page)
      },
      // 提交修改用户的信息
      saveUser () {
        api.editUser(this.user).then(() => {
          // 更新列表
          this.getUserList(this.currentPage)
        }).then(() => {
          this.showUserDialog = false
        }).catch(() => {
        })
      },
      // 打开用户对话框
      openUserDialog (id) {
        this.showUserDialog = true
        api.getUser(id).then(res => {
          this.user = res.data.data
          this.user.password = ''
          this.user.real_tfa = this.user.two_factor_auth
        })
      },
      // 获取用户列表
      getUserList (page) {
        this.loadingTable = true
        api.getUserList((page - 1) * this.pageSize, this.pageSize, this.keyword).then(res => {
          this.loadingTable = false
          this.total = res.data.data.total
          this.userList = res.data.data.results
        }, () => {
          this.loadingTable = false
        })
      },
      deleteUsers (ids) {
        this.$confirm('确认删除该用户？该用户创建的题目、公告等关联资源也会一并删除。', '删除用户', {
          type: 'warning'
        }).then(() => {
          api.deleteUsers(ids.join(',')).then(() => {
            this.getUserList(this.currentPage)
          }).catch(() => {
            this.getUserList(this.currentPage)
          })
        }, () => {
        })
      },
      handleSelectionChange (val) {
        this.selectedUsers = val
      },
      generateUser () {
        this.$refs['formGenerateUser'].validate((valid) => {
          if (!valid) {
            this.$error('请先修正表单中的校验错误')
            return
          }
          this.loadingGenerate = true
          let data = Object.assign({}, this.formGenerateUser)
          api.generateUser(data).then(res => {
            this.loadingGenerate = false
            let url = '/admin/generate-user?file_id=' + res.data.data.file_id
            utils.downloadFile(url).then(() => {
              this.$alert('用户已全部创建，账号文件已下载到本地。', '生成完成')
            })
            this.getUserList(1)
          }).catch(() => {
            this.loadingGenerate = false
          })
        })
      },
      handleUsersCSV (file) {
        papa.parse(file, {
          complete: (results) => {
            let data = results.data.filter(user => {
              return user[0] && user[1] && user[2] && user[3]
            })
            let delta = results.data.length - data.length
            if (delta > 0) {
              this.$warning(delta + ' 条记录因存在空字段已被过滤')
            }
            this.uploadUsersCurrentPage = 1
            this.uploadUsers = data
            this.uploadUsersPage = data.slice(0, this.uploadUsersPageSize)
          },
          error: (error) => {
            this.$error(error)
          }
        })
      },
      handleUsersUpload () {
        api.importUsers(this.uploadUsers).then(() => {
          this.getUserList(1)
          this.handleResetData()
        }).catch(() => {
        })
      },
      handleResetData () {
        this.uploadUsers = []
        this.uploadUsersPage = []
        this.uploadUsersCurrentPage = 1
      },
      handleUploadPaginationChange ({ page, pageSize }) {
        this.uploadUsersCurrentPage = page
        this.uploadUsersPageSize = pageSize
        this.uploadUsersPage = this.uploadUsers.slice((page - 1) * pageSize, page * pageSize)
      }
    },
    computed: {
      selectedUserIDs () {
        let ids = []
        for (let user of this.selectedUsers) {
          ids.push(user.id)
        }
        return ids
      }
    },
    watch: {
      'keyword' () {
        this.currentPage = 1
        this.getUserList(1)
      },
      'user.admin_type' () {
        if (this.user.admin_type === 'Admin') {
          this.user.problem_permission = 'All'
        } else if (this.user.admin_type === 'Teacher') {
          this.user.problem_permission = 'Own'
        } else if (this.user.admin_type === 'Regular User') {
          this.user.problem_permission = 'None'
        }
      },
      'uploadUsersCurrentPage' (page) {
        this.uploadUsersPage = this.uploadUsers.slice((page - 1) * this.uploadUsersPageSize, page * this.uploadUsersPageSize)
      },
      'uploadUsersPageSize' () {
        this.uploadUsersCurrentPage = 1
        this.uploadUsersPage = this.uploadUsers.slice(0, this.uploadUsersPageSize)
      }
    }
  }
</script>

<style scoped lang="less">
  .import-help-btn {
    margin-left: 6px;
    font-size: 13px;
    font-weight: 700;
    color: #909399;
    width: 22px;
    height: 22px;
    vertical-align: middle;
  }

  .import-help-content {
    h4 {
      font-size: 14px;
      font-weight: 600;
      color: #303133;
      margin: 16px 0 8px;
      &:first-child { margin-top: 0; }
    }
    p {
      font-size: 13px;
      color: #606266;
      line-height: 1.6;
      margin: 0 0 8px;
    }
    pre {
      background: #f5f7fa;
      border-radius: 4px;
      padding: 12px;
      font-size: 12px;
      line-height: 1.6;
      overflow-x: auto;
    }
    .help-table {
      width: 100%;
      border-collapse: collapse;
      font-size: 13px;
      th, td {
        border: 1px solid #ebeef5;
        padding: 8px 12px;
        text-align: left;
      }
      th {
        background: #f5f7fa;
        font-weight: 600;
        color: #303133;
      }
      td { color: #606266; }
    }
  }

  .userPreview {
    padding-left: 10px;
  }

  .notification {
    p {
      margin: 0;
      text-align: left;
    }
  }
</style>
