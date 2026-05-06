<template>
  <div class="settings-page-wrap">
    <div class="settings-grid">
      <aside class="sidebar">
        <div class="sidebar-top">
          <div class="avatar-preview" @click="goRoute('/setting/profile')" title="点击更换头像" @mousedown="ripple">
            <img v-if="profile.avatar" :src="profile.avatar" class="avatar-img" />
            <span v-else class="avatar-letter">{{ avatarLetter }}</span>
            <div class="avatar-overlay">
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <path d="M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z"/>
                <circle cx="12" cy="13" r="4"/>
              </svg>
            </div>
          </div>
          <div class="sidebar-username">{{ profile.user ? profile.user.username : '' }}</div>
          <div class="sidebar-role">{{ roleLabel }}</div>
        </div>

        <nav class="sidebar-nav">
          <div
            v-for="(item, idx) in navItems"
            :key="idx"
            class="sidebar-nav-item"
            :class="{ active: activeName === item.route }"
            @click="handleNavClick(item, $event)"
            @mousedown="ripple"
          >
            <div class="nav-icon-box">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" v-html="item.icon"></svg>
            </div>
            {{ item.label }}
          </div>
        </nav>
      </aside>

      <div class="main-content">
        <router-view v-slot="{ Component }">
          <transition name="st-fade">
            <component :is="Component"></component>
          </transition>
        </router-view>
      </div>
    </div>

    <div id="settings-toasts"></div>
  </div>
</template>

<script>
import { mapGetters } from 'vuex'

export default {
  name: 'Settings',
  data () {
    return {
      navItems: [
        {
          label: '个人信息设置',
          route: '/setting/profile',
          icon: '<path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/>'
        },
        {
          label: '账号设置',
          route: '/setting/account',
          icon: '<rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/>'
        },
        {
          label: 'Career 模块设置',
          route: '/setting/career',
          icon: '<circle cx="12" cy="12" r="10"/><line x1="2" y1="12" x2="22" y2="12"/><path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"/>'
        }
      ]
    }
  },
  computed: {
    ...mapGetters(['profile']),
    activeName () {
      return this.$route.path
    },
    avatarLetter () {
      const u = this.profile.user
      if (u && u.username) return u.username.charAt(0).toUpperCase()
      return '?'
    },
    roleLabel () {
      const profile = this.profile || {}
      const user = profile.user || {}
      const adminType = user.admin_type || profile.admin_type || ''
      if (adminType === 'Admin') return '管理员'
      if (adminType === 'Teacher') return '教师'
      const roleMap = { Student: '学生', Teacher: '教师', Admin: '管理员' }
      return roleMap[profile.role] || '学生'
    }
  },
  mounted () {
    document.documentElement.classList.add('settings-no-scrollbar')
  },
  beforeUnmount () {
    document.documentElement.classList.remove('settings-no-scrollbar')
  },
  methods: {
    goRoute (routePath) {
      if (routePath && this.$route.path !== routePath) {
        this.$router.push(routePath)
      }
    },
    handleNavClick (item) {
      if (item.route) {
        this.goRoute(item.route)
      } else {
        this.$settingsToast('「' + item.label + '」功能即将上线', 'warn')
      }
    },
    ripple (e) {
      const el = e.currentTarget
      const r = el.getBoundingClientRect()
      const size = Math.max(r.width, r.height) * 2
      const rp = document.createElement('span')
      rp.className = 'st-ripple'
      Object.assign(rp.style, {
        width: size + 'px',
        height: size + 'px',
        left: (e.clientX - r.left - size / 2) + 'px',
        top: (e.clientY - r.top - size / 2) + 'px'
      })
      el.appendChild(rp)
      rp.addEventListener('animationend', () => rp.remove())
    }
  }
}
</script>

<style lang="less">
:root {
  --st-bg: #f4f6fb;
  --st-bg-card: #ffffff;
  --st-border: #e8eaed;
  --st-border-s: #f3f4f6;
  --st-text: #1a1d2e;
  --st-text-2: #5f6368;
  --st-text-3: #9aa0ab;
  --st-blue: #1a73e8;
  --st-blue-l: #f0f6ff;
  --st-blue-b: #d2e3fc;
  --st-green: #34a853;
  --st-red: #ea4335;
  --st-shadow-sm: 0 1px 3px rgba(0,0,0,.05);
  --st-shadow-md: 0 4px 16px rgba(0,0,0,.08);
  --st-radius: 12px;
  --st-radius-s: 8px;
}

html.settings-no-scrollbar,
html.settings-no-scrollbar body {
  overflow: hidden;
  height: 100%;
}

/* Toast */
#settings-toasts {
  position: fixed;
  bottom: 28px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 9999;
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: center;
  pointer-events: none;
}

