<template>
  <div class="view">
    <panel>
      <template #title>
        <span>{{$t('m.Test_Case_Prune_Test_Case')}}
        <el-popover placement="right" trigger="hover">
          这些测试数据当前没有被任何题目引用，可以安全清理。
          <template #reference><i class="el-icon-fa-question-circle import-user-icon"></i></template>
        </el-popover>
      </span>
      </template>
      <el-table :data="pagedRows">
        <el-table-column
          label="最后修改时间"
          align="center">
          <template #default="{row}">
            {{ formatTimestamp(row.create_time) }}
          </template>
        </el-table-column>
        <el-table-column
          prop="id"
          label="测试数据 ID"
          align="center">
        </el-table-column>
        <el-table-column
          label="操作"
          fixed="right"
          width="200"
          align="center">
          <template #default="{row}">
            <icon-btn name="删除测试数据" icon="trash" @click="deleteTestCase(row.id)"></icon-btn>
          </template>
        </el-table-column>
      </el-table>
      <div class="panel-options" v-show="data.length > 0">
        <el-button type="warning" size="small"
                   :loading="loading"
                   icon="el-icon-fa-trash"
                   @click="deleteTestCase()">全部清理
        </el-button>
        <AdminPagination
          :total="data.length"
          :current-page="page"
          :page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          @update:currentPage="page = $event"
          @update:pageSize="pageSize = $event"
          @change="handlePaginationChange">
        </AdminPagination>
      </div>
    </panel>
  </div>
</template>

<script>
  import api from '@admin/api'
  import moment from 'moment'

  export default {
    name: 'PruneTestCase',
    data () {
      return {
        data: [],
        loading: false,
        page: 1,
        pageSize: 10
      }
    },
    computed: {
      pagedRows () {
        const start = (this.page - 1) * this.pageSize
        return this.data.slice(start, start + this.pageSize)
      }
    },
    mounted () {
      this.init()
    },
    methods: {
      formatTimestamp (value) {
        return moment(value).format('YYYY-MM-DD HH:mm:ss')
      },
      init () {
        api.getInvalidTestCaseList().then(resp => {
          this.data = resp.data.data
          if ((this.page - 1) * this.pageSize >= this.data.length) {
            this.page = 1
          }
        }, () => {
        })
      },
      deleteTestCase (id) {
        if (!id) {
          this.loading = true
        }
        api.pruneTestCase(id).then(resp => {
          this.loading = false
          this.init()
        })
      },
      handlePaginationChange ({ page, pageSize }) {
        this.page = page
        this.pageSize = pageSize
      }
    }
  }
</script>

<style>

</style>
