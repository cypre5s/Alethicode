<template>
  <div id="header">
    <ElMenu mode="horizontal" @select="handleRoute" :default-active="activeMenu" :ellipsis="false" class="oj-menu">
      <div class="logo" @click="handleRoute('/')">
        <img class="logo-ring" src="/logo.png" alt="Alethicode logo">
        <span class="logo-text">{{website.website_name}}</span>
      </div>

      <ElMenuItem index="/problem">
        <ElIcon><Grid /></ElIcon>
        做题
      </ElMenuItem>
      <ElMenuItem index="/learner-notebook" class="nav-notebook">
        <ElIcon><Notebook /></ElIcon>
        错题本
        <span v-if="reviewDueBadge > 0" class="nav-badge">{{ reviewDueBadge > 99 ? '99+' : reviewDueBadge }}</span>
      </ElMenuItem>
      <ElMenuItem index="/language-pack-qa">
        <ElIcon><ChatLineSquare /></ElIcon>
        课件问答
      </ElMenuItem>
      <ElMenuItem index="/classroom">
        <ElIcon><School /></ElIcon>
        班级
      </ElMenuItem>
      <ElSubMenu index="/career">
        <template #title>
          <ElIcon><Compass /></ElIcon>
          专业学习
        </template>
        <ElMenuItem index="/career/profile">专业档案</ElMenuItem>
        <ElMenuItem index="/career/studio">微项目</ElMenuItem>
        <ElMenuItem index="/career/reports">专业报告</ElMenuItem>
      </ElSubMenu>
      <ElMenuItem index="/guide">
        <ElIcon><Reading /></ElIcon>
        新手指南
      </ElMenuItem>

      <div class="nav-right">
        <div v-if="isAdminRole" class="alert-bell-wrap">
          <ElPopover placement="bottom" :width="320" trigger="click" @show="loadAlerts">
            <template #reference>
              <span class="alert-bell">
                <ElIcon :size="18"><Bell /></ElIcon>
                <span v-if="unreadAlertCount > 0" class="alert-badge">{{ unreadAlertCount > 99 ? '99+' : unreadAlertCount }}</span>
              </span>
            </template>
            <div class="alert-popover">
              <div class="alert-popover-head">
                <span class="alert-popover-title">教学警报</span>
                <button v-if="teacherAlerts.length" class="alert-clear-btn" @click="clearAlerts">全部已读</button>
              </div>
              <div v-if="teacherAlerts.length" class="alert-list">
                <div v-for="alert in teacherAlerts.slice(0, 8)" :key="alert.id" class="alert-item">
                  <div class="alert-dot" :class="alert.level === 'high' ? 'is-high' : 'is-warn'"></div>
                  <div class="alert-body">
                    <div class="alert-msg">{{ alert.student_name }} {{ alert.message }}</div>
                    <div class="alert-meta">{{ alert.problem_title || '' }}</div>
                  </div>
                </div>
              </div>
              <div v-else class="alert-empty">暂无警报</div>
            </div>
          </ElPopover>
        </div>
        <ElDropdown class="drop-menu" @command="handleRoute" placement="bottom" trigger="click">
          <span class="drop-menu-title">
            <span class="user-avatar">
              <img v-if="userAvatar" :src="userAvatar" :alt="`${user.username || 'user'} avatar`">
              <span v-else class="user-avatar-fallback">{{ userInitial }}</span>
            </span>
            <span class="user-name">{{ user.username }}</span>
            <ElIcon><ArrowDown /></ElIcon>
          </span>
          <template #dropdown><ElDropdownMenu>
            <ElDropdownItem command="/user-home">{{$t('m.MyHome')}}</ElDropdownItem>
            <ElDropdownItem command="/status?myself=1">{{$t('m.MySubmissions')}}</ElDropdownItem>
            <ElDropdownItem command="/setting/profile">{{$t('m.Settings')}}</ElDropdownItem>
            <ElDropdownItem v-if="isAdminRole" command="/admin">{{$t('m.Management')}}</ElDropdownItem>
            <ElDropdownItem divided command="/logout">{{$t('m.Logout')}}</ElDropdownItem>
          </ElDropdownMenu></template>
        </ElDropdown>
        <div class="announce-icon-wrap">
          <ElPopover placement="bottom-end" :width="360" trigger="click" @show="loadAnnouncements">
            <template #reference>
              <span class="announce-icon">
                <ElIcon :size="18"><Message /></ElIcon>
                <span v-if="hasAnnouncements" class="announce-dot"></span>
              </span>
            </template>
            <div class="announce-popover">
              <div class="announce-popover-head">
                <span class="announce-popover-title">公告</span>
              </div>
              <div v-if="announcements.length" class="announce-list">
                <div v-for="item in announcements.slice(0, 8)" :key="item.id" class="announce-item">
                  <div class="announce-item-title">{{ item.title }}</div>
                  <div class="announce-item-date">{{ formatDate(item.create_time) }}</div>
                </div>
              </div>
              <div v-else class="announce-empty">暂无公告</div>
            </div>
          </ElPopover>
        </div>
        <div class="guide-icon-wrap">
          <ElTooltip content="使用手册" placement="bottom">
            <span class="guide-icon" role="button" tabindex="0" aria-label="使用手册"
                  @click="$router.push('/guide')"
                  @keyup.enter="$router.push('/guide')">
              <ElIcon :size="18"><QuestionFilled /></ElIcon>
            </span>
          </ElTooltip>
        </div>
        <BetaFeedbackButton />
      </div>
    </ElMenu>
    <ElDialog v-model="modalVisible" :width="400" :show-close="true">
      <template #header><div class="modal-title">{{$t('m.Welcome_to')}} {{website.website_name_shortcut}}</div></template>
      <component :is="modalStatus.mode" v-if="modalVisible"></component>
      <template #footer><div style="display: none"></div></template>
    </ElDialog>
  </div>
