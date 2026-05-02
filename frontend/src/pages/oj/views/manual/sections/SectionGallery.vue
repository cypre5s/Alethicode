<template>
  <section id="gallery" class="manual-section manual-section--gallery">
    <header class="manual-section__head">
      <ManualNaiwaSticker :index="7" size="md" :rotate="8" />
      <div>
        <span class="manual-section__kicker">07.5 · 彩蛋</span>
        <h2>奶蛙图鉴</h2>
        <p>本节是趣味专栏，不承担说明功能。关闭"趣味模式"后整段会自动消失。</p>
      </div>
    </header>

    <ManualNaiwaGallery :fun-mode="funMode" @burst="(e) => $emit('burst', e)" />
  </section>
</template>

<script>
import ManualNaiwaSticker from '../ManualNaiwaSticker.vue'
import ManualNaiwaGallery from '../ManualNaiwaGallery.vue'

export default {
  name: 'SectionGallery',
  components: { ManualNaiwaSticker, ManualNaiwaGallery },
  props: {
    funMode: { type: Boolean, default: true }
  }
}
</script>

<style lang="less" scoped>
@import './shared.less';

.manual-section--gallery {
  background: linear-gradient(180deg, var(--warm-bg-subtle) 0%, var(--bg-card) 100%);
  content-visibility: auto;
  contain-intrinsic-size: 1px 600px;
}
</style>
