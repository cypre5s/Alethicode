<template>
  <div class="th-hero" role="banner">
    <div class="th-hero__bg" aria-hidden="true">
      <span class="th-hero__blob th-blob-a"></span>
      <span class="th-hero__blob th-blob-b"></span>
    </div>
    <div class="th-hero__content">
      <div class="th-hero__left">
        <TwinPersonaCard />
      </div>
      <div class="th-hero__right">
        <div class="th-hero__quote-card">
          <div class="th-hero__greeting">{{ greeting }}</div>
          <p class="th-hero__quote">{{ dailyQuote }}</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import TwinPersonaCard from './TwinPersonaCard.vue'

const QUOTES = [
  '每次提交都是一次学习的脉搏',
  '代码会说谎，但测试不会',
  '调试就是侦探工作——线索都在错误里',
  '学编程不是背语法，是学思维',
  '今天多写一行，明天少查一次',
  '报错不可怕，可怕的是不读报错',
  '递归的尽头是信任'
]

export default {
  name: 'TwinHero',
  components: { TwinPersonaCard },
  computed: {
    greeting () {
      const h = new Date().getHours()
      if (h < 6) return '夜深了，注意休息'
      if (h < 12) return '早上好'
      if (h < 18) return '下午好'
      return '晚上好'
    },
    dailyQuote () {
      const dayOfYear = Math.floor((Date.now() - new Date(new Date().getFullYear(), 0, 0)) / 86400000)
      return QUOTES[dayOfYear % QUOTES.length]
    }
  }
}
</script>

<style lang="less" scoped>
@import '~@/styles/l99-tokens.less';

.th-hero {
  position: relative;
  width: 100%;
  padding: @l99-sp-8 @l99-sp-6;
  overflow: hidden;
  border-radius: @l99-radius-lg;
  background: linear-gradient(135deg, @l99-primary-soft 0%, #fff 60%, fade(@l99-accent, 8%) 100%);
  margin-bottom: @l99-sp-6;
}

.th-hero__bg {
  position: absolute;
  inset: 0;
  pointer-events: none;
}
.th-hero__blob {
  position: absolute;
  border-radius: 50%;
  opacity: 0.06;
  &.th-blob-a { width: 300px; height: 300px; background: @l99-primary; top: -100px; right: -50px; }
  &.th-blob-b { width: 200px; height: 200px; background: @l99-accent; bottom: -60px; left: -40px; }
}

.th-hero__content {
  position: relative;
  display: flex;
  gap: @l99-sp-6;
  align-items: flex-start;
}

.th-hero__left { flex: 1; min-width: 0; }
.th-hero__right { flex-shrink: 0; width: 280px; }

.th-hero__quote-card {
  background: rgba(255,255,255,0.8);
  backdrop-filter: blur(8px);
  border-radius: @l99-radius-md;
  padding: @l99-sp-5;
  box-shadow: @l99-shadow-1;
}

.th-hero__greeting {
  font-size: @l99-fs-sm;
  color: @l99-neutral-500;
  margin-bottom: @l99-sp-2;
}

.th-hero__quote {
  font-size: @l99-fs-lg;
  color: @l99-neutral-900;
  font-weight: 500;
  line-height: 1.6;
  margin: 0;
}

@media (max-width: 767px) {
  .th-hero { padding: @l99-sp-4; }
  .th-hero__content { flex-direction: column; }
  .th-hero__right { width: 100%; }
}
</style>
