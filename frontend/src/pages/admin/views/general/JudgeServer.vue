<template>
  <div class="view">
    <Panel :title="$t('m.Judge_Server_Token')">
      <code>{{ token }}</code>
    </Panel>
    <Panel :title="$t('m.Judge_Server_Info')">
      <el-table
        :data="pagedServers"
        :default-expand-all="true"
        border>
        <el-table-column
          type="expand">
          <template #default="props">
            <p>{{$t('m.IP')}}:
              <el-tag type="success">{{ props.row.ip }}</el-tag>&nbsp;&nbsp;
              {{$t('m.Judger_Version')}}:
              <el-tag type="success">{{ props.row.judger_version }}</el-tag>
            </p>
            <p>
              {{$t('m.Service_URL')}}:
              <a :href="props.row.service_url" target="_blank" rel="noopener noreferrer">
                <code>{{ props.row.service_url }}</code>
              </a>
            </p>
            <p>{{$t('m.Last_Heartbeat')}}: {{ localtime(props.row.last_heartbeat) }}</p>
            <p>{{$t('m.Create_Time')}}: {{ localtime(props.row.create_time) }}</p>
          </template>
        </el-table-column>
        <el-table-column
          prop="status"
          label="Status">
          <template #default="scope">
            <el-tag
              :type="scope.row.status === 'normal' ? 'success' : 'danger'">
              {{ scope.row.status === 'normal' ? 'Normal' : 'Abnormal' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          prop="hostname"
          label="Hostname">
        </el-table-column>
        <el-table-column
          prop="task_number"
          label="Task Number">
        </el-table-column>
        <el-table-column
          prop="cpu_core"
          label="CPU Core">
        </el-table-column>
        <el-table-column
          prop="cpu_usage"
          label="CPU Usage">
          <template #default="scope">{{ scope.row.cpu_usage }}%</template>
        </el-table-column>
        <el-table-column
          prop="memory_usage"
          label="Memory Usage">
          <template #default="scope">{{ scope.row.memory_usage }}%</template>
        </el-table-column>
        <el-table-column label="Disabled">
          <template #default="{ row }">
            <el-switch v-model="row.is_disabled" @change="handleDisabledSwitch(row.id, row.is_disabled)"></el-switch>
          </template>
        </el-table-column>
        <el-table-column
          fixed="right"
          label="Options">
          <template #default="scope">
            <icon-btn name="Delete" icon="trash" @click="deleteJudgeServer(scope.row.hostname)"></icon-btn>
          </template>
        </el-table-column>
      </el-table>
      <div class="panel-options" v-if="servers.length > 0">
        <AdminPagination
          :total="servers.length"
          :current-page="page"
          :page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          @update:currentPage="page = $event"
          @update:pageSize="pageSize = $event"
          @change="handlePaginationChange">
        </AdminPagination>
      </div>
    </Panel>
  </div>
</template>

<script>
  import api from '../../api.js'
  import { utcToLocal } from '@/utils/time'

  export default {
    name: 'JudgeServer',
    data () {
      return {
        servers: [],
        token: '',
        intervalId: -1,
        page: 1,
        pageSize: 10
      }
    },
    computed: {
      pagedServers () {
        const start = (this.page - 1) * this.pageSize
        return this.servers.slice(start, start + this.pageSize)
      }
    },
    mounted () {
      this.refreshJudgeServerList()
      this.intervalId = setInterval(() => {
        this.refreshJudgeServerList()
      }, 5000)
    },
    methods: {
      localtime: utcToLocal,
      refreshJudgeServerList () {
        api.getJudgeServer().then(res => {
          this.servers = res.data.data.servers
          this.token = res.data.data.token
          if ((this.page - 1) * this.pageSize >= this.servers.length) {
            this.page = 1
          }
        })
      },
      deleteJudgeServer (hostname) {
        this.$confirm('If you delete this judge server, it can\'t be used until next heartbeat', 'Warning', {
          confirmButtonText: 'Delete',
          cancelButtonText: 'Cancel',
          type: 'warning'
        }).then(() => {
          api.deleteJudgeServer(hostname).then(res =>
            this.refreshJudgeServerList()
          )
        }).catch(() => {
        })
      },
      handleDisabledSwitch (id, value) {
        let data = {
          id,
          is_disabled: value
        }
        api.updateJudgeServer(data).catch(() => {})
      },
      handlePaginationChange ({ page, pageSize }) {
        this.page = page
        this.pageSize = pageSize
      }
    },
    beforeRouteLeave (to, from, next) {
      clearInterval(this.intervalId)
      next()
    }
  }
</script>
