<template>
  <div>
    <NavBar v-if="showNavBar"></NavBar>
    <div :class="['content-app', {'content-auth': isAuthPage || isFullscreenPage}]">
      <router-view v-slot="{ Component }">
        <transition name="fadeInUp" mode="out-in">
          <component :is="Component"></component>
        </transition>
      </router-view>
    </div>
    <ElBacktop v-if="showNavBar"></ElBacktop>
    <BetaPrivacyNotice v-if="isAuthenticated" />
  </div>
</template>

<script>
  import { mapActions, mapGetters, mapState } from 'vuex'
  import NavBar from '@oj/components/NavBar.vue'
  import BetaPrivacyNotice from '@oj/components/BetaPrivacyNotice.vue'
  import { FRONTEND_ENV } from '@/utils/runtimeEnv'

  export default {
    name: 'App',
    components: {
      NavBar,
      BetaPrivacyNotice
    },
    data () {
      return {
        version: FRONTEND_ENV.appVersion
      }
    },
    created () {
      try {
        document.body.removeChild(document.getElementById('app-loader'))
      } catch (e) {
      }
    },
    mounted () {
      this.getWebsiteConfig()
      document.addEventListener('contextmenu', this._blockImageContextMenu)
    },
    beforeUnmount () {
      document.removeEventListener('contextmenu', this._blockImageContextMenu)
    },
    methods: {
      ...mapActions(['getWebsiteConfig', 'changeDomTitle']),
      _blockImageContextMenu (e) {
        if (e.target && e.target.tagName === 'IMG') {
          e.preventDefault()
        }
      }
    },
    computed: {
      ...mapState(['website']),
      ...mapGetters(['isAuthenticated']),
      isAuthPage () {
        return this.$route.name === 'login' || this.$route.name === 'register'
      },
      isFullscreenPage () {
        return this.$route.name === 'pdf-viewer'
      },
      showNavBar () {
        return this.isAuthenticated && !this.isAuthPage && !this.isFullscreenPage
      }
    },
    watch: {
      'website' () {
        this.changeDomTitle({ title: this.$route.meta && this.$route.meta.title ? this.$route.meta.title : '' })
      },
      '$route' () {
        this.changeDomTitle({ title: this.$route.meta && this.$route.meta.title ? this.$route.meta.title : '' })
      }
    }
  }
</script>

<style lang="less">

  * {
    -webkit-box-sizing: border-box;
    -moz-box-sizing: border-box;
    box-sizing: border-box;
  }

  a {
    text-decoration: none;
    background-color: transparent;
    &:active, &:hover {
      outline-width: 0;
    }
  }


@media screen and (max-width: 1200px) {
  .content-app {
    --oj-content-top-offset: 160px;
    margin-top: 160px;
    padding: 0 2%;
    min-height: calc(100vh - 160px);
    min-height: calc(100dvh - 160px);
    min-width: 0;
    max-width: 100%;
  }
}

@media screen and (min-width: 1200px) {
  .content-app {
    --oj-content-top-offset: 64px;
    margin-top: 64px;
    padding: 0 2%;
    min-height: calc(100vh - 64px);
    min-height: calc(100dvh - 64px);
    min-width: 0;
    max-width: 100%;
  }
}

  .content-app {
    width: 100%;
    min-width: 0;
    max-width: 100%;
  }

  .content-auth {
    --oj-content-top-offset: 0px;
    margin-top: 0 !important;
    padding: 0 !important;
    min-height: auto !important;
  }

  .fadeInUp-enter-active {
    animation: fadeInUp .25s ease-out;
  }


</style>