</template>

<script>
  import { mapGetters, mapActions } from 'vuex'
  import { Grid, ArrowDown, Bell, School, ChatLineSquare, Message, Notebook, Reading, QuestionFilled, Compass } from '@element-plus/icons-vue'
  import login from '@oj/views/user/Login'
  import register from '@oj/views/user/Register'
  import BetaFeedbackButton from '@oj/components/BetaFeedbackButton.vue'
  import api from '@oj/api'

  const REVIEW_DUE_UPDATED_EVENT = 'oj:review-due-updated'

  function normalizeListPayload (payload) {
    if (Array.isArray(payload)) return payload
    if (payload && Array.isArray(payload.results)) return payload.results
    return []
  }

  export default {
    name: 'NavBar',
    components: {
      login,
      register,
      Grid,
      Bell,
      School,
      ArrowDown,
      ChatLineSquare,
      Message,
      Notebook,
      Reading,
      QuestionFilled,
      Compass,
      BetaFeedbackButton
    },
    data () {
      return {
        teacherAlerts: [],
        unreadAlertCount: 0,
        alertPollTimer: null,
        announcements: [],
        announcePollTimer: null,
        defaultLanguagePackId: null,
        reviewDueBadge: 0
      }
    },
    mounted () {
      this.getProfile().then(() => {
        if (this.isAdminRole) {
          this.loadAlerts()
          this.alertPollTimer = setInterval(() => this.loadAlerts(), 60000)
        }
      }).catch(() => {})
      this.loadDefaultLanguagePack()
      this.loadReviewBadge()
      this.loadAnnouncements()
      this.announcePollTimer = setInterval(() => this.loadAnnouncements(), 120000)
      window.addEventListener(REVIEW_DUE_UPDATED_EVENT, this.handleReviewDueUpdated)
    },
    beforeUnmount () {
      if (this.alertPollTimer) clearInterval(this.alertPollTimer)
      if (this.announcePollTimer) clearInterval(this.announcePollTimer)
      window.removeEventListener(REVIEW_DUE_UPDATED_EVENT, this.handleReviewDueUpdated)
    },
    methods: {
      ...mapActions(['getProfile', 'changeModalStatus']),
      handleReviewDueUpdated () {
        this.loadReviewBadge()
      },
      handleRoute (route) {
        if (!route) return

        if (route.indexOf('admin') >= 0) {
          window.open('/admin/')
          return
        }

        const target = this.$router.resolve(route)
        if (!target || !target.fullPath) return
        if (target.fullPath === this.$route.fullPath) return

        this.$router.push(target.fullPath).catch(err => {
          if (err && (err.name === 'NavigationDuplicated' || /redundant navigation/i.test(err.message))) {
            return
          }
          throw err
        })
      },
      handleBtnClick (mode) {
        this.changeModalStatus({
          visible: true,
          mode: mode
        })
      },
      async loadAlerts () {
        try {
          const classroomRes = await api.getClassroomList()
          const classrooms = normalizeListPayload(classroomRes.data && classroomRes.data.data)
          const allAlerts = []
          for (const classroom of classrooms.slice(0, 3)) {
            try {
              const res = await api.getInterventionCandidates(classroom.id, 60)
              const candidates = (res.data && res.data.data && res.data.data.candidates) || []
              for (const c of candidates) {
                allAlerts.push({
                  id: classroom.id + '-' + (c.user_id || '') + '-' + allAlerts.length,
                  student_name: c.username || '学生',
                  message: c.reason || '需要关注',
                  level: c.urgency === 'high' ? 'high' : 'warn',
                  problem_title: c.problem_title || ''
                })
              }
            } catch (e) {
              console.warn('[NavBar] loadTeacherAlerts for classroom failed:', e)
            }
          }
          this.teacherAlerts = allAlerts
          this.unreadAlertCount = allAlerts.length
        } catch (e) {
          console.warn('[NavBar] loadTeacherAlerts failed:', e)
          this.teacherAlerts = []
          this.unreadAlertCount = 0
        }
      },
      clearAlerts () {
        this.teacherAlerts = []
        this.unreadAlertCount = 0
      },
      async loadReviewBadge () {
        try {
          const res = await api.getReviewDue(1)
          const stats = (res.data && res.data.data && res.data.data.stats) || {}
          this.reviewDueBadge = stats.due_count || 0
        } catch (e) {
          console.warn('[NavBar] loadReviewBadge failed:', e)
          this.reviewDueBadge = 0
        }
      },
      async loadDefaultLanguagePack () {
        try {
          const res = await api.getVisibleLanguagePackList()
          const packs = normalizeListPayload(res.data && res.data.data)
          if (packs.length > 0) {
            this.defaultLanguagePackId = packs[0].id
          }
        } catch (e) {
          console.warn('[NavBar] loadDefaultLanguagePack failed:', e)
        }
      },
      async loadAnnouncements () {
        try {
          const res = await api.getAnnouncementList(0, 10)
          const data = res.data.data
          this.announcements = Array.isArray(data) ? data : (data && data.results ? data.results : [])
        } catch (e) {
          console.warn('[NavBar] loadAnnouncements failed:', e)
          this.announcements = []
        }
      },
      formatDate (dateStr) {
        if (!dateStr) return ''
        const d = new Date(dateStr)
        if (isNaN(d.getTime())) return dateStr
        const pad = n => String(n).padStart(2, '0')
        return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
      }
    },
    computed: {
      ...mapGetters(['website', 'modalStatus', 'user', 'profile', 'isAuthenticated', 'isAdminRole']),
      activeMenu () {
        return '/' + this.$route.path.split('/')[1]
      },
      hasAnnouncements () {
        return this.announcements.length > 0
      },
      userAvatar () {
        return this.profile && this.profile.avatar ? this.profile.avatar : ''
      },
      userInitial () {
        const username = this.user && this.user.username ? String(this.user.username).trim() : ''
        return username ? username.charAt(0).toUpperCase() : '?'
      },
      modalVisible: {
        get () {
          return this.modalStatus.visible
        },
        set (value) {
          this.changeModalStatus({visible: value})
        }
      }
    }
  }
