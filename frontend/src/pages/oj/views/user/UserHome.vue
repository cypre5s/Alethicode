<template>
  <div class="dash-layout">
    <div class="g-tooltip" ref="tooltip"></div>
    <div class="toast-container" ref="toastContainer"></div>

    <div class="dash-wrap">

      <!-- ═══ Hero ═══ -->
      <div class="hero card" ref="hero">
        <div class="h-profile">
          <div class="h-avatar ripple-host"
            :data-tip="'点击查看全部资料'"
            :style="profile.avatar ? {backgroundImage:'url('+profile.avatar+')',backgroundSize:'cover'} : {}"
            @click="ripple($event)">
            <span v-if="!profile.avatar">{{ profile.user ? profile.user.username.charAt(0).toUpperCase() : 'U' }}</span>
          </div>
          <div>
            <div class="h-name">{{ profile.user ? profile.user.username : 'User' }}</div>
            <div class="h-sub" v-if="profile.school">{{ profile.school }}</div>
            <div class="edit-btn ripple-host" v-if="isSelf" @click="ripple($event); goSetting()">✏ 编辑资料</div>
          </div>
        </div>
        <div class="h-ring">
          <div class="ring-wrap" :data-tip="'通过率：AC ' + acceptedSubmissionCount + ' / 总提交 ' + (profile.submission_number||0)">
            <svg width="80" height="80" viewBox="0 0 80 80">
              <circle cx="40" cy="40" r="35" fill="none" stroke="#f0f2f5" stroke-width="7"/>
              <circle ref="ringWa" cx="40" cy="40" r="35" fill="none" stroke="#fbbc04" stroke-width="7"
                stroke-linecap="round" :stroke-dasharray="ringC" :stroke-dashoffset="ringC"
                style="transform:rotate(-90deg);transform-origin:center;transition:stroke-dashoffset 1.4s .2s cubic-bezier(.4,0,.2,1)"/>
              <circle ref="ringAc" cx="40" cy="40" r="35" fill="none" stroke="#34a853" stroke-width="7"
                stroke-linecap="round" :stroke-dasharray="ringC" :stroke-dashoffset="ringC"
                style="transform:rotate(-90deg);transform-origin:center;transition:stroke-dashoffset 1.4s cubic-bezier(.4,0,.2,1)"/>
            </svg>
            <div class="ring-center">
              <div class="ring-pct" ref="ringPct">0%</div>
              <div class="ring-lbl">AC 率</div>
            </div>
          </div>
          <div class="ring-legend">
            <div class="ring-leg-item" :data-tip="'AC 提交 ' + acceptedSubmissionCount + ' 次'">
              <span class="ring-dot" style="background:#34a853"></span>
              <span class="ring-leg-n">{{ acceptedSubmissionCount }}</span>
              <span class="ring-leg-l"> 通过</span>
            </div>
            <div class="ring-leg-item">
              <span class="ring-dot" style="background:#fbbc04"></span>
              <span class="ring-leg-n">{{ waCount }}</span>
              <span class="ring-leg-l"> WA/TLE</span>
            </div>
            <div class="ring-leg-item">
              <span class="ring-dot" style="background:#e8eaed"></span>
              <span class="ring-leg-n">{{ profile.submission_number || 0 }}</span>
              <span class="ring-leg-l"> 总提交</span>
            </div>
          </div>
        </div>
        <div class="h-stats">
          <div class="stats-row">
            <div class="stat-pill ripple-host" @click="ripple($event)">
              <div class="stat-n" style="color:#1a73e8" ref="statAc">0</div>
              <div class="stat-l">已解题目</div>
            </div>
            <div class="stat-pill ripple-host" @click="ripple($event)">
              <div class="stat-n" ref="statSub">0</div>
              <div class="stat-l">总提交</div>
            </div>
          </div>
          <div class="stats-row">
            <div class="stat-pill diff-pill" v-for="d in diffItems" :key="d.key">
              <div class="stat-n" :style="{color: d.color}">{{ d.solved }}</div>
              <div class="stat-l">{{ d.label }}</div>
            </div>
          </div>
        </div>
      </div>

      <!-- ═══ 主区：左(热力图+KC) ║ 右(标签进度+复习+错题) ═══ -->
      <div class="main-grid">
        <div class="card" ref="mainVisualCard">
          <div class="card-head">
            过去一年提交记录
            <span class="card-hint">{{ heatmapData ? heatmapData.total_ac : 0 }} 次</span>
          </div>
          <div class="hm-wrap">
            <PracticeHeatmap :heatmapData="heatmapData" :loading="heatmapLoading"
              :error="heatmapError" :lastUpdated="lastUpdated" @retry="refreshHeatmap"/>
          </div>
          <div class="card-head" style="border-top:1px solid #f3f4f6;padding-top:14px;margin-top:2px">
            <div class="viz-tabs">
              <span class="viz-tab viz-tab-active">知识星图</span>
            </div>
            <div class="viz-controls">
              <div v-if="visibleLanguagePacks.length > 0" class="lp-selector">
                <label class="lp-label" for="user-home-language-pack">课程内容包</label>
                <select
                  id="user-home-language-pack"
                  v-model.number="selectedLanguagePackId"
                  class="lp-select"
                  :disabled="languagePackLoading"
                  @change="onLanguagePackChange"
                >
                  <option v-for="pack in visibleLanguagePacks" :key="pack.id" :value="Number(pack.id)">
                    {{ pack.name }} v{{ pack.version }}
                  </option>
                </select>
              </div>
              <span v-else class="lp-empty">未绑定课程内容包</span>
              <span class="card-action" @click="loadKnowledgeGraph()">刷新</span>
            </div>
          </div>
          <div class="card-body">
            <template v-if="!selectedLanguagePackId">
              <div class="empty-hint">当前未绑定课程内容包，暂无法展示知识分析</div>
            </template>
            <template v-else>
              <KnowledgeStarMap
                :graphData="knowledgeGraphData"
                :loading="knowledgeGraphLoading"
                :snapshotMastery="snapshotMastery"
                @node-click="onStarMapNodeClick"
                @node-dblclick="onStarMapNodeDblClick"
                @snapshot-request="onSnapshotRequest"
              />
            </template>
          </div>

          <StarMapDetailPanel
            :visible="starMapDetailVisible"
            :kcDetail="starMapKcDetail"
            @close="starMapDetailVisible = false"
            @prereq-click="onPrereqClick"
          />
        </div>

        <!-- 右：标签进度 + 今日待复习 + 最近错题 -->
        <div class="side-stack" ref="sideStack">
          <div class="card tag-progress-card">
            <div class="card-head">
              标签进度
              <span class="card-hint">{{ tagProgress.length }} 个标签</span>
            </div>
            <div class="card-body tag-progress-body">
              <div v-if="tagProgressLoading" class="skel-list">
                <div class="skel-bar" v-for="i in 5" :key="'tp'+i"></div>
              </div>
              <div v-else-if="tagProgress.length === 0" class="empty-hint">暂无标签数据</div>
              <div v-else class="tag-list">
                <div v-for="tag in tagProgress" :key="tag.rawName" class="tag-row"
                  @click="goTagFilter(tag.rawName)">
                  <div class="tag-label">
                    <span class="tag-name">{{ tag.displayName }}</span>
                    <span class="tag-ratio">{{ tag.solved }}/{{ tag.total }}</span>
                  </div>
                  <div class="tag-bar-bg">
                    <div class="tag-bar-fill" :style="{ width: tag.total ? (tag.solved / tag.total * 100) + '%' : '0%' }"></div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div class="card bc-card">
            <div class="bc-half">
              <div class="card-head">
                今日待复习
                <span class="card-hint">{{ reviewDueItems.length }} 题</span>
              </div>
              <div class="card-body bc-body">
                <div v-if="reviewDueLoading" class="skel-list">
                  <div class="skel-bar" v-for="i in 3" :key="'rd'+i"></div>
                </div>
                <div v-else-if="reviewDueItems.length === 0" class="empty-hint">今日无待复习</div>
                <div v-else v-for="item in reviewDueItems" :key="'rd-'+(item.error_taxonomy||item.kc_id)" class="due-item ripple-host"
                  @click="ripple($event); goReviewKc(item)">
                  <div class="due-main">
                    <span v-if="item.problem_key" class="due-key">{{ item.problem_key }}</span>
                    <span class="due-kc">{{ item.kc_name }}</span>
                  </div>
                  <span class="due-state">{{ item.wrong_count > 0 ? ('错 ' + item.wrong_count + ' 次') : '去复习' }}</span>
                  <span class="due-go">›</span>
                </div>
              </div>
            </div>
            <div class="bc-half">
              <div class="card-head" style="border-top:1px solid #f3f4f6">
                最近错题
                <span class="card-hint">{{ recentWrong.length }} 题</span>
              </div>
              <div class="card-body bc-body">
                <div v-if="recentWrongLoading" class="skel-list">
                  <div class="skel-bar" v-for="i in 3" :key="'rw'+i"></div>
                </div>
                <div v-else-if="recentWrong.length === 0" class="empty-hint">近期无错题</div>
                <div v-else v-for="item in recentWrong" :key="'rw-'+item.submission_id" class="wrong-item ripple-host"
                  @click="ripple($event); goProblem(item.problem_key)">
                  <div class="sub-bar bar-wa"></div>
                  <div class="wrong-info">
                    <div class="wrong-id">{{ item.problem_key }}</div>
                    <div class="wrong-title">{{ item.title }}</div>
                  </div>
                  <span class="verdict v-wa">{{ item.result_label }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- ═══ 下方：最近提交 ║ 我的易错点 ═══ -->
      <div class="bottom-grid">
        <div class="card">
          <div class="card-head">
            最近提交
            <span class="card-action" @click="goSubmission">所有提交 →</span>
          </div>
          <div class="card-body">
            <div v-if="problems.length === 0" class="empty-hint">暂无数据</div>
            <div v-for="problem in problems" :key="problem.id" class="sub-item ripple-host"
              @click="ripple($event); goProblem(problem._id)">
              <div class="sub-bar bar-ac"></div>
              <div class="sub-info">
                <div class="sub-id">{{ problem._id }}</div>
                <div class="sub-name">{{ problem.title }}</div>
                <div class="sub-time">{{ localtime(problem.pass_time) }}</div>
              </div>
              <span class="verdict v-ac">AC</span>
            </div>
          </div>
        </div>

        <div class="card" v-if="isSelf">
          <div class="card-head">
            我的易错点
            <span class="card-hint">共 {{ misconceptions.length }} 项</span>
          </div>
          <div class="card-body">
            <div v-if="misconceptionsLoading" class="skel-list">
              <div class="skel-bar" v-for="i in 3" :key="'mc'+i"></div>
            </div>
            <div v-else-if="misconceptions.length === 0" class="empty-hint">暂无易错点记录</div>
            <div v-else v-for="mc in misconceptions" :key="mc.id" class="mc-item">
              <div class="mc-header">
                <span class="mc-name">{{ mc.misconception_name || mc.description || '待标注易错点' }}</span>
                <span class="mc-count">触发 {{ mc.trigger_count || 0 }} 次</span>
              </div>
              <div class="mc-detail" v-if="mc.correction_hint">{{ mc.correction_hint }}</div>
              <div class="mc-time" v-if="mc.last_triggered_at">最近触发：{{ localtime(mc.last_triggered_at) }}</div>
              <div class="mc-kc" v-if="mc.kc_name">
                <span class="mc-kc-tag">{{ mc.kc_name }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

    </div>
  </div>
</template>

<script>
  import { mapActions } from 'vuex'
  import { utcToLocal } from '@/utils/time'
  import api from '@oj/api'
  import PracticeHeatmap from '@oj/components/skillProfile/PracticeHeatmap'
  import KnowledgeStarMap from '@oj/components/skillProfile/KnowledgeStarMap'
  import StarMapDetailPanel from '@oj/components/skillProfile/StarMapDetailPanel'
  import { normalizeDisplayTags } from '@oj/utils/problemTagView'
  export default {
    name: 'UserHome',
    components: { PracticeHeatmap, KnowledgeStarMap, StarMapDetailPanel },
    data () {
      return {
        username: '',
        profile: {},
        problems: [],
        heatmapData: null,
        heatmapLoading: true,
        heatmapError: false,
        lastUpdated: null,
        pollingTimer: null,
        _onWindowFocus: null,
        _sideStackResizeObserver: null,
        tagProgress: [],
        tagProgressLoading: false,
        reviewDueItems: [],
        reviewDueLoading: false,
        recentWrong: [],
        recentWrongLoading: false,
        misconceptions: [],
        misconceptionsLoading: false,
        knowledgeGraphData: null,
        knowledgeGraphLoading: false,
        snapshotMastery: null,
        visibleLanguagePacks: [],
        selectedLanguagePackId: null,
        languagePackLoading: false,
        starMapDetailVisible: false,
        starMapKcDetail: {
          kc: { id: 0, name: '', chapter: '', description: '' },
          mastery: { p_mastery: 0, update_count: 0 },
          problems: [],
          prerequisites: [],
          active_misconceptions: [],
          mastery_history: []
        }
      }
    },
    computed: {
      isSelf () { return this.username === this.$store.getters.user.username },
      acceptedSubmissionCount () {
        return this.profile.accepted_submission_number != null
          ? this.profile.accepted_submission_number
          : ((this.heatmapData && this.heatmapData.total_ac != null)
              ? this.heatmapData.total_ac
          : (this.profile.accepted_number || 0)
            )
      },
      waCount () {
        const total = this.profile.submission_number || 0
        return Math.max(total - this.acceptedSubmissionCount, 0)
      },
      submissionRate () {
        const s = this.profile.submission_number || 1
        return ((this.acceptedSubmissionCount || 0) / s * 100).toFixed(1)
      },
      solvedStats () {
        const d = this.profile.solved_by_difficulty || {}
        return { easy: d.Low || 0, medium: d.Mid || 0, hard: d.High || 0 }
      },
      diffItems () {
        return [
          { key: 'easy', label: '简单', color: '#34a853', solved: this.solvedStats.easy },
          { key: 'mid', label: '中等', color: '#fbbc04', solved: this.solvedStats.medium },
          { key: 'hard', label: '困难', color: '#ea4335', solved: this.solvedStats.hard }
        ]
      },
      ringC () { return (2 * Math.PI * 35).toFixed(1) }
    },
    mounted () {
      this.init()
      this._setupTooltip()
      this.$nextTick(() => this._setupSideStackSync())
      this.pollingTimer = setInterval(() => {
        if (!this.username) return
        this.refreshHeatmap(true)
        this.refreshProfileStats()
      }, 5000)
      this._onWindowFocus = () => {
        if (!this.username) return
        this.refreshHeatmap(true)
        this.refreshProfileStats()
      }
      window.addEventListener('focus', this._onWindowFocus)
    },
    beforeUnmount () {
      if (this.pollingTimer) clearInterval(this.pollingTimer)
      if (this._onWindowFocus) window.removeEventListener('focus', this._onWindowFocus)
      if (this._ttCleanup) this._ttCleanup()
      this._cleanupSideStackSync()
    },
    methods: {
      ...mapActions(['changeDomTitle']),
      localtime: utcToLocal,
      normalizeProfilePayload (payload) {
        if (!payload || typeof payload !== 'object') {
          return {}
        }
        if (payload.user && typeof payload.user === 'object') {
          return payload
        }
        if (!payload.id) {
          return payload
        }
        return Object.assign({}, payload, {
          user: {
            id: payload.id,
            username: payload.username,
            email: payload.email,
            admin_type: payload.admin_type,
            problem_permission: payload.problem_permission
          }
        })
      },
      normalizeDueItems (items) {
        const list = Array.isArray(items) ? items : []
        return list.map(item => {
          const kcName = item.label || item.title || item.kc_name || item.error_taxonomy || ''
          const wrongCount = item.wrong_count != null ? item.wrong_count : (item.recent_wrong_count || 0)
          return Object.assign({}, item, { kc_name: kcName, problem_key: '', wrong_count: wrongCount })
        })
      },
      localizeMisconceptionName (rawName) {
        const key = (rawName || '').trim()
        if (!key) return ''
        const nameMap = {
          input_returns_int: '输入读取方式错误',
          misconception_detected_ast: '代码结构易错点',
          preflight_go_edit: '提交前命中易错点（已返回修改）',
          preflight_force_submit: '提交前命中易错点（仍继续提交）',
          misconception: '学习易错点'
        }
        if (nameMap[key]) {
          return nameMap[key]
        }
        if (key.includes('_')) {
          return key.replace(/_/g, ' ')
        }
        return key
      },
      isTelemetryEventName (rawName) {
        const key = (rawName || '').trim()
        const telemetryEvents = new Set([
          'problem_opened',
          'problem_closed',
          'submission_attempt',
          'code_edit_summary',
          'submission',
          'kc_review',
          'kc_practice',
          'problem_guide_requested'
        ])
        return telemetryEvents.has(key)
      },
      normalizeMisconceptions (items) {
        const list = Array.isArray(items) ? items : []
        const merged = {}
        list.forEach((item, idx) => {
          const rawName = (item && (item.misconception_name || item.description || item.kc_name || '')).trim() || '待标注易错点'
          if (this.isTelemetryEventName(rawName)) {
            return
          }
          const name = this.localizeMisconceptionName(rawName) || '待标注易错点'
          if (!merged[name]) {
            merged[name] = Object.assign({}, item, {
              id: item && item.id ? item.id : ('mc-' + idx),
              misconception_name: name,
              trigger_count: Number(item && item.trigger_count) || 0
            })
          } else {
            merged[name].trigger_count += Number(item && item.trigger_count) || 0
            if (!merged[name].correction_hint && item && item.correction_hint) {
              merged[name].correction_hint = item.correction_hint
            }
            if (!merged[name].kc_name && item && item.kc_name) {
              merged[name].kc_name = item.kc_name
            }
          }
        })
        return Object.values(merged).sort((a, b) => (b.trigger_count || 0) - (a.trigger_count || 0))
      },
      _syncSideStackHeight () {
        this.$nextTick(() => {
          const sideStack = this.$refs.sideStack
          const mainVisualCard = this.$refs.mainVisualCard
          if (!sideStack || !mainVisualCard) return
          if (window.matchMedia('(max-width: 860px)').matches) {
            sideStack.style.height = 'auto'
            return
          }
          const targetHeight = Math.round(mainVisualCard.getBoundingClientRect().height)
          if (targetHeight > 0) sideStack.style.height = targetHeight + 'px'
        })
      },
      _setupSideStackSync () {
        this._syncSideStackHeight()
        const mainVisualCard = this.$refs.mainVisualCard
        if (!mainVisualCard) return
        if (typeof ResizeObserver !== 'undefined') {
          if (this._sideStackResizeObserver) this._sideStackResizeObserver.disconnect()
          this._sideStackResizeObserver = new ResizeObserver(() => this._syncSideStackHeight())
          this._sideStackResizeObserver.observe(mainVisualCard)
        }
        window.addEventListener('resize', this._syncSideStackHeight)
      },
      _cleanupSideStackSync () {
        window.removeEventListener('resize', this._syncSideStackHeight)
        if (this._sideStackResizeObserver) {
          this._sideStackResizeObserver.disconnect()
          this._sideStackResizeObserver = null
        }
      },

      ripple (e) {
        const h = e.currentTarget, r = h.getBoundingClientRect(), sz = Math.max(r.width, r.height) * 2
        const s = document.createElement('span')
        s.className = 'ripple-dot'
        Object.assign(s.style, { width: sz + 'px', height: sz + 'px', left: (e.clientX - r.left - sz / 2) + 'px', top: (e.clientY - r.top - sz / 2) + 'px' })
        h.appendChild(s)
        s.addEventListener('animationend', () => s.remove())
      },
      _setupTooltip () {
        const tt = this.$refs.tooltip; if (!tt) return
        const show = e => { const el = e.target.closest('[data-tip]'); if (!el) return; tt.textContent = el.dataset.tip || ''; tt.style.opacity = '1'; mv(e) }
        const mv = e => { tt.style.left = (e.clientX - tt.offsetWidth / 2) + 'px'; tt.style.top = (e.clientY - tt.offsetHeight - 12) + 'px' }
        const hide = e => { if (e.target.closest('[data-tip]') && !(e.relatedTarget && e.relatedTarget.closest && e.relatedTarget.closest('[data-tip]'))) tt.style.opacity = '0' }
        const move = e => { if (tt.style.opacity === '1') mv(e) }
        document.addEventListener('mouseover', show)
        document.addEventListener('mousemove', move)
        document.addEventListener('mouseout', hide)
        this._ttCleanup = () => {
          document.removeEventListener('mouseover', show)
          document.removeEventListener('mousemove', move)
          document.removeEventListener('mouseout', hide)
        }
      },
      showToast (icon, msg) {
        const c = this.$refs.toastContainer; if (!c) return
        const t = document.createElement('div'); t.className = 'toast-msg'
        const iconEl = document.createElement('span')
        iconEl.className = 'toast-icon'
        iconEl.textContent = icon
        t.appendChild(iconEl)
        t.appendChild(document.createTextNode(msg || ''))
        c.appendChild(t)
        setTimeout(() => { t.classList.add('out'); t.addEventListener('animationend', () => t.remove()) }, 2400)
      },
      _countUp (el, target, dur) {
        if (!el) return; dur = dur || 1100; const s = performance.now()
        const step = n => { const t = Math.min((n - s) / dur, 1); el.textContent = Math.round(target * (1 - Math.pow(1 - t, 3))); if (t < 1) requestAnimationFrame(step); else el.textContent = target }
        requestAnimationFrame(step)
      },
      _animateRing () {
        const C = 2 * Math.PI * 35
        const totalSubmission = this.profile.submission_number || 0
        const sub = totalSubmission > 0 ? totalSubmission : 1
        const ac = this.acceptedSubmissionCount || 0
        const solved = this.profile.accepted_number || 0
        this.$nextTick(() => {
          if (this.$refs.ringAc) this.$refs.ringAc.style.strokeDashoffset = totalSubmission > 0 ? C * (1 - ac / sub) : C
          if (this.$refs.ringWa) this.$refs.ringWa.style.strokeDashoffset = totalSubmission > 0 ? 0 : C
          const p = this.$refs.ringPct; if (p) { const f = parseFloat(this.submissionRate); let v = 0; const iv = setInterval(() => { v = Math.min(v + f / 40, f); p.textContent = v.toFixed(1) + '%'; if (v >= f) { clearInterval(iv); p.textContent = f.toFixed(1) + '%' } }, 25) }
          this._countUp(this.$refs.statAc, solved)
          this._countUp(this.$refs.statSub, totalSubmission)
        })
      },

      init () {
        const ru = this.$route.query.username, su = this.$store.getters.user && this.$store.getters.user.username
        this.username = ru || su || ''
        api.getUserInfo(this.username).then(res => {
          const normalized = this.normalizeProfilePayload(res && res.data ? res.data.data : {})
          if (normalized.user && normalized.user.username) {
            this.username = normalized.user.username
            this.changeDomTitle({title: normalized.user.username})
          }
          this.profile = normalized
          this.problems = this.profile.recent_passed || []
          this.$nextTick(() => this._animateRing())
          this.loadHeatmap()
          this.loadVisibleLanguagePacks().finally(() => {
            this.loadTagProgress()
            this.loadKnowledgeGraph()
          })
          this.loadReviewDue()
          this.loadRecentWrong()
          if (this.isSelf) {
            this.loadMisconceptions()
          }
        }).catch(() => {
          this.heatmapError = true; this.heatmapLoading = false
        })
      },
      loadVisibleLanguagePacks () {
        if (!this.profile || !this.profile.user) {
          return Promise.resolve()
        }
        this.languagePackLoading = true
        return api.getVisibleLanguagePackList().then(res => {
          const data = res && res.data ? res.data.data : []
          const packs = Array.isArray(data) ? data : []
          this.visibleLanguagePacks = packs
          if (packs.length === 0) {
            this.selectedLanguagePackId = null
            return
          }
          const selected = Number(this.selectedLanguagePackId)
          const hasSelected = packs.some(pack => Number(pack.id) === selected)
          if (!hasSelected) {
            this.selectedLanguagePackId = Number(packs[0].id)
          }
        }).catch(() => {
          this.visibleLanguagePacks = []
          this.selectedLanguagePackId = null
        }).finally(() => {
          this.languagePackLoading = false
        })
      },
      refreshHeatmap (silent) { if (!silent) { this.heatmapLoading = true; this.heatmapError = false }; this.loadHeatmap(silent) },
      refreshProfileStats () {
        api.getUserInfo(this.username).then(res => {
          const fresh = this.normalizeProfilePayload(res && res.data ? res.data.data : {})
          if (!fresh.user) return
          this.profile = {
            ...this.profile,
            accepted_number: fresh.accepted_number,
            accepted_submission_number: fresh.accepted_submission_number,
            submission_number: fresh.submission_number,
            solved_by_difficulty: fresh.solved_by_difficulty
          }
          this.problems = fresh.recent_passed || this.problems
          this._animateRing()
        }).catch(() => {})
      },
      loadHeatmap (silent) {
        if (!this.profile || !this.profile.user) return
        api.getPracticeHeatmap(this.profile.user.id).then(res => {
          if (!res.data.error) { this.heatmapData = (res.data.data && res.data.data.heatmap_data) || null; this.heatmapError = false; this.lastUpdated = new Date() } else this.heatmapError = true
          if (!silent) this.heatmapLoading = false
        }).catch(() => { if (!silent) { this.heatmapLoading = false; if (!this.heatmapData) this.heatmapError = true } })
      },
      loadTagProgress () {
        if (!this.profile || !this.profile.user || !this.selectedLanguagePackId) {
          this.tagProgress = []
          this.tagProgressLoading = false
          return
        }
        this.tagProgressLoading = true
        api.getTagProgress(this.profile.user.id, {
          language_pack_id: this.selectedLanguagePackId
        }).then(res => {
          const data = (res.data && res.data.data) || {}
          const rawTags = Array.isArray(data.tags) ? data.tags : (Array.isArray(data) ? data : [])
          const visibleTags = normalizeDisplayTags(rawTags.map(item => item && item.name))
          const displayMap = new Map(visibleTags.map(item => [item.rawName, item.displayName]))
          this.tagProgress = rawTags
            .map(item => ({
              rawName: item && item.name ? String(item.name).trim() : '',
              solved: Number(item && item.solved) || 0,
              total: Number(item && item.total) || 0
            }))
            .filter(item => item.rawName && displayMap.has(item.rawName))
            .map(item => ({
              rawName: item.rawName,
              displayName: displayMap.get(item.rawName),
              solved: item.solved,
              total: item.total
            }))
          this.tagProgressLoading = false
        }).catch(() => { this.tagProgress = []; this.tagProgressLoading = false })
      },
      loadReviewDue () {
        if (!this.profile || !this.profile.user || !this.isSelf) return
        this.reviewDueLoading = true
        api.getReviewDue(5, this.selectedLanguagePackId).then(res => {
          const d = (res.data && res.data.data) || {}
          this.reviewDueItems = this.normalizeDueItems((d.due_reviews || []).slice(0, 5))
          this.reviewDueLoading = false
        }).catch(() => { this.reviewDueItems = []; this.reviewDueLoading = false })
      },
      loadRecentWrong () {
        if (!this.profile || !this.profile.user) return
        this.recentWrongLoading = true
        api.getRecentWrong(this.profile.user.id, 5).then(res => {
          this.recentWrong = (res.data && res.data.data && res.data.data.items) || []
          this.recentWrongLoading = false
        }).catch(() => { this.recentWrong = []; this.recentWrongLoading = false })
      },
      goTagFilter (tagName) {
        const query = { tag: tagName }
        if (this.selectedLanguagePackId) {
          query.language_pack_id = String(this.selectedLanguagePackId)
        }
        this.$router.push({ name: 'problem-list', query })
      },
      goReviewKc (item) {
        if (!item || !item.problem_key) return
        this.$router.push({ name: 'problem-details', params: { problemID: item.problem_key } })
      },
      loadMisconceptions () {
        this.misconceptionsLoading = true
        api.getMyMisconceptions().then(res => {
          const raw = (res.data && res.data.data && res.data.data.misconceptions) || []
          this.misconceptions = this.normalizeMisconceptions(raw)
          this.misconceptionsLoading = false
        }).catch(() => {
          this.misconceptions = []
          this.misconceptionsLoading = false
        })
      },
      loadKnowledgeGraph () {
        if (!this.profile || !this.profile.user || !this.selectedLanguagePackId) {
          this.knowledgeGraphData = null
          this.knowledgeGraphLoading = false
          this.snapshotMastery = null
          return
        }
        this.knowledgeGraphLoading = true
        this.snapshotMastery = null
        api.getKnowledgeGraph(this.profile.user.id, this.selectedLanguagePackId).then(res => {
          this.knowledgeGraphData = (res.data && res.data.data) || null
          this.knowledgeGraphLoading = false
        }).catch(() => {
          this.knowledgeGraphData = null
          this.knowledgeGraphLoading = false
        })
      },
      onStarMapNodeClick (node) {
        if (!this.profile || !this.profile.user || !this.selectedLanguagePackId) return
        api.getKCDetail(node.id, this.profile.user.id, this.selectedLanguagePackId).then(res => {
          this.starMapKcDetail = (res.data && res.data.data) || this.starMapKcDetail
          this.starMapDetailVisible = true
        }).catch(() => {
          if (this.$message && typeof this.$message.warning === 'function') {
            this.$message.warning('该知识点暂无详情数据')
          }
        })
      },
      onStarMapNodeDblClick (node) {
        if (node.first_problem_id) {
          this.goProblem(node.first_problem_id)
        }
      },
      onSnapshotRequest (dateStr) {
        if (!this.profile || !this.profile.user || !this.selectedLanguagePackId) return
        if (!dateStr) {
          this.snapshotMastery = null
          return
        }
        api.getKnowledgeGraphSnapshot(this.profile.user.id, dateStr, this.selectedLanguagePackId).then(res => {
          const data = (res.data && res.data.data) || {}
          this.snapshotMastery = data.mastery_map || null
        }).catch(() => {
          this.snapshotMastery = null
        })
      },
      onPrereqClick (kcId) {
        if (!this.profile || !this.profile.user || !this.selectedLanguagePackId) return
        api.getKCDetail(kcId, this.profile.user.id, this.selectedLanguagePackId).then(res => {
          this.starMapKcDetail = (res.data && res.data.data) || this.starMapKcDetail
        }).catch(() => {
          if (this.$message && typeof this.$message.warning === 'function') {
            this.$message.warning('该知识点暂无详情数据')
          }
        })
      },
      onLanguagePackChange () {
        this.starMapDetailVisible = false
        this.snapshotMastery = null
        this.knowledgeGraphData = null
        this.loadTagProgress()
        this.loadKnowledgeGraph()
      },
      goProblem (id) { this.$router.push({name: 'problem-details', params: {problemID: id}}) },
      goSetting () { this.$router.push({name: 'profile-setting'}) },
      goSubmission () { this.$router.push({name: 'submission-list', query: this.isSelf ? {myself: '1'} : {username: this.username, myself: '0'}}) }
    },
    watch: { '$route' (n, o) { if (n !== o) this.init() } }
  }
</script>

<style lang="less" scoped>
.dash-layout {
  background: #f4f6fb; min-height: 100vh; padding: 22px 16px 60px;
  font-family: -apple-system,'PingFang SC','Microsoft YaHei',sans-serif; font-size: 13px; color: #1a1d2e;
}
.dash-wrap { max-width: 1260px; margin: 0 auto; }

.card {
  background: #fff; border: 1px solid #e8eaed; border-radius: 12px;
  box-shadow: 0 1px 4px rgba(0,0,0,.04); overflow: hidden;
}
.card-head {
  display: flex; align-items: center; justify-content: space-between;
  padding: 15px 18px 0; font-size: 13px; font-weight: 600; color: #1a1d2e;
}
.card-hint { font-size: 11px; color: #aaa; font-weight: 400; }
.card-action {
  font-size: 11px; color: #1a73e8; cursor: pointer; padding: 3px 10px; border-radius: 6px;
  border: 1px solid transparent; transition: all .15s; font-weight: 500;
  &:hover { border-color: #d2e3fc; background: #f0f6ff; }
}
.card-body { padding: 12px 18px 16px; }


/* ═══ Hero ═══ */
.hero {
  display: grid; grid-template-columns: 200px 1fr auto; margin-bottom: 18px;
  @media (max-width: 860px) { grid-template-columns: 1fr; }
}
.h-profile { padding: 22px 20px; display: flex; align-items: center; gap: 14px; border-right: 1px solid #f0f2f5; }
.h-avatar {
  width: 56px; height: 56px; border-radius: 50%; flex-shrink: 0;
  background: linear-gradient(135deg,#4f7cff,#a78bfa);
  display: flex; align-items: center; justify-content: center;
  color: #fff; font-size: 22px; font-weight: 700;
  box-shadow: 0 0 0 3px #fff, 0 0 0 5px #e8f0fe; cursor: pointer; transition: transform .25s;
  &:hover { transform: scale(1.08) rotate(-3deg); }
}
.h-name { font-size: 16px; font-weight: 700; margin-bottom: 2px; }
.h-sub { font-size: 11px; color: #888; margin-bottom: 8px; }
.edit-btn {
  display: inline-flex; align-items: center; gap: 5px; font-size: 11px; padding: 4px 10px;
  border-radius: 6px; background: #f8f9ff; color: #1a73e8; cursor: pointer;
  border: 1px solid #d2e3fc; transition: all .15s; font-weight: 500;
  &:hover { background: #1a73e8; color: #fff; border-color: #1a73e8; }
}
.h-ring { padding: 18px 28px; display: flex; align-items: center; gap: 20px; border-right: 1px solid #f0f2f5; }
.ring-wrap { position: relative; width: 80px; height: 80px; flex-shrink: 0; }
.ring-center { position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; }
.ring-pct { font-size: 14px; font-weight: 800; color: #1a1d2e; line-height: 1; }
.ring-lbl { font-size: 9px; color: #888; margin-top: 2px; }
.ring-legend { display: flex; flex-direction: column; gap: 8px; }
.ring-leg-item { display: flex; align-items: center; gap: 6px; font-size: 12px; }
.ring-dot { width: 9px; height: 9px; border-radius: 50%; flex-shrink: 0; display: inline-block; }
.ring-leg-n { font-weight: 700; }
.ring-leg-l { color: #888; font-size: 10px; }
.h-stats { padding: 18px 24px; display: flex; flex-direction: column; justify-content: center; gap: 10px; }
.stats-row { display: flex; gap: 8px; }
.stat-pill {
  flex: 1; padding: 10px 14px; border-radius: 10px; background: #f8f9fb; border: 1px solid #eaecf0;
  text-align: center; cursor: default; transition: all .2s;
  &:hover { border-color: #d2e3fc; transform: translateY(-1px); box-shadow: 0 3px 8px rgba(0,0,0,.07); }
}
.stat-n { font-size: 20px; font-weight: 800; line-height: 1; }
.stat-l { font-size: 10px; color: #888; margin-top: 3px; }
.diff-pill .stat-n { font-size: 16px; }

/* ═══ Grids ═══ */
.main-grid { display: grid; grid-template-columns: 1fr 340px; gap: 18px; margin-bottom: 18px; @media(max-width:860px){grid-template-columns:1fr;} }
.main-grid > .card { align-self: start; }
.bottom-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 18px; @media(max-width:860px){grid-template-columns:1fr;} }
.hm-wrap { padding: 10px 18px 12px; }

/* ═══ 右侧堆叠 ═══ */
.side-stack { display: flex; flex-direction: column; gap: 18px; min-height: 0; }
.tag-progress-card { flex: 1; min-height: 0; display: flex; flex-direction: column; }
.tag-progress-body { flex: 1; overflow-y: auto; }
.tag-list { display: flex; flex-direction: column; gap: 10px; }
.tag-row {
  cursor: pointer; transition: all .15s; padding: 2px 0;
  &:hover .tag-name { color: #1a73e8; }
  &:hover .tag-bar-fill { filter: brightness(1.08); }
}
.tag-label { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
.tag-name { font-size: 12px; font-weight: 500; transition: color .15s; }
.tag-ratio { font-size: 10px; color: #999; font-weight: 600; }
.tag-bar-bg { height: 6px; border-radius: 3px; background: #f0f2f5; overflow: hidden; }
.tag-bar-fill {
  height: 100%; border-radius: 3px; transition: width .6s cubic-bezier(.4,0,.2,1), filter .15s;
  background: linear-gradient(90deg, #4f7cff, #34a853);
}

/* ═══ B+C 卡片 ═══ */
.bc-card { display: flex; flex-direction: column; flex: 1; min-height: 0; }
.bc-half { flex: 1; min-height: 0; display: flex; flex-direction: column; }
.bc-body { flex: 1; overflow-y: auto; }

.due-item {
  display: flex; align-items: center; gap: 8px; padding: 7px 0; border-bottom: 1px solid #f3f4f6;
  cursor: pointer; transition: all .15s;
  &:last-child { border-bottom: none; }
  &:hover { padding-left: 4px; }
  &:hover .due-go { opacity: 1; transform: translateX(0); }
}
.due-main { flex: 1; min-width: 0; display: flex; align-items: baseline; gap: 8px; }
.due-key { flex-shrink: 0; font-size: 11px; font-weight: 700; color: #1a73e8; font-family: var(--font-mono, monospace); }
.due-kc { flex: 1; min-width: 0; font-size: 12px; font-weight: 500; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.due-state { font-size: 11px; font-weight: 700; flex-shrink: 0; color: #1a73e8; }
.due-go { font-size: 12px; color: #1a73e8; flex-shrink: 0; opacity: 0; transform: translateX(-4px); transition: all .15s; }

.wrong-item {
  display: flex; align-items: center; gap: 8px; padding: 7px 0; border-bottom: 1px solid #f3f4f6;
  cursor: pointer; transition: all .15s;
  &:last-child { border-bottom: none; }
  &:hover { padding-left: 4px; }
}
.wrong-info { flex: 1; min-width: 0; }
.wrong-id { font-size: 10px; color: #1a73e8; }
.wrong-title { font-size: 12px; font-weight: 500; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.bar-wa { background: #ea4335; }
.v-wa { background: #fef2f2; color: #dc2626; border: 1px solid #fecaca; }

/* ═══ 最近提交 ═══ */
.sub-item {
  display: flex; align-items: center; gap: 10px; padding: 9px 0; border-bottom: 1px solid #f3f4f6;
  cursor: pointer; transition: all .15s;
  &:last-child { border-bottom: none; }
  &:hover { padding-left: 5px; }
  &:hover .verdict { background: #16a34a; color: #fff; }
}
.sub-bar { width: 3px; height: 30px; border-radius: 2px; flex-shrink: 0; }
.bar-ac { background: #34a853; }
.sub-info { flex: 1; min-width: 0; }
.sub-id { font-size: 10px; color: #1a73e8; }
.sub-name { font-size: 12px; font-weight: 500; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.sub-time { font-size: 10px; color: #ccc; margin-top: 1px; }
.verdict { font-size: 10px; font-weight: 700; padding: 2px 8px; border-radius: 5px; flex-shrink: 0; transition: all .15s; }
.v-ac { background: #f0fdf4; color: #16a34a; border: 1px solid #bbf7d0; }

/* ═══ Ripple ═══ */
.ripple-host { position: relative; overflow: hidden; }
.ripple-dot {
  position: absolute; border-radius: 50%; background: rgba(0,0,0,.08);
  transform: scale(0); animation: rpl .5s linear; pointer-events: none;
}
@keyframes rpl { to { transform: scale(4); opacity: 0; } }

/* ═══ Tooltip ═══ */
.g-tooltip {
  position: fixed; pointer-events: none; z-index: 9998;
  background: #1a1d2e; color: #fff; font-size: 11px; line-height: 1.5;
  padding: 6px 10px; border-radius: 7px; box-shadow: 0 4px 16px rgba(0,0,0,.18);
  opacity: 0; transition: opacity .15s; max-width: 200px; white-space: normal;
  &::after { content: ''; position: absolute; top: 100%; left: 50%; transform: translateX(-50%); border: 5px solid transparent; border-top-color: #1a1d2e; }
}

/* ═══ Toast ═══ */
.toast-container {
  position: fixed; bottom: 28px; left: 50%; transform: translateX(-50%);
  z-index: 9999; display: flex; flex-direction: column; gap: 8px; align-items: center; pointer-events: none;
}
.toast-msg {
  background: #1a1d2e; color: #fff; padding: 10px 18px; border-radius: 10px;
  font-size: 12px; font-weight: 500; white-space: nowrap; box-shadow: 0 6px 20px rgba(0,0,0,.2);
  animation: tin .3s ease; display: flex; align-items: center; gap: 8px;
}
.toast-msg.out { animation: tout .3s ease forwards; }
@keyframes tin  { from { opacity:0; transform:translateY(12px); } to { opacity:1; transform:none; } }
@keyframes tout { from { opacity:1; } to { opacity:0; transform:translateY(-8px); } }

/* ═══ Skeleton ═══ */
@keyframes shimmer { 0% { background-position: -400px 0; } 100% { background-position: 400px 0; } }
.skel-list { padding: 8px 0; }
.skel-bar {
  height: 38px; border-radius: 8px; margin-bottom: 6px;
  background: linear-gradient(90deg, #f0f2f5 25%, #e8eaed 50%, #f0f2f5 75%);
  background-size: 400px 100%; animation: shimmer 1.4s infinite;
}
.empty-hint { text-align: center; color: #aaa; padding: 28px 0; font-size: 12px; }

/* ═══ E.2 易错点 ═══ */
.mc-item {
  padding: 10px 0; border-bottom: 1px solid #f3f4f6;
  &:last-child { border-bottom: none; }
}
.mc-header { display: flex; align-items: center; justify-content: space-between; }
.mc-name { font-size: 13px; font-weight: 600; color: #1a1d2e; }
.mc-count { font-size: 11px; color: #ea4335; font-weight: 500; }
.mc-detail { font-size: 12px; color: #666; margin-top: 4px; line-height: 1.5; }
.mc-time { font-size: 11px; color: #8a8f99; margin-top: 2px; }
.mc-kc { margin-top: 4px; }
.mc-kc-tag {
  display: inline-block; font-size: 10px; padding: 1px 8px; border-radius: 10px;
  background: #f0f6ff; color: #1a73e8; border: 1px solid #d2e3fc;
}

/* ═══ Viz Tabs ═══ */
.viz-tabs {
  display: flex; gap: 2px; background: #f0f2f5; border-radius: 8px; padding: 2px;
}
.viz-controls {
  display: flex; align-items: center; gap: 10px;
}
.lp-selector {
  display: flex; align-items: center; gap: 8px;
}
.lp-label {
  font-size: 11px; color: #666; font-weight: 500;
}
.lp-select {
  height: 34px; min-width: 180px; border: 1px solid #d9dde5; border-radius: 8px;
  font-size: 12px; color: #1a1d2e; background: #fff; padding: 0 10px; cursor: pointer;
  &:focus {
    outline: none;
    border-color: #1a73e8;
    box-shadow: 0 0 0 2px rgba(26, 115, 232, .12);
  }
  &:disabled {
    cursor: not-allowed;
    color: #999;
    background: #f6f7fa;
  }
}
.lp-empty {
  font-size: 11px; color: #999;
}
.viz-tab {
  padding: 5px 14px; border-radius: 6px; font-size: 12px; font-weight: 500;
  color: #888; cursor: pointer; transition: all .15s; user-select: none;
  &:hover { color: #555; }
}
.viz-tab-active {
  background: #fff; color: #1a1d2e; box-shadow: 0 1px 3px rgba(0,0,0,.08);
}
</style>