.settings-toast {
  background: #1a1d2e;
  color: #fff;
  padding: 10px 18px;
  border-radius: 10px;
  font-size: 12px;
  font-weight: 500;
  box-shadow: 0 6px 20px rgba(0,0,0,.2);
  display: flex;
  align-items: center;
  gap: 8px;
  animation: st-toast-in .3s ease;
}
.settings-toast.out { animation: st-toast-out .3s ease forwards; }
@keyframes st-toast-in {
  from { opacity: 0; transform: translateY(12px); }
  to   { opacity: 1; transform: none; }
}
@keyframes st-toast-out {
  to { opacity: 0; transform: translateY(-8px); }
}
.settings-toast-dot {
  width: 6px; height: 6px; border-radius: 50%;
  background: var(--st-green); flex-shrink: 0;
}
.settings-toast-dot.warn { background: #fbbc04; }

/* Ripple */
.st-ripple {
  position: absolute;
  border-radius: 50%;
  background: rgba(0,0,0,.07);
  transform: scale(0);
  animation: st-rip .5s linear;
  pointer-events: none;
}
@keyframes st-rip { to { transform: scale(4); opacity: 0; } }

/* AccountSetting / SecuritySetting 子路由遗留样式 */
.setting-main {
  position: relative;
  margin: 10px 20px;
  padding-bottom: 20px;
  .setting-content {
    margin-left: 0;
  }
  .mini-container {
    width: 500px;
  }
  .section-title {
    font-size: 14px;
    font-weight: 600;
    color: var(--st-text);
    margin-bottom: 16px;
    padding-bottom: 10px;
    border-bottom: 1px solid var(--st-border-s);
  }
  .flex-container {
    display: flex;
    flex-wrap: wrap;
    gap: 20px;
  }
}
</style>

<style lang="less" scoped>
.settings-page-wrap {
  max-width: 960px;
  margin: 0 auto;
  padding: 28px 16px 60px;
  height: calc(100vh - 80px);
  overflow-y: auto;
  scrollbar-width: none;
  -ms-overflow-style: none;
  &::-webkit-scrollbar {
    display: none;
  }
}

@media screen and (max-width: 1200px) {
  .settings-page-wrap {
    height: calc(100vh - 160px);
  }
}

.settings-grid {
  display: grid;
  grid-template-columns: 220px 1fr;
  gap: 24px;
  align-items: start;
}

/* ---- Sidebar ---- */
.sidebar {
  background: var(--st-bg-card);
  border: 1px solid var(--st-border);
  border-radius: var(--st-radius);
  box-shadow: var(--st-shadow-sm);
  overflow: hidden;
  animation: st-fadeUp .35s ease both;
}

@keyframes st-fadeUp {
  from { opacity: 0; transform: translateY(10px); }
  to   { opacity: 1; transform: none; }
}

.sidebar-top {
  padding: 28px 20px 20px;
  display: flex; flex-direction: column; align-items: center; gap: 12px;
  border-bottom: 1px solid var(--st-border-s);
}

.avatar-preview {
  width: 72px; height: 72px; border-radius: 50%;
  background: linear-gradient(135deg, #4f7cff, #a78bfa);
  display: flex; align-items: center; justify-content: center;
  color: #fff; font-size: 26px; font-weight: 700;
  box-shadow: 0 0 0 3px #fff, 0 0 0 5px #e8f0fe;
  cursor: pointer; transition: transform .2s, box-shadow .2s;
  position: relative; overflow: hidden; user-select: none;
}
.avatar-preview:hover {
  transform: scale(1.06);
  box-shadow: 0 0 0 3px #fff, 0 0 0 6px rgba(79,124,255,.4);
}
.avatar-img { width: 100%; height: 100%; object-fit: cover; border-radius: 50%; }
.avatar-overlay {
  position: absolute; inset: 0; border-radius: 50%;
  background: rgba(0,0,0,.45);
  display: flex; align-items: center; justify-content: center;
  opacity: 0; transition: opacity .2s;
}
.avatar-overlay svg { color: #fff; }
.avatar-preview:hover .avatar-overlay { opacity: 1; }

.sidebar-username { font-size: 15px; font-weight: 700; color: var(--st-text); }
.sidebar-role {
  font-size: 11px; padding: 2px 10px; border-radius: 10px;
  background: var(--st-blue-l); color: var(--st-blue);
  border: 1px solid var(--st-blue-b); font-weight: 500;
}

/* ---- Sidebar Nav ---- */
.sidebar-nav { padding: 8px 0 12px; }
.sidebar-nav-item {
  display: flex; align-items: center; gap: 10px;
  padding: 10px 16px; cursor: pointer; transition: all .15s;
  font-size: 13px; color: var(--st-text-2); position: relative; overflow: hidden;
}
.sidebar-nav-item::before {
  content: ''; position: absolute; left: 0; top: 8px; bottom: 8px;
  width: 3px; border-radius: 0 2px 2px 0; background: var(--st-blue);
  transform: scaleY(0); transition: transform .2s;
}
.sidebar-nav-item.active {
  color: var(--st-blue); font-weight: 500; background: var(--st-blue-l);
}
.sidebar-nav-item.active::before { transform: scaleY(1); }
.sidebar-nav-item:not(.active):hover { background: #f8f9fb; color: var(--st-text); }

.nav-icon-box {
  width: 28px; height: 28px; border-radius: var(--st-radius-s);
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0; transition: background .15s; color: var(--st-text-3);
}
.sidebar-nav-item.active .nav-icon-box {
  background: rgba(26,115,232,.12); color: var(--st-blue);
}
.sidebar-nav-item:not(.active):hover .nav-icon-box {
  background: #f0f2f5; color: var(--st-text-2);
}

/* ---- Main Content ---- */
.main-content {
  display: flex; flex-direction: column; gap: 18px; min-width: 0;
}

/* Transition */
.st-fade-enter-active { animation: st-fadeUp .4s ease both; }
.st-fade-leave-active { animation: st-fadeUp .2s ease reverse both; }
</style>