</script>

<style lang="less" scoped>
  #header {
    min-width: 300px;
    position: fixed;
    top: 0;
    left: 0;
    height: 64px;
    width: 100%;
    z-index: 1000;
    background-color: var(--bg-card);
    box-shadow: var(--shadow-sm);
    border-bottom: 1px solid var(--border-color);
    transition: all 0.3s ease;

    .oj-menu {
      background: transparent;
      height: 64px;
      border-bottom: none;
      display: flex;
      align-items: center;
      max-width: 1400px;
      margin: 0 auto;
      padding: 0 20px;
    }

    .logo {
      font-size: 15px;
      font-weight: 700;
      color: var(--primary-color);
      margin-right: 24px;
      display: flex;
      align-items: center;
      gap: 8px;
      cursor: pointer;
      text-decoration: none;
      
      .logo-ring {
        width: 32px;
        height: 32px;
        flex-shrink: 0;
        object-fit: contain;
        display: block;
      }
      .logo-text {
        background: none;
        -webkit-background-clip: initial;
        -webkit-text-fill-color: var(--primary-color);
      }
    }

    :deep(.el-menu-item) {
      font-weight: 500;
      color: var(--text-secondary);
      border-bottom: 2px solid transparent;
      transition: color 0.2s;
      font-size: 15px;
      padding: 0 14px;
      height: 64px;
      line-height: 64px;
      
      &:hover, &.is-active {
        color: var(--primary-color);
        border-bottom: 2px solid var(--primary-color);
        background: transparent !important;
      }
      
      .el-icon {
        margin-right: 6px;
        font-size: 16px;
        vertical-align: middle;
      }
    }

    :deep(.el-sub-menu__title) {
      font-weight: 500;
      color: var(--text-secondary);
      font-size: 15px;
      height: 64px;
      line-height: 64px;
      border-bottom: 2px solid transparent;
      transition: color 0.2s;

      &:hover {
        color: var(--primary-color);
        background: transparent !important;
      }

      .el-icon {
        margin-right: 6px;
        font-size: 16px;
        vertical-align: middle;
      }
    }

    :deep(.el-sub-menu.is-active > .el-sub-menu__title) {
      color: var(--primary-color);
      border-bottom-color: var(--primary-color);
    }

    .nav-notebook {
      position: relative;
    }

    .nav-badge {
      position: absolute;
      top: 12px;
      right: 2px;
      min-width: 18px;
      height: 18px;
      border-radius: 9px;
      background: #ef4444;
      color: #fff;
      font-size: 11px;
      font-weight: 700;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      padding: 0 4px;
      line-height: 1;
    }

    .nav-dropdown {
      display: inline-flex;
      align-items: center;
      height: 64px;

      .nav-dropdown-trigger {
        display: inline-flex;
        align-items: center;
        gap: 6px;
        padding: 0 14px;
        height: 64px;
        font-size: 15px;
        font-weight: 500;
        color: var(--text-secondary);
        cursor: pointer;
        border-bottom: 2px solid transparent;
        transition: color 0.2s;

        &:hover, &.is-active {
          color: var(--primary-color);
          border-bottom-color: var(--primary-color);
        }

        .el-icon {
          font-size: 16px;
        }
        .nav-arrow {
          font-size: 12px;
          margin-left: 0;
        }
      }
    }

    .drop-menu {
      display: inline-flex;
      align-items: center;
      flex-shrink: 0;
      
      .drop-menu-title {
        font-size: 15px;
        font-weight: 600;
        color: var(--text-primary);
        padding: 0 12px;
        height: 40px;
        display: inline-flex;
        align-items: center;
        gap: 8px;
        border-radius: var(--border-radius-sm);
        transition: background 0.2s;
        cursor: pointer;
        
        &:hover {
            background: var(--bg-panel);
            color: var(--primary-color);
        }
        
        .el-icon {
            margin-left: 6px;
            font-size: 12px;
        }
      }

      .user-avatar {
        width: 28px;
        height: 28px;
        border-radius: 50%;
        overflow: hidden;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        flex-shrink: 0;
        background: linear-gradient(135deg, var(--primary-color), var(--success-color));
        color: #fff;
        font-size: 13px;
        font-weight: 700;

        img {
          width: 100%;
          height: 100%;
          object-fit: cover;
          display: block;
        }
      }

      .user-avatar-fallback {
        line-height: 1;
      }

      .user-name {
        max-width: 140px;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
    }
    
    .nav-right {
      margin-left: auto;
      display: flex;
      align-items: center;
      flex-shrink: 0;
    }
  }

  .modal {
    &-title {
      font-size: 20px;
      font-weight: 700;
      color: var(--text-primary);
      text-align: center;
      padding-bottom: 10px;
    }
  }

  .alert-bell-wrap {
    display: flex;
    align-items: center;
    margin-right: 8px;
  }

  .alert-bell {
    position: relative;
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    width: 36px;
    height: 36px;
    border-radius: 50%;
    transition: background 0.2s;
    color: var(--text-secondary, #666);

    &:hover {
      background: var(--bg-panel, #f0f2f5);
      color: var(--primary-color, #1a73e8);
    }
  }

  .alert-badge {
    position: absolute;
    top: 2px;
    right: 0;
    min-width: 16px;
    height: 16px;
    border-radius: 8px;
    background: #ef4444;
    color: #fff;
    font-size: 10px;
    font-weight: 700;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 0 4px;
    line-height: 1;
  }

  .alert-popover-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding-bottom: 10px;
    border-bottom: 1px solid var(--border-color);
  }

  .alert-popover-title {
    font-size: 14px;
    font-weight: 700;
    color: var(--text-primary);
  }

  .alert-clear-btn {
    background: none;
    border: none;
    color: var(--primary-color);
    cursor: pointer;
    font-size: 12px;
    font-weight: 500;
  }

  .alert-list {
    max-height: 320px;
    overflow-y: auto;
    padding-top: 8px;
  }

  .alert-item {
    display: flex;
    align-items: flex-start;
    gap: 10px;
    padding: 8px 4px;
    border-bottom: 1px solid #f1f5f9;
  }

  .alert-dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    flex-shrink: 0;
    margin-top: 5px;

    &.is-high {
      background: #ef4444;
    }

    &.is-warn {
      background: #f59e0b;
    }
  }

  .alert-body {
    flex: 1;
    min-width: 0;
  }

  .alert-msg {
    font-size: 13px;
    font-weight: 500;
    color: var(--text-primary);
    line-height: 1.4;
  }

  .alert-meta {
    font-size: 11px;
    color: var(--text-secondary);
    margin-top: 2px;
  }

  .alert-empty {
    text-align: center;
    padding: 24px 0;
    color: var(--text-secondary);
    font-size: 13px;
  }

  .announce-icon-wrap {
    display: flex;
    align-items: center;
    margin-left: 8px;
  }

  .announce-icon {
    position: relative;
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    width: 36px;
    height: 36px;
    border-radius: 50%;
    transition: background 0.2s;
    color: var(--text-secondary);

    &:hover {
      background: var(--bg-panel);
      color: var(--primary-color);
    }
  }

  .announce-dot {
    position: absolute;
    top: 5px;
    right: 5px;
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: #ef4444;
    border: 1.5px solid var(--bg-card);
  }

  .announce-popover-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding-bottom: 10px;
    border-bottom: 1px solid var(--border-color);
  }

  .announce-popover-title {
    font-size: 14px;
    font-weight: 700;
    color: var(--text-primary);
  }

  .announce-list {
    max-height: 360px;
    overflow-y: auto;
    padding-top: 4px;
  }

  .announce-item {
    padding: 10px 4px;
    border-bottom: 1px solid #f1f5f9;
    cursor: default;

    &:last-child {
      border-bottom: none;
    }
  }

  .announce-item-title {
    font-size: 13px;
    font-weight: 600;
    color: var(--text-primary);
    line-height: 1.5;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .announce-item-date {
    font-size: 11px;
    color: var(--text-secondary);
    margin-top: 3px;
  }

  .announce-empty {
    text-align: center;
    padding: 24px 0;
    color: var(--text-secondary);
    font-size: 13px;
  }

  .guide-icon-wrap {
    display: flex;
    align-items: center;
    margin-left: 8px;
  }

  .guide-icon {
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    width: 36px;
    height: 36px;
    border-radius: 50%;
    transition: background 0.2s, color 0.2s, transform 0.2s;
    color: var(--text-secondary);

    &:hover, &:focus-visible {
      background: var(--bg-panel);
      color: var(--primary-color);
      transform: rotate(-6deg);
      outline: none;
    }
  }
</style>
