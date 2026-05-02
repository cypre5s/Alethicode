<template>
  <div class="admin-shell">
    <SideMenu></SideMenu>

    <div class="admin-shell__main">
      <main class="admin-shell__content">
        <router-view v-slot="{ Component }">
          <transition name="fade-transform" mode="out-in">
            <component :is="Component"></component>
          </transition>
        </router-view>
      </main>

      <footer class="admin-shell__footer">
        <div class="admin-shell__footer-divider"></div>
        <div class="admin-shell__footer-row">
          <span class="admin-shell__footer-title">Alethicode 管理台</span>
          <span class="admin-shell__footer-version">构建版本：{{ version }}</span>
        </div>
      </footer>
    </div>
  </div>
</template>

<script>
  import { types } from '@/store'
  import { FRONTEND_ENV } from '@/utils/runtimeEnv'
  import SideMenu from '../components/SideMenu.vue'
  import api from '../api'

  export default {
    name: 'AdminHome',
    data () {
      return {
        version: FRONTEND_ENV.appVersion
      }
    },
    components: {
      SideMenu
    },
    beforeRouteEnter (to, from, next) {
      api.getProfile().then(res => {
        if (!res.data.data) {
          next({name: 'login'})
        } else {
          next(vm => {
            vm.$store.commit(types.CHANGE_PROFILE, {profile: res.data.data})
          })
        }
      }).catch(() => {
        next({name: 'login'})
      })
    }
  }
</script>

<style lang="less" scoped>
  .admin-shell {
    min-height: 100vh;
    background: var(--admin-shell-bg);
    display: flex;
  }

  .admin-shell__main {
    width: calc(100% - 240px);
    max-width: calc(100% - 240px);
    min-width: 0;
    margin-left: 240px;
    display: flex;
    flex-direction: column;
    min-height: 100vh;
    transition: margin-left 0.3s ease-in-out;
  }

  .admin-shell__content {
    flex: 1;
    padding: 24px 28px 28px;
    overflow-x: hidden;
  }

  .admin-shell__footer {
    padding: 0 28px 28px;
    font-size: 13px;
  }

  .admin-shell__footer-divider {
    height: 1px;
    background: rgba(148, 163, 184, 0.18);
    margin-bottom: 16px;
  }

  .admin-shell__footer-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  .admin-shell__footer-title {
    color: #334155;
    font-weight: 600;
  }

  .admin-shell__footer-version {
    color: #94a3b8;
    font-size: 12px;
  }

  .fade-transform-enter-active,
  .fade-transform-leave-active {
    transition: all 0.28s ease;
  }

  .fade-transform-enter {
    opacity: 0;
    transform: translateY(14px);
  }

  .fade-transform-leave-to {
    opacity: 0;
    transform: translateY(-12px);
  }
</style>
