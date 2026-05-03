<template>
  <section id="tour" class="manual-section">
    <header class="manual-section__head">
      <ManualNaiwaSticker :index="2" size="md" :rotate="-3" />
      <div>
        <span class="manual-section__kicker">08 · Tour · 附录</span>
        <h2>页面导览</h2>
        <p>14 张主要页面的截图与说明。遇到陌生页面或者忘了某个按钮在哪，回来查一下。每张配一段"什么时候打开它 / 主要看什么 / 主要按钮"。</p>
      </div>
    </header>

    <div class="tour-grid">
      <article
        v-for="page in pages"
        :key="page.id"
        class="tour-card"
      >
        <figure class="tour-card__media">
          <img
            v-if="page.screenshot"
            :src="page.screenshot"
            :alt="`${page.title} 截图`"
            loading="lazy"
            decoding="async"
            @error="onImgError($event, page)"
          >
          <div v-else class="tour-card__placeholder">
            <ManualNaiwaSticker :index="5" size="lg" />
            <span>截图待补</span>
          </div>
        </figure>
        <div class="tour-card__body">
          <h3>{{ page.title }}</h3>
          <p>{{ page.desc }}</p>
          <ul>
            <li v-for="(pt, i) in page.points" :key="i">{{ pt }}</li>
          </ul>
          <button v-if="page.target" type="button" class="tour-card__go" @click="goPage(page.target)">
            前往该页 →
          </button>
        </div>
      </article>
    </div>
  </section>
</template>

<script>
import ManualNaiwaSticker from '../ManualNaiwaSticker.vue'
import { TOUR_PAGES } from '../manualContent.js'

export default {
  name: 'SectionTour',
  components: { ManualNaiwaSticker },
  data () {
    return {
      pages: TOUR_PAGES.map(p => ({ ...p, _hasScreenshot: !!p.screenshot }))
    }
  },
  methods: {
    onImgError (event, page) {
      page.screenshot = ''
      page._hasScreenshot = false
    },
    goPage (target) {
      this.$router.push(target).catch(() => {})
    }
  }
}
</script>

<style lang="less" scoped>
@import './shared.less';

.tour-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 16px;
}

.tour-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  overflow: hidden;
  transition: box-shadow 0.2s, transform 0.2s, border-color 0.2s;

  &:hover {
    box-shadow: var(--shadow-md);
    transform: translateY(-2px);
    border-color: var(--warm-primary);
  }
}

.tour-card__media {
  margin: 0;
  aspect-ratio: 16 / 9;
  background: var(--bg-panel);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border-bottom: 1px solid var(--border-color);

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    display: block;
  }
}

.tour-card__placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  color: var(--text-disabled);
  font-size: 13px;
}

.tour-card__body {
  padding: 16px 18px 18px;

  h3 {
    margin: 0 0 6px;
    font-size: 15px;
    color: var(--text-primary);
  }

  p {
    margin: 0 0 10px;
    color: var(--text-secondary);
    font-size: 13px;
    line-height: 1.6;
    text-wrap: pretty;
  }

  ul {
    margin: 0 0 12px;
    padding-left: 16px;
    color: var(--text-secondary);
    font-size: 12px;
    line-height: 1.7;

    li { margin-bottom: 2px; }
  }
}

.tour-card__go {
  border: 0;
  background: transparent;
  font-size: 13px;
  font-weight: 600;
  color: var(--primary-color);
  padding: 0;
  cursor: pointer;

  &:hover { color: var(--primary-hover); }
}
</style>
