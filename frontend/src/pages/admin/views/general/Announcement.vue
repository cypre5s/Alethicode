<template>
  <div class="announcement view">
    <Panel :title="$t('m.General_Announcement')">
      <div class="list">
        <el-table
          v-loading="loading"
          element-loading-text="加载中"
          ref="table"
          :header-cell-style="{ textAlign: 'center' }"
          :data="announcementList"
          style="width: 100%">
          <el-table-column
            width="100"
            prop="id"
            label="ID">
          </el-table-column>
          <el-table-column
            prop="title"
            label="标题">
          </el-table-column>
          <el-table-column
            prop="create_time"
            label="创建时间">
            <template #default="scope">
              {{ localtime(scope.row.create_time) }}
            </template>
          </el-table-column>
          <el-table-column
            prop="last_update_time"
            label="最后更新时间">
            <template #default="scope">
              {{ localtime(scope.row.last_update_time) }}
            </template>
          </el-table-column>
          <el-table-column
            prop="created_by.username"
            label="作者">
          </el-table-column>
          <el-table-column
            width="100"
            prop="visible"
            label="可见">
            <template #default="scope">
              <el-switch v-model="scope.row.visible"
                         active-text=""
                         inactive-text=""
                         @change="handleVisibleSwitch(scope.row)">
              </el-switch>
            </template>
          </el-table-column>
        <el-table-column
            fixed="right"
            label="操作"
            width="200">
            <template #default="scope">
              <icon-btn name="编辑公告" icon="edit" @click="openAnnouncementDialog(scope.row.id)"></icon-btn>
              <icon-btn name="删除公告" icon="trash" @click="deleteAnnouncement(scope.row.id)"></icon-btn>
            </template>
          </el-table-column>
        </el-table>
        <div class="panel-options">
          <el-button type="primary" size="small" @click="openAnnouncementDialog(null)" icon="el-icon-plus">新建公告</el-button>
          <AdminPagination
            :total="total"
            :current-page="currentPage"
            :page-size="pageSize"
            :page-sizes="[10, 15, 20, 50]"
            @update:currentPage="currentPage = $event"
            @update:pageSize="pageSize = $event"
            @change="handlePaginationChange">
          </AdminPagination>
        </div>
      </div>
    </Panel>
    <el-dialog :title="announcementDialogTitle" v-model="showEditAnnouncementDialog"
               @open="onOpenEditDialog" :close-on-click-modal="false">
      <el-form label-position="top">
        <el-form-item :label="$t('m.Announcement_Title')" required>
          <el-input
            v-model="announcement.title"
            :placeholder="$t('m.Announcement_Title')" class="title-input">
          </el-input>
        </el-form-item>
        <el-form-item :label="$t('m.Announcement_Content')" required>
          <Simditor v-model="announcement.content"></Simditor>
        </el-form-item>
        <div class="visible-box">
          <span>{{$t('m.Announcement_visible')}}</span>
          <el-switch
            v-model="announcement.visible"
            active-text=""
            inactive-text="">
          </el-switch>
        </div>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <cancel @click="showEditAnnouncementDialog = false"></cancel>
          <save type="primary" @click="submitAnnouncement"></save>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script>
  import Simditor from '../../components/Simditor.vue'
  import api from '../../api.js'
  import { utcToLocal } from '@/utils/time'

  export default {
    name: 'Announcement',
    components: {
      Simditor
    },
    data () {
      return {
        showEditAnnouncementDialog: false,
        announcementList: [],
        pageSize: 15,
        total: 0,
        currentAnnouncementId: null,
        mode: 'create',
        announcement: {
          title: '',
          visible: true,
          content: ''
        },
        announcementDialogTitle: '编辑公告',
        loading: true,
        currentPage: 1
      }
    },
    mounted () {
      this.init()
    },
    methods: {
      localtime: utcToLocal,
      init () {
        this.getAnnouncementList(1)
      },
      handlePaginationChange ({ page, pageSize }) {
        this.pageSize = pageSize
        this.currentPage = page
        this.getAnnouncementList(page)
      },
      getAnnouncementList (page) {
        this.loading = true
        api.getAnnouncementList((page - 1) * this.pageSize, this.pageSize).then(res => {
          this.loading = false
          this.total = res.data.data.total
          this.announcementList = res.data.data.results
        }, res => {
          this.loading = false
        })
      },
      onOpenEditDialog () {
        this.$nextTick(() => {
          window.dispatchEvent(new Event('resize'))
        })
      },
      submitAnnouncement (data = undefined) {
        if (!data.title) {
          data = {
            id: this.currentAnnouncementId,
            title: this.announcement.title,
            content: this.announcement.content,
            visible: this.announcement.visible
          }
        }
        const funcName = this.mode === 'edit' ? 'updateAnnouncement' : 'createAnnouncement'
        api[funcName](data).then(res => {
          this.showEditAnnouncementDialog = false
          this.init()
        }).catch(err => { console.error('announcement save error:', err) })
      },
      deleteAnnouncement (announcementId) {
        this.$confirm('确认删除这条公告？', '删除公告', {
          confirmButtonText: '删除',
          cancelButtonText: '取消',
          type: 'warning'
        }).then(() => {
          this.loading = true
          api.deleteAnnouncement(announcementId).then(res => {
            this.loading = true
            this.init()
          })
        }).catch(() => {
          this.loading = false
        })
      },
      openAnnouncementDialog (id) {
        this.showEditAnnouncementDialog = true
        if (id !== null) {
          this.currentAnnouncementId = id
          this.announcementDialogTitle = '编辑公告'
          this.announcementList.find(item => {
            if (item.id === this.currentAnnouncementId) {
              this.announcement.title = item.title
              this.announcement.visible = item.visible
              this.announcement.content = item.content
              this.mode = 'edit'
            }
          })
        } else {
          this.announcementDialogTitle = '新建公告'
          this.announcement.title = ''
          this.announcement.visible = true
          this.announcement.content = ''
          this.mode = 'create'
        }
      },
      handleVisibleSwitch (row) {
        this.mode = 'edit'
        this.submitAnnouncement({
          id: row.id,
          title: row.title,
          content: row.content,
          visible: row.visible
        })
      }
    },
    watch: {
      $route () {
        this.init()
      }
    }
  }
</script>

<style lang="less" scoped>
  .title-input {
    margin-bottom: 20px;
  }

  .visible-box {
    margin-top: 10px;
    width: 205px;
    float: left;
  }
</style>
