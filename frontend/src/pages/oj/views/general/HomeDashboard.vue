<template>
  <div class="home-dashboard">
    <section class="hero-banner" :style="{ '--gal-accent': heroChar ? heroChar.color : '#DC3545' }">
      <div class="hero-content">
        <div class="hero-left">
          <h1 class="hero-greeting">{{ greetingPhrase }}，{{ displayName }}</h1>
          <p class="hero-date">{{ todayDateStr }}</p>
          <div class="hero-char-bubble" v-if="heroChar">
            <img :src="heroSpriteSrc" class="hero-char-avatar" :alt="heroChar.name" />
            <div class="hero-char-speech">
              <span class="hero-char-name" :style="{ color: heroChar.color }">{{ heroChar.name }}</span>
              <span class="hero-char-line">{{ heroGreeting }}</span>
            </div>
          </div>
        </div>
      </div>
    </section>

    <div class="dashboard-columns">
      <div class="col-main">
        <section class="continue-card">
          <template v-if="courseLoading">
            <div class="continue-skeleton">
              <div class="skeleton-bar skeleton-title"></div>
              <div class="skeleton-bar skeleton-progress"></div>
              <div class="skeleton-bar skeleton-stats"></div>
            </div>
          </template>
          <template v-else-if="hasCourse">
            <div class="continue-header">
              <div class="continue-header-left">
                <svg class="continue-icon" viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z"/><path d="M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z"/></svg>
                <span class="continue-label">继续学习</span>
              </div>
              <select v-if="allPacks.length > 1" class="pack-selector" v-model="languagePackId" @change="onPackChange">
                <option v-for="p in allPacks" :key="p.id" :value="p.id">{{ p.name || p.language }}</option>
              </select>
            </div>
            <h2 class="continue-title">{{ courseTitle }}</h2>
            <div class="mastery-bar-wrap">
              <div class="mastery-bar">
                <div class="mastery-fill" :style="{ width: masteryPct + '%' }"></div>
              </div>
              <span class="mastery-text">掌握度 {{ masteryPct }}%</span>
            </div>
            <div class="continue-stats">
              <div class="stat-item">
                <span class="stat-value">{{ progress.problems_attempted || 0 }}</span>
                <span class="stat-label">已做题</span>
              </div>
              <div class="stat-item">
                <span class="stat-value">{{ progress.problems_solved || 0 }}</span>
                <span class="stat-label">已通过</span>
              </div>
              <div class="stat-item" v-if="reviewDueCount > 0">
                <span class="stat-value stat-review">{{ reviewDueCount }}</span>
                <span class="stat-label">待复习</span>
              </div>
            </div>
            <button class="continue-btn" @click="$router.push('/problem')">继续学习</button>
          </template>
          <template v-else>
            <div class="continue-header">
              <svg class="continue-icon" viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z"/><path d="M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z"/></svg>
              <span class="continue-label">开始探索</span>
            </div>
            <h2 class="continue-title">开始做题</h2>
            <p class="continue-desc">前往题库选择练习题目，提升编程能力。</p>
            <button class="continue-btn" @click="$router.push('/problem')">浏览题目</button>
          </template>
        </section>

        <section v-if="weeklySummary && weeklySummary.total_errors > 0" class="weekly-section weekly-section--main">
          <h3 class="section-heading">本周错题概况</h3>
          <div class="weekly-card">
            <div class="weekly-stat">
              <span class="weekly-num">{{ weeklySummary.total_errors }}</span>
              <span class="weekly-label">本周错题</span>
            </div>
            <div class="weekly-stat">
              <span class="weekly-num weekly-conquered">{{ weeklySummary.conquered_count }}</span>
              <span class="weekly-label">已攻克</span>
            </div>
            <div v-if="weeklySummary.top_error_label" class="weekly-top">
              <span class="weekly-top-label">最常犯错类型</span>
              <span class="weekly-top-value">{{ weeklySummary.top_error_label }}</span>
            </div>
          </div>
        </section>

        <section class="quick-actions">
          <div class="action-card action-card--blue" @click="$router.push('/problem')">
            <img :src="cardSprites.problems" class="action-char-thumb" :alt="cardCharacters.problems.name" style="width:48px;height:56px;max-width:48px;max-height:56px;object-fit:cover;object-position:top center;border-radius:8px" />
            <span class="action-title">去做题</span>
            <span class="action-desc">刷题提升编程能力</span>
          </div>
          <div class="action-card action-card--purple" @click="$router.push('/language-pack-qa')">
            <img :src="cardSprites.qa" class="action-char-thumb" :alt="cardCharacters.qa.name" style="width:48px;height:56px;max-width:48px;max-height:56px;object-fit:cover;object-position:top center;border-radius:8px" />
            <span class="action-title">课件问答</span>
            <span class="action-desc">AI 助手答疑解惑</span>
          </div>
          <div class="action-card action-card--green" @click="$router.push('/classroom')">
            <img :src="cardSprites.classroom" class="action-char-thumb" :alt="cardCharacters.classroom.name" style="width:48px;height:56px;max-width:48px;max-height:56px;object-fit:cover;object-position:top center;border-radius:8px" />
            <span class="action-title">我的班级</span>
            <span class="action-desc">查看班级与协作</span>
          </div>
        </section>
      </div>

      <div class="col-side">
        <section class="recent-activity">
          <h3 class="section-heading">最近提交</h3>
          <div v-if="recentSubmissions.length" class="activity-list">
            <div v-for="sub in recentSubmissions" :key="sub.id" class="activity-row"
                 @click="$router.push({name: 'submission-details', params: {id: sub.id}})">
              <span class="activity-badge" :class="sub.result === 0 ? 'is-ac' : 'is-fail'">
                {{ sub.result === 0 ? 'AC' : (sub.result === -1 ? 'WA' : (sub.result === -2 ? 'CE' : 'ERR')) }}
              </span>
              <span class="activity-title">{{ sub.problem_title || ('题目 #' + sub.problem) }}</span>
              <span class="activity-time">{{ relativeTime(sub.create_time) }}</span>
            </div>
          </div>
          <div v-else class="activity-empty">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>
            <span>还没有提交记录，<a @click.prevent="$router.push('/problem')">去做第一道题吧</a></span>
          </div>
        </section>

        <section v-if="announcements.length" class="announce-section">
          <h3 class="section-heading">平台公告</h3>
          <div class="announce-list">
            <div v-for="ann in announcements" :key="ann.id" class="announce-item"
                 @click="$router.push('/announcements')">
              <div class="announce-dot"></div>
              <div class="announce-body">
                <span class="announce-title">{{ ann.title }}</span>
                <span class="announce-date">{{ formatDate(ann.create_time) }}</span>
              </div>
            </div>
          </div>
        </section>

        <section v-if="supplementPlan && supplementPlan.cards && supplementPlan.cards.length" class="next-step-section">
          <h3 class="section-heading">下一步学习建议</h3>
          <div class="next-step-card">
            <div class="next-step-intro-row" v-if="suggestChar">
              <img :src="suggestSpriteSrc" class="next-step-char-avatar" :alt="suggestChar.name" />
              <div class="next-step-intro">{{ supplementPlan.intro_message }}</div>
            </div>
            <div v-else class="next-step-intro">{{ supplementPlan.intro_message }}</div>
            <div class="next-step-list">
              <div
                v-for="(card, idx) in supplementPlan.cards"
                :key="card.card_type + '-' + idx"
                class="next-step-item">
                <div class="next-step-item-head">
                  <span class="next-step-index">Step {{ idx + 1 }}</span>
                  <span class="next-step-type">{{ cardTypeLabel(card.card_type) }}</span>
                </div>
                <div class="next-step-title">{{ card.title || (card.payload && card.payload.title) || '练习任务' }}</div>
                <div class="next-step-why">{{ card.why_this_now }}</div>
              </div>
            </div>
          </div>
        </section>

        <section v-if="reviewDueList.length > 0" class="review-section">
          <h3 class="section-heading">今日复习</h3>
          <div class="review-grid">
            <div v-for="card in reviewDueList" :key="card.error_taxonomy" class="review-chip"
                 :class="{ 'is-mastered': card.last_package_mastery }">
              <span class="review-tag" :class="'tag-' + card.error_taxonomy">{{ card.label }}</span>
              <span v-if="card.last_package_mastery" class="review-mastered-badge">已掌握</span>
              <span class="review-count">{{ card.notebook_count }} 条错题</span>
              <button v-if="card.has_active_package" class="review-btn review-btn--warn"
                      @click="continueReview(card)">继续复习</button>
              <button v-else class="review-btn review-btn--primary"
                      @click="startReview(card)">{{ card.last_package_mastery ? '再练一次' : '开始复习' }}</button>
            </div>
          </div>
        </section>
      </div>
    </div>

  </div>
