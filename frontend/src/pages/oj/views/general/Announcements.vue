<template>
  <OjPanel shadow :padding="10">
    <template #title><div>
      {{title}}
    </div></template>
    <template #extra><div>
      <ElButton v-if="listVisible" type="info" @click="init" :loading="btnLoading">{{$t('m.Refresh')}}</ElButton>
      <ElButton v-else plain @click="goBack"><ElIcon><RefreshLeft /></ElIcon>{{$t('m.Back')}}</ElButton>
    </div></template>

    <transition-group name="announcement-animate">
      <div class="no-announcement" v-if="!announcements.length" key="no-announcement">
        <p>{{$t('m.No_Announcements')}}</p>
      </div>
      <template v-if="listVisible">
        <ul class="announcements-container" key="list">
          <li v-for="announcement in announcements" :key="announcement.title">
            <div class="flex-container">
              <div class="title"><a class="entry" @click="goAnnouncement(announcement)">
                {{announcement.title}}</a></div>
              <div class="date">{{ localtime(announcement.create_time) }}</div>
              <div class="creator"> {{$t('m.By')}} {{announcement.created_by.username}}</div>
            </div>
          </li>
        </ul>
        <Pagination key="page"
                    :total="total"
                    :page-size="limit"
                    @change="onAnnouncementPageChange">
        </Pagination>
      </template>

      <template v-else>
        <div v-katex v-html="sanitize(announcement.content)" key="content" class="content-container markdown-body"></div>
      </template>
    </transition-group>
  </OjPanel>
</template>

<script>
  import api from '@oj/api'
  import { sanitize } from '@/utils/sanitize'
  import { utcToLocal } from '@/utils/time'
  import Pagination from '@/components/Pagination.vue'
  import { RefreshLeft } from '@element-plus/icons-vue'

  export default {
    name: 'Announcements',
    components: {
      Pagination,
      RefreshLeft
    },
    data () {
      return {
        limit: 10,
        total: 10,
        btnLoading: false,
        announcements: [],
        announcement: '',
        listVisible: true
      }
    },
    mounted () {
      this.init()
    },
    methods: {
      sanitize,
      localtime: utcToLocal,
      init () {
        this.getAnnouncementList()
      },
      getAnnouncementList (page = 1) {
        this.btnLoading = true
        api.getAnnouncementList((page - 1) * this.limit, this.limit).then(res => {
          this.btnLoading = false
          this.announcements = res.data.data.results
          this.total = res.data.data.total
        }, () => {
          this.btnLoading = false
        })
      },
      onAnnouncementPageChange (payload) {
        const page = payload && typeof payload.page === 'number' ? payload.page : 1
        this.getAnnouncementList(page)
      },
      goAnnouncement (announcement) {
        this.announcement = announcement
        this.listVisible = false
      },
      goBack () {
        this.listVisible = true
        this.announcement = ''
      }
    },
    computed: {
      title () {
        if (this.listVisible) {
          return this.$t('m.Announcements')
        } else {
          return this.announcement.title
        }
      }
    }
  }
</script>

<style scoped lang="less">
  .announcements-container {
    margin-top: -10px;
    margin-bottom: 10px;
    li {
      padding-top: 15px;
      list-style: none;
      padding-bottom: 15px;
      margin-left: 20px;
      font-size: 16px;
      border-bottom: 1px solid var(--border-color);
      
      &:last-child {
        border-bottom: none;
      }
      
      .flex-container {
        display: flex;
        align-items: center;
        
        .title {
          flex: 1 1;
          text-align: left;
          padding-left: 10px;
          
          a.entry {
            color: var(--text-primary);
            text-decoration: none;
            transition: color 0.2s;
            font-weight: 500;
            
            &:hover {
              color: var(--primary-color);
            }
          }
        }
        
        .creator {
          flex: none;
          width: 200px;
          text-align: center;
          color: var(--text-secondary);
          font-size: 14px;
        }
        
        .date {
          flex: none;
          width: 200px;
          text-align: center;
          color: var(--text-secondary);
          font-size: 14px;
          font-family: var(--font-mono);
        }
      }
    }
  }

  .content-container {
    padding: 0 20px 20px 20px;
  }

  .no-announcement {
    text-align: center;
    font-size: 16px;
    padding: 20px;
    color: var(--text-secondary);
  }

  .announcement-animate-enter-active {
    animation: fadeIn 1s;
  }
</style>
