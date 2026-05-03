<template>
  <div class="td-page">
    <TwinHero />

    <div class="td-row td-row--main">
      <div class="td-col td-col--timeline">
        <h2 class="td-section-title">学习时间线</h2>
        <LearningTimeline />
      </div>
      <div class="td-col td-col--health">
        <h2 class="td-section-title">学习健康度</h2>
        <LearningHealthCard />
      </div>
    </div>

    <div class="td-row td-row--galaxy">
      <h2 class="td-section-title">知识星系</h2>
      <KcGalaxyView />
    </div>

    <div class="td-row td-row--museum">
      <ErrorMuseumView />
    </div>

    <transition name="td-fab-fade">
      <button
        v-if="showBackToTop"
        type="button"
        class="td-back-to-top"
        aria-label="返回顶部"
        @click="scrollToTop"
      >
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><polyline points="18 15 12 9 6 15"/></svg>
      </button>
    </transition>
  </div>
</template>

<script>
import TwinHero from './TwinHero.vue'
import LearningTimeline from './LearningTimeline.vue'
import LearningHealthCard from './LearningHealthCard.vue'
import KcGalaxyView from './KcGalaxyView.vue'
import ErrorMuseumView from './ErrorMuseumView.vue'

export default {
  name: 'TwinDashboardPage',
  components: { TwinHero, LearningTimeline, LearningHealthCard, KcGalaxyView, ErrorMuseumView },
  data () {
    return { showBackToTop: false }
  },
  mounted () {
    window.addEventListener('scroll', this.handleScroll)
  },
  beforeUnmount () {
    window.removeEventListener('scroll', this.handleScroll)
  },
  methods: {
    handleScroll () {
      this.showBackToTop = window.scrollY > 600
    },
    scrollToTop () {
      window.scrollTo({ top: 0, behavior: 'smooth' })
    }
  }
}
</script>

<style lang="less" scoped>
@import '~@/styles/l99-tokens.less';

.td-page {
  max-width: 1200px;
  margin: 0 auto;
  padding: @l99-sp-6;
}

.td-section-title {
  font-size: @l99-fs-lg;
  font-weight: 600;
  color: @l99-neutral-900;
  margin: 0 0 @l99-sp-4;
}

.td-row {
  margin-bottom: @l99-sp-8;

  &--main {
    display: flex;
    gap: @l99-sp-6;
  }
}

.td-col {
  &--timeline { flex: 7; min-width: 0; }
  &--health { flex: 3; min-width: 280px; }
}

.td-back-to-top {
  position: fixed;
  bottom: @l99-sp-6;
  right: @l99-sp-6;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: @l99-primary;
  color: #fff;
  border: none;
  box-shadow: @l99-shadow-2;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 50;
  transition: opacity @l99-dur-mid @l99-ease;
  &:hover { opacity: 0.9; }
}

.td-fab-fade-enter-active, .td-fab-fade-leave-active {
  transition: opacity @l99-dur-mid @l99-ease;
}
.td-fab-fade-enter-from, .td-fab-fade-leave-to {
  opacity: 0;
}

@media (max-width: 767px) {
  .td-page { padding: @l99-sp-4; }
  .td-row--main { flex-direction: column; }
  .td-col--health { min-width: unset; }
}
</style>