</template>

<script>
  import { mapGetters } from 'vuex'
  import api from '@oj/api'
  import { encodeRouteCtx } from '@/utils/urlCipher'
  import { getCharacter, getSpritePath } from '../problem/characterConfig'

  const WEEKDAYS = ['日', '一', '二', '三', '四', '五', '六']
  const MONTHS = ['1', '2', '3', '4', '5', '6', '7', '8', '9', '10', '11', '12']
  const GAL_IDS = ['nene', 'yoshino', 'ayase', 'kanna', 'murasame']
  const HERO_CHAR_ID = 'murasame'
  const HERO_EXTRA_LINES = {
    murasame: ['强者不需要借口', '别让我失望', '……切，算你有点意思', '这种水平还差得远', '别停下来'],
    nene: ['需要帮忙就说一声哦～', '今天学了什么新东西吗？', '你已经很努力了呢', '休息一下也没关系的'],
    yoshino: ['代码要写规范', '别偷懒', '我看着呢', '你上次提交的代码……还行'],
    ayase: ['不会比我快的！', '来比一场！', '今天也要加油！', '嘿嘿，有意思'],
    kanna: ['……嗯', '……在看', '安静地写吧', '……']
  }
  const SUGGEST_CHAR_ID = 'kanna'
  const CARD_CHAR_MAP = { problems: 'ayase', qa: 'nene', classroom: 'yoshino' }

  const CHAR_GREETINGS = {
    nene: {
      morning: '早上好～今天也一起学编程吧！',
      noon: '中午好～要不要休息一下再继续呢？',
      afternoon: '下午好～来做道题放松一下吧',
      evening: '晚上好～夜间学习效率更高哦～',
      night: '这么晚了……要注意休息哦'
    },
    yoshino: {
      morning: '早安。今天的学习计划排好了吗？',
      noon: '午间适合复习上午的内容',
      afternoon: '下午了，检查一下进度吧',
      evening: '……别偷懒，今天的任务完成了吗',
      night: '熬夜写代码……我不是在等你'
    },
    ayase: {
      morning: '嘿！早上好！今天也要赢过我哦！',
      noon: '饿了吧！吃完饭来比一场！',
      afternoon: '下午了！不会已经偷懒了吧？',
      evening: '晚上好！来做题看看谁更快！',
      night: '这么晚还在？……我也是啦！'
    },
    kanna: {
      morning: '……早',
      noon: '……午安',
      afternoon: '……',
      evening: '……来了',
      night: '……还没睡'
    },
    murasame: {
      morning: '切，这么早？……算你有点干劲',
      noon: '午休？我可不休息',
      afternoon: '别磨蹭，下午了',
      evening: '……来吧，出几道题给你',
      night: '深夜才是编程的黄金时间'
    }
  }

  const DAILY_TIPS = [
    '每天写几行代码，一个月后你会惊讶于自己的进步。',
    '遇到 bug 别着急，调试本身就是最好的学习。',
    '看不懂代码？试着一行一行读，就像读句子一样。',
    '写代码就像搭积木，先把小块搞清楚，再组合起来。',
    '今天学一个新概念，明天就多一种解决问题的方式。',
    'print() 是你最好的朋友，不确定的时候就打印看看。',
    '每个程序员都是从 Hello World 开始的，你已经在路上了。',
    '犯错是学习的一部分，错误信息其实在告诉你答案。',
    '代码写完先跑一遍，别等到最后才测试。',
    '不理解的时候画个流程图，思路会清晰很多。',
    '学编程不需要数学很好，需要的是耐心和好奇心。',
    '复习昨天的代码，也是一种很有效的学习方式。',
    '把大问题拆成小步骤，每一步都不难。',
    '变量命名写清楚，未来的自己会感谢现在的你。',
    '看别人的代码也是学习，试着理解他们的思路。',
    '想不出来的时候，起来走走，灵感可能就来了。',
    '不要怕问问题，每个好程序员都问过很多"笨"问题。',
    '今天的一小步，就是编程路上的一大步。',
    '语法记不住很正常，用多了自然就熟了。',
    '试着用自己的话解释代码，能说清楚就是真懂了。'
  ]

  export default {
    name: 'HomeDashboard',
    components: {},
    data () {
      return {
        heroLineOverride: '',
        heroLineTimer: null,
        courseLoading: true,
        courseTitle: '',
        languagePackId: null,
        allPacks: [],
        progress: {},
        reviewDueList: [],
        reviewDueCount: 0,
        weeklySummary: null,
        supplementPlan: null,
        recentSubmissions: [],
        announcements: []
      }
    },
    computed: {
      ...mapGetters(['user', 'profile']),
      displayName () {
        return this.profile.real_name || this.user.username || '同学'
      },
      greetingPhrase () {
        const h = new Date().getHours()
        if (h < 6) return '夜深了'
        if (h < 11) return '早上好'
        if (h < 14) return '中午好'
        if (h < 18) return '下午好'
        return '晚上好'
      },
      heroChar () {
        return getCharacter(HERO_CHAR_ID)
      },
      heroSpriteSrc () {
        return getSpritePath(HERO_CHAR_ID, 'smirk')
      },
      heroGreeting () {
        if (this.heroLineOverride) return this.heroLineOverride
        const h = new Date().getHours()
        const slot = h < 6 ? 'night' : h < 11 ? 'morning' : h < 14 ? 'noon' : h < 18 ? 'afternoon' : h < 22 ? 'evening' : 'night'
        const lines = CHAR_GREETINGS[HERO_CHAR_ID] || CHAR_GREETINGS.murasame
        return lines[slot] || lines.morning
      },
      suggestChar () {
        return getCharacter(SUGGEST_CHAR_ID)
      },
      suggestSpriteSrc () {
        return getSpritePath(SUGGEST_CHAR_ID, 'contemplative')
      },
      cardCharacters () {
        return {
          problems: getCharacter(CARD_CHAR_MAP.problems),
          qa: getCharacter(CARD_CHAR_MAP.qa),
          classroom: getCharacter(CARD_CHAR_MAP.classroom)
        }
      },
      cardSprites () {
        return {
          problems: getSpritePath(CARD_CHAR_MAP.problems, 'grin'),
          qa: getSpritePath(CARD_CHAR_MAP.qa, 'smile'),
          classroom: getSpritePath(CARD_CHAR_MAP.classroom, 'slight_smile')
        }
      },
      todayDateStr () {
        const d = new Date()
        return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日 · 星期${WEEKDAYS[d.getDay()]}`
      },
      dailyTip () {
        const dayIndex = Math.floor(Date.now() / 86400000) % DAILY_TIPS.length
        return DAILY_TIPS[dayIndex]
      },
      hasCourse () {
        return !!this.languagePackId
      },
      masteryPct () {
        const m = this.progress.overall_mastery
        if (m == null) return 0
        return Math.round(m * 100)
      }
    },
    created () {
      this.CARD_TYPE_LABELS = {
        course_example: '课件例题',
        faded_example: '渐退示例',
        worked_example: '完整示例',
        parsons_problem: '排序题',
        minimal_hint: '最小提示',
        coding_problem: '编码练习',
        transfer_problem: '迁移练习',
        problem_guide: '审题导学',
        ideate_analysis: '思路分析',
        error_diagnosis: '错误诊断',
        execution_trace_explainer: '执行过程',
        post_ac: '通过总结',
        ai_reply: 'AI 回复'
      }
    },
    mounted () {
      this.loadData()
      this._heroIdx = 0
      this.heroLineTimer = setInterval(() => {
        const pool = HERO_EXTRA_LINES[HERO_CHAR_ID] || HERO_EXTRA_LINES.murasame
        this.heroLineOverride = pool[this._heroIdx % pool.length]
        this._heroIdx++
      }, 30000)
    },
    beforeUnmount () {
      clearInterval(this.heroLineTimer)
    },
    methods: {
      getSpritePath,
      cardTypeLabel (type) {
        return (this.CARD_TYPE_LABELS && this.CARD_TYPE_LABELS[type]) || type
      },
      async loadData () {
        this.courseLoading = true
        try {
          const packRes = await api.getVisibleLanguagePackList()
          const packs = (packRes.data && packRes.data.data) || []
          this.allPacks = packs
          if (packs.length > 0) {
            const savedId = Number(localStorage.getItem('home_selected_lp'))
            const pack = (savedId && packs.find(p => p.id === savedId)) || packs[0]
            this.languagePackId = pack.id
            localStorage.setItem('home_selected_lp', String(pack.id))
            this.courseTitle = pack.name || pack.language || '课程'
            const progressRes = await api.getCourseProgress(pack.id)
            this.progress = (progressRes.data && progressRes.data.data) || {}
            await this.loadSupplementPlan()
          }
        } catch {
          // fail-fast: 数据加载失败时 hasCourse 为 false, UI 降级显示"浏览课程"
        }
        try {
          const reviewRes = await api.getReviewDue(9, this.languagePackId)
          const data = (reviewRes.data && reviewRes.data.data) || {}
          this.reviewDueList = data.due_reviews || []
          const stats = data.stats || {}
          this.reviewDueCount = stats.focus_count || this.reviewDueList.length
        } catch {}
        try {
          const weeklyRes = await api.getNotebookWeeklySummary()
          this.weeklySummary = (weeklyRes.data && weeklyRes.data.data) || null
        } catch {}
        this.courseLoading = false
        this.loadRecentSubmissions()
        this.loadAnnouncements()
      },
      async loadRecentSubmissions () {
        try {
          const res = await api.getSubmissionList(0, 5, {})
          const data = (res.data && res.data.data) || {}
          const results = Array.isArray(data.results) ? data.results : []
          this.recentSubmissions = results.map(s => ({
            id: s.id,
            problem: s.problem,
            problem_title: s.problem_title || '',
            result: s.result,
            create_time: s.create_time
          }))
        } catch {
          this.recentSubmissions = []
        }
      },
      async loadAnnouncements () {
        try {
          const res = await api.getAnnouncementList(0, 2)
          const data = (res.data && res.data.data) || {}
          const results = Array.isArray(data.results) ? data.results : []
          this.announcements = results.slice(0, 2)
        } catch {
          this.announcements = []
        }
      },
      relativeTime (utcStr) {
        const now = Date.now()
        const t = new Date(utcStr).getTime()
        const diff = now - t
        if (diff < 60000) return '刚刚'
        if (diff < 3600000) return Math.floor(diff / 60000) + ' 分钟前'
        if (diff < 86400000) return Math.floor(diff / 3600000) + ' 小时前'
        if (diff < 172800000) return '昨天'
        if (diff < 604800000) return Math.floor(diff / 86400000) + ' 天前'
        const d = new Date(utcStr)
        return `${MONTHS[d.getMonth()]}月${d.getDate()}日`
      },
      formatDate (utcStr) {
        const d = new Date(utcStr)
        return `${MONTHS[d.getMonth()]}月${d.getDate()}日`
      },
      async onPackChange () {
        const pack = this.allPacks.find(p => p.id === this.languagePackId)
        if (!pack) return
        localStorage.setItem('home_selected_lp', String(pack.id))
        this.courseTitle = pack.name || pack.language || '课程'
        this.courseLoading = true
        try {
          const progressRes = await api.getCourseProgress(pack.id)
          this.progress = (progressRes.data && progressRes.data.data) || {}
        } catch {
          this.progress = {}
        }
        await this.loadSupplementPlan()
        try {
          const reviewRes = await api.getReviewDue(9, this.languagePackId)
          const data = (reviewRes.data && reviewRes.data.data) || {}
          this.reviewDueList = data.due_reviews || []
          const stats = data.stats || {}
          this.reviewDueCount = stats.focus_count || this.reviewDueList.length
        } catch {
          this.reviewDueList = []
          this.reviewDueCount = 0
        }
        this.courseLoading = false
      },
      async loadSupplementPlan () {
        if (!this.languagePackId) {
          this.supplementPlan = null
          return
        }
        try {
          const res = await api.getSupplementPlan({
            trigger: 'warmup',
            language_pack_id: this.languagePackId
          })
          this.supplementPlan = (res.data && res.data.data) || null
        } catch {
          this.supplementPlan = null
        }
      },
      async startReview (card) {
        try {
          const res = await api.createReviewPackage({
            error_taxonomy: card.error_taxonomy,
            language_pack_id: this.languagePackId,
            trigger: 'daily_review'
          })
          const pkg = res.data.data
          await this.$router.push({ name: 'error-review-package', query: { ctx: encodeRouteCtx({ pkg: pkg.id }) } })
        } catch (err) {
          const msg = (err.response && err.response.data && err.response.data.data) || '创建复习包失败'
          this.$error(msg)
        }
      },
      continueReview (card) {
        this.$router.push({ name: 'error-review-package', query: { ctx: encodeRouteCtx({ pkg: card.active_page_id }) } })
      }
    }
  }
</script>

<style lang="less" scoped>
  .home-dashboard {
    max-width: 1160px;
    margin: 0 auto;
    padding: 32px 24px 48px;
  }

  /* ── Two-Column Layout (golden ratio 1.618:1) ── */
  .dashboard-columns {
    display: grid;
    grid-template-columns: 1.618fr 1fr;
    gap: 28px;
    align-items: start;
  }
  .col-main {
    min-width: 0;
  }
  .col-side {
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 20px;
  }

  /* ── Hero Banner ── */
  .hero-banner {
    background: linear-gradient(135deg, #e8f0fe 0%, #f0f6ff 40%, #fff 100%);
    border: 1px solid #dce6f5;
    border-radius: 18px;
    padding: 28px 32px 24px;
    margin-bottom: 24px;
    position: relative;
    overflow: hidden;
    &::before {
      content: '';
      position: absolute;
      top: -40px;
      right: -40px;
      width: 140px;
      height: 140px;
      background: radial-gradient(circle, rgba(26, 115, 232, 0.06) 0%, transparent 70%);
      border-radius: 50%;
    }
  }
  .hero-content {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 16px;
    position: relative;
    z-index: 1;
  }
  .hero-greeting {
    font-size: 28px;
    font-weight: 800;
    color: var(--text-primary, #1a1a1a);
    margin: 0;
    line-height: 1.3;
    letter-spacing: -0.01em;
  }
  .hero-date {
    font-size: 14px;
    color: var(--text-secondary, #777);
    margin: 6px 0 0;
    font-weight: 400;
  }
  .hero-char-bubble {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-top: 10px;
  }

  .hero-char-avatar {
    width: 44px;
    height: 44px;
    border-radius: 50%;
    object-fit: cover;
    object-position: top center;
    border: 2px solid var(--gal-accent, #F4C2D0);
    flex-shrink: 0;
    background: rgba(255,255,255,0.6);
  }

  .hero-char-speech {
    display: flex;
    flex-direction: column;
    gap: 2px;
    padding: 8px 14px;
    border-radius: 12px;
    background: rgba(255,255,255,0.65);
    backdrop-filter: blur(6px);
    position: relative;
  }

  .hero-char-name {
    font-size: 12px;
    font-weight: 700;
    line-height: 1.2;
  }

  .hero-char-line {
    font-size: 13px;
    color: #444;
    line-height: 1.5;
    animation: hero-char-float 4s ease-in-out infinite;
  }

  @keyframes hero-char-float {
    0%, 100% { transform: translateY(0); }
    50% { transform: translateY(-2px); }
  }
  .hero-tip {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-top: 16px;
    padding: 9px 14px;
    background: rgba(255, 255, 255, 0.7);
    border-radius: 10px;
    border: 1px solid rgba(26, 115, 232, 0.1);
  }
  .tip-icon {
    color: #f59e0b;
    flex-shrink: 0;
    opacity: 0.85;
  }
  .tip-text {
    font-size: 13px;
    color: var(--text-secondary, #555);
    line-height: 1.6;
  }

  /* ── Continue Learning Card ── */
  .continue-card {
    background: var(--bg-card, #fff);
    border: 1px solid var(--border-color, #e8e8e8);
    border-radius: 16px;
    padding: 28px 32px;
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
  }
  .continue-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    margin-bottom: 8px;
    color: var(--primary-color, #1a73e8);
  }
  .continue-header-left {
    display: flex;
    align-items: center;
    gap: 8px;
  }
  .continue-label {
    font-size: 14px;
    font-weight: 600;
    letter-spacing: 0.02em;
  }
  .pack-selector {
    appearance: none;
    border: 1px solid #d9d9d9;
    border-radius: 8px;
    padding: 6px 32px 6px 12px;
    font-size: 13px;
    font-weight: 500;
    color: #303133;
    background: #fff url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 24 24' fill='none' stroke='%23999' stroke-width='2'%3E%3Cpolyline points='6 9 12 15 18 9'/%3E%3C/svg%3E") no-repeat right 10px center;
    cursor: pointer;
    outline: none;
    transition: border-color 0.2s;
    max-width: 200px;
    &:hover { border-color: var(--primary-color, #1a73e8); }
    &:focus { border-color: var(--primary-color, #1a73e8); box-shadow: 0 0 0 2px rgba(26, 115, 232, 0.1); }
  }
  .continue-title {
    font-size: 22px;
    font-weight: 700;
    color: var(--text-primary, #1a1a1a);
    margin: 0 0 16px;
  }
  .continue-desc {
    font-size: 14px;
    color: var(--text-secondary, #666);
    margin: 0 0 20px;
    line-height: 1.6;
  }
  .mastery-bar-wrap {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 20px;
  }
  .mastery-bar {
    flex: 1;
    height: 8px;
    background: var(--bg-panel, #f0f2f5);
    border-radius: 4px;
    overflow: hidden;
  }
  .mastery-fill {
    height: 100%;
    border-radius: 4px;
    background: linear-gradient(90deg, #1a73e8, #34a853);
    transition: width 0.6s ease;
  }
  .mastery-text {
    font-size: 13px;
    font-weight: 600;
    color: var(--text-secondary, #666);
    white-space: nowrap;
  }
  .continue-stats {
    display: flex;
    gap: 32px;
    margin-bottom: 24px;
  }
  .stat-item {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }
  .stat-value {
    font-size: 20px;
    font-weight: 700;
    color: var(--text-primary, #1a1a1a);
  }
  .stat-review {
    color: #e65100;
  }
  .stat-label {
    font-size: 12px;
    color: var(--text-secondary, #999);
  }
  .continue-btn {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    height: 40px;
    padding: 0 28px;
    border: none;
    border-radius: 8px;
    background: var(--primary-color, #1a73e8);
    color: #fff;
    font-size: 15px;
    font-weight: 600;
    cursor: pointer;
    transition: background 0.2s, transform 0.1s;
    &:hover { background: #1557b0; }
    &:active { transform: scale(0.97); }
  }

  /* ── Skeleton ── */
  .continue-skeleton {
    display: flex;
    flex-direction: column;
    gap: 16px;
  }
  .skeleton-bar {
    border-radius: 6px;
    background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
    background-size: 200% 100%;
    animation: shimmer 1.5s infinite;
  }
  .skeleton-title { width: 45%; height: 24px; }
  .skeleton-progress { width: 100%; height: 8px; }
  .skeleton-stats { width: 60%; height: 20px; }
  @keyframes shimmer {
    0% { background-position: 200% 0; }
    100% { background-position: -200% 0; }
  }

  /* ── Quick Actions ── */
  .quick-actions {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 16px;
    margin-top: 24px;
  }
  .action-card {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 10px;
    padding: 24px 16px 20px;
    background: var(--bg-card, #fff);
    border: 1px solid var(--border-color, #e8e8e8);
    border-radius: 14px;
    cursor: pointer;
    transition: box-shadow 0.2s, transform 0.15s, border-color 0.2s;
    &:hover {
      box-shadow: 0 6px 20px rgba(0, 0, 0, 0.07);
      transform: translateY(-3px);
    }
  }
  .action-card--blue:hover  { border-color: #a8c7fa; }
  .action-card--purple:hover { border-color: #c9b8f8; }
  .action-card--green:hover  { border-color: #a8dab5; }

  .action-icon-wrap {
    width: 48px;
    height: 48px;
    border-radius: 14px;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
    transition: transform 0.2s;
    .action-card:hover & { transform: scale(1.08); }
  }
  .action-icon-wrap--blue {
    background: linear-gradient(135deg, #e8f0fe, #d2e3fc);
    color: #1a73e8;
  }
  .action-icon-wrap--purple {
    background: linear-gradient(135deg, #f3e8ff, #e9d5ff);
    color: #7c3aed;
  }
  .action-icon-wrap--green {
    background: linear-gradient(135deg, #dcfce7, #bbf7d0);
    color: #16a34a;
  }
  .action-title {
    font-size: 15px;
    font-weight: 600;
    color: var(--text-primary, #1a1a1a);
  }
  .action-desc {
    font-size: 12px;
    color: var(--text-secondary, #999);
  }

  /* ── Recent Activity ── */
  .recent-activity {}
  .activity-list {
    background: var(--bg-card, #fff);
    border: 1px solid var(--border-color, #e8e8e8);
    border-radius: 12px;
    overflow: hidden;
  }
  .activity-row {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 12px 18px;
    cursor: pointer;
    transition: background 0.15s;
    &:hover { background: #f8fafc; }
    & + & { border-top: 1px solid var(--border-color, #f0f0f0); }
  }
  .activity-badge {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 36px;
    height: 22px;
    border-radius: 4px;
    font-size: 11px;
    font-weight: 700;
    letter-spacing: 0.04em;
    flex-shrink: 0;
  }
  .activity-badge.is-ac {
    background: #f0fdf4;
    color: #16a34a;
    border: 1px solid #bbf7d0;
  }
  .activity-badge.is-fail {
    background: #fef2f2;
    color: #dc2626;
    border: 1px solid #fecaca;
  }
  .activity-title {
    font-size: 14px;
    color: var(--text-primary, #1a1a1a);
    flex: 1;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .activity-time {
    font-size: 12px;
    color: var(--text-secondary, #999);
    white-space: nowrap;
    flex-shrink: 0;
  }
  .activity-empty {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    padding: 28px 16px;
    background: var(--bg-card, #fff);
    border: 1px dashed var(--border-color, #ddd);
    border-radius: 12px;
    font-size: 14px;
    color: var(--text-secondary, #999);
    a {
      color: var(--primary-color, #1a73e8);
      font-weight: 600;
      cursor: pointer;
      text-decoration: none;
      &:hover { text-decoration: underline; }
    }
  }

  /* ── Announcements ── */
  .announce-section {}
  .announce-list {
    background: var(--bg-card, #fff);
    border: 1px solid var(--border-color, #e8e8e8);
    border-radius: 12px;
    overflow: hidden;
  }
  .announce-item {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 14px 18px;
    cursor: pointer;
    transition: background 0.15s;
    &:hover { background: #f8fafc; }
    & + & { border-top: 1px solid var(--border-color, #f0f0f0); }
  }
  .announce-dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: #f59e0b;
    flex-shrink: 0;
  }
  .announce-body {
    display: flex;
    align-items: center;
    gap: 12px;
    flex: 1;
    min-width: 0;
  }
  .announce-title {
    font-size: 14px;
    color: var(--text-primary, #1a1a1a);
    font-weight: 500;
    flex: 1;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .announce-date {
    font-size: 12px;
    color: var(--text-secondary, #999);
    white-space: nowrap;
    flex-shrink: 0;
  }

  /* ── Section Heading ── */
  .section-heading {
    font-size: 16px;
    font-weight: 700;
    color: var(--text-primary, #1a1a1a);
    margin: 0 0 14px;
  }

  /* ── Next Step / Weekly / Review ── */
  .weekly-section {}
  .weekly-section--main {
    margin-top: 24px;
  }
  .next-step-section {}
  .next-step-card {
    padding: 16px 18px;
    border: 1px solid var(--border-color, #e8e8e8);
    border-radius: 12px;
    background: var(--bg-card, #fff);
  }
  .next-step-intro {
    font-size: 13px;
    line-height: 1.6;
    color: var(--text-secondary, #666);
    margin-bottom: 12px;
  }
  .next-step-list {
    display: flex;
    flex-direction: column;
    gap: 10px;
  }
  .next-step-item {
    border: 1px solid #e8edf7;
    border-radius: 10px;
    padding: 10px 12px;
    background: #fbfdff;
  }
  .next-step-item-head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 8px;
    margin-bottom: 6px;
  }
  .next-step-index {
    font-size: 11px;
    color: #4f46e5;
    font-weight: 700;
  }
  .next-step-type {
    font-size: 11px;
    color: #475569;
    background: #e2e8f0;
    border-radius: 999px;
    padding: 2px 8px;
  }
  .next-step-title {
    font-size: 14px;
    color: var(--text-primary, #1a1a1a);
    font-weight: 600;
    line-height: 1.4;
    margin-bottom: 4px;
  }
  .next-step-why {
    font-size: 12px;
    color: var(--text-secondary, #666);
    line-height: 1.5;
  }
  .weekly-card {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 20px;
    padding: 18px 20px;
    background: var(--bg-card, #fff);
    border: 1px solid var(--border-color, #e8e8e8);
    border-radius: 12px;
  }
  .weekly-stat {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }
  .weekly-num {
    font-size: 22px;
    font-weight: 700;
    color: #ef4444;
  }
  .weekly-conquered {
    color: #16a34a;
  }
  .weekly-label {
    font-size: 12px;
    color: var(--text-secondary, #999);
  }
  .weekly-top {
    display: flex;
    flex-direction: column;
    gap: 2px;
    margin-left: auto;
  }
  .weekly-top-label {
    font-size: 12px;
    color: var(--text-secondary, #999);
  }
  .weekly-top-value {
    font-size: 15px;
    font-weight: 600;
    color: var(--text-primary, #1a1a1a);
  }

  /* ── Review Section ── */
  .review-section {}
  .review-grid {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }
  .review-chip {
    display: flex;
    flex-direction: column;
    gap: 8px;
    padding: 14px;
    border-radius: 10px;
    border: 1px solid var(--border-color, #e8e8e8);
    background: var(--bg-card, #fafbfc);
    transition: box-shadow 0.2s;
    &:hover { box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06); }
    &.is-mastered { background: #f0faf0; border-color: #c3e6cb; }
  }
  .review-tag {
    display: inline-block;
    font-size: 13px;
    font-weight: 600;
    padding: 2px 8px;
    border-radius: 4px;
    background: #eef4ff;
    color: #2d6cdf;
    align-self: flex-start;
  }
  .review-mastered-badge {
    font-size: 11px;
    color: #34a853;
    font-weight: 600;
  }
  .review-count {
    font-size: 12px;
    color: var(--text-secondary, #777);
  }
  .review-btn {
    align-self: flex-start;
    height: 28px;
    padding: 0 14px;
    border: none;
    border-radius: 6px;
    font-size: 12px;
    font-weight: 600;
    cursor: pointer;
    color: #fff;
    transition: opacity 0.15s;
    &:hover { opacity: 0.85; }
  }
  .review-btn--primary { background: var(--primary-color, #1a73e8); }
  .review-btn--warn { background: #e6a700; }

  .tag-syntax_error { background: #fff0f0; color: #cf1322; }
  .tag-runtime_error { background: #fff7e6; color: #d46b08; }
  .tag-logic_error { background: #e6f7ff; color: #096dd9; }
  .tag-boundary_condition { background: #f6ffed; color: #389e0d; }
  .tag-performance { background: #fff2e8; color: #d4380d; }
  .tag-algorithm_error { background: #f9f0ff; color: #722ed1; }
  .tag-input_parsing { background: #e6fffb; color: #13c2c2; }
  .tag-name_or_type_error { background: #fcffe6; color: #7cb305; }
  .tag-unknown { background: #f5f5f5; color: #8c8c8c; }


  /* ── Responsive ── */
  @media (max-width: 900px) {
    .dashboard-columns {
      grid-template-columns: 1fr;
      gap: 20px;
    }
    .col-side { gap: 16px; }
  }
  @media (max-width: 640px) {
    .home-dashboard { padding: 20px 12px 40px; }
    .hero-banner { padding: 20px 18px 18px; border-radius: 14px; margin-bottom: 18px; }
    .hero-greeting { font-size: 22px; }
    .hero-char-avatar { width: 36px; height: 36px; }
    .hero-char-line { font-size: 12px; }
    .continue-card { padding: 20px; }
    .quick-actions { grid-template-columns: 1fr; }
    .action-icon-wrap { width: 40px; height: 40px; border-radius: 10px; }
    .continue-stats { gap: 20px; }
    .activity-row { padding: 10px 14px; }
    .announce-item { padding: 12px 14px; }
  }

  /* ── 快捷卡片角色头像 ── */
  .action-char-thumb {
    width: 42px;
    height: 42px;
    border-radius: 50%;
    object-fit: cover;
    object-position: top center;
    flex-shrink: 0;
    background: rgba(200,200,200,0.1);
  }

  /* ── 学习建议角色 ── */
  .next-step-intro-row {
    display: flex;
    align-items: flex-start;
    gap: 10px;
  }
  .next-step-char-avatar {
    width: 32px;
    height: 32px;
    border-radius: 50%;
    object-fit: cover;
    object-position: top center;
    flex-shrink: 0;
    margin-top: 2px;
  }

</style>
