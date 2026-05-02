<template>
  <div class="login-page">
    <div class="bg-mesh"></div>
    <div class="bg-orb orb-1"></div>
    <div class="bg-orb orb-2"></div>
    <div class="bg-orb orb-3"></div>
    <canvas ref="particleCanvas" class="particle-canvas"></canvas>

    <div class="toasts" ref="toastContainer"></div>

    <div class="card-wrap">
      <!-- Left brand panel — snake cursor interaction -->
      <div class="brand-panel" ref="brandPanel" @mousemove="onSnakeMove" @mouseleave="onSnakeLeave">
        <canvas ref="snakeCanvas" class="snake-canvas" />
        <div class="brand-deco deco-1"></div>
        <div class="brand-deco deco-2"></div>
        <div class="brand-deco deco-3"></div>
        <div class="brand-top">
          <div class="brand-logo">
            <img class="brand-logo-ring" src="/logo.png" alt="Alethicode logo">
            <span class="brand-logo-text">Alethicode</span>
          </div>
          <div class="brand-headline">
            <span v-for="(c, i) in headlineLetters" :key="'h' + i" class="ltr">{{ c === ' ' ? '\u00A0' : c }}</span>
            <br>
            <span v-for="(c, i) in accentLetters" :key="'a' + i" class="ltr ltr-accent">{{ c }}</span>
          </div>
          <div class="brand-desc">先登录，再进入系统。使用统一账号完成做题、评测与学习分析。</div>
        </div>
        <div class="brand-tags">
          <div class="brand-tag tag-judge">Judge</div>
          <div class="brand-tag tag-track">Track</div>
          <div class="brand-tag tag-improve">Improve</div>
        </div>
      </div>

      <!-- Right form panel -->
      <div class="form-panel">
        <div class="form-top">
          <div class="form-top-brand">Alethicode</div>
          <div class="form-top-title">Sign in</div>
          <div class="step-dots">
            <div class="step-dot inactive"></div>
            <div class="step-dot active"></div>
          </div>
        </div>

        <div class="form-body">
          <div class="field">
            <div class="field-label">
              <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
              用户名
            </div>
            <div class="field-wrap">
              <span class="field-icon">
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
              </span>
              <input class="f-input" ref="inputUser"
                :class="{error: fieldErr.user}"
                v-model="formLogin.username" type="text"
                :placeholder="$t('m.LoginUsername')"
                @input="clearFieldErr('user')"
                @keydown.enter="handleLogin">
              <div class="field-underline"></div>
            </div>
            <div class="field-error" :class="{show: fieldErr.user}">
              <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
              <span>{{ fieldErr.userMsg }}</span>
            </div>
          </div>

          <div class="field">
            <div class="field-label">
              <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
              密码
            </div>
            <div class="field-wrap">
              <span class="field-icon">
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
              </span>
              <input class="f-input" ref="inputPass"
                :class="{error: fieldErr.pass}"
                v-model="formLogin.password"
                :type="pwVisible ? 'text' : 'password'"
                :placeholder="$t('m.LoginPassword')"
                @input="clearFieldErr('pass')"
                @keydown.enter="handleLogin">
              <div class="pw-toggle" @click="togglePw">
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" v-if="!pwVisible"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" v-else><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/><line x1="1" y1="1" x2="23" y2="23"/></svg>
              </div>
              <div class="field-underline"></div>
            </div>
            <div class="field-error" :class="{show: fieldErr.pass}">
              <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
              <span>{{ fieldErr.passMsg }}</span>
            </div>
          </div>

          <div class="field" v-if="tfaRequired">
            <div class="field-label">
              <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"/><line x1="12" y1="8" x2="12" y2="16"/><line x1="8" y1="12" x2="16" y2="12"/></svg>
              {{ $t('m.TFA_Code') }}
            </div>
            <div class="field-wrap">
              <span class="field-icon">
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"/><line x1="12" y1="8" x2="12" y2="16"/><line x1="8" y1="12" x2="16" y2="12"/></svg>
              </span>
              <input class="f-input" v-model="formLogin.tfa_code" type="text"
                :placeholder="$t('m.TFA_Code')" @keydown.enter="handleLogin">
              <div class="field-underline"></div>
            </div>
          </div>

          <div class="form-aux">
            <div class="remember-row" @click="rememberMe = !rememberMe">
              <div class="custom-check" :class="{checked: rememberMe}">
                <svg width="9" height="9" viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg>
              </div>
              <span class="remember-label">记住我</span>
            </div>
            <div class="forgot-link" @click="goResetPassword">{{ $t('m.Forget_Password') }}</div>
          </div>

          <button class="login-btn rip"
            :class="{loading: btnLoading, success: loginSuccess}"
            @click="handleLogin"
            :disabled="btnLoading || loginSuccess">
            <div class="btn-inner">
              <div class="btn-spinner"></div>
              <svg v-if="loginSuccess" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg>
              <span class="btn-text">{{ btnText }}</span>
            </div>
          </button>

          <div class="register-row" v-if="website.allow_register">
            还没有账号？<span class="register-link" @click="goRegister">立即注册</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
  import { mapGetters, mapActions } from 'vuex'
  import api from '@oj/api'

  const SNAKE_LEN = 14
  const LINK_DIST = 8
  const SCATTER_R = 72
  const SPRING_K = 0.11
  const DAMPING = 0.80

  export default {
    name: 'StandaloneLogin',
    data () {
      return {
        formLogin: { username: '', password: '', tfa_code: '' },
        tfaRequired: false,
        pwVisible: false,
        rememberMe: false,
        btnLoading: false,
        loginSuccess: false,
        btnText: '登录',
        fieldErr: {
          user: false,
          userMsg: '',
          pass: false,
          passMsg: ''
        },
        particles: [],
        animFrameId: null,
        headlineLetters: [...'Code with'],
        accentLetters: [...'focus.']
      }
    },
    computed: {
      ...mapGetters(['website', 'modalStatus'])
    },
    mounted () {
      this.initParticles()
      api.csrf().catch(() => {})

      // ── Snake init (non-reactive, all state on _snake* props) ──
      this._snake = Array.from({ length: SNAKE_LEN }, () => ({ x: 0, y: 0 }))
      this._letterData = []
      this._mouse = { x: 0, y: 0, active: false }
      this._snakeRaf = null

      this.$nextTick(() => {
        this._resizeSnakeCanvas()
        this._measureLetters()
        this._startSnakeLoop()

        const panel = this.$refs.brandPanel
        if (panel) {
          const cx = panel.offsetWidth / 2
          const cy = panel.offsetHeight * 0.6
          this._snake.forEach(seg => { seg.x = cx; seg.y = cy })
        }

        this._panelResizeObs = new ResizeObserver(() => {
          this._resizeSnakeCanvas()
          this._measureLetters()
        })
        this._panelResizeObs.observe(this.$refs.brandPanel)
      })
    },
    beforeUnmount () {
      if (this.animFrameId) cancelAnimationFrame(this.animFrameId)
      if (this._removeResizeListener) this._removeResizeListener()
      if (this._snakeRaf) cancelAnimationFrame(this._snakeRaf)
      if (this._panelResizeObs) this._panelResizeObs.disconnect()
    },
    methods: {
      ...mapActions(['changeModalStatus', 'getProfile']),

      /* ── Particle system ── */
      initParticles () {
        const cv = this.$refs.particleCanvas
        if (!cv) return
        const cx = cv.getContext('2d')
        const rnd = (a, b) => a + Math.random() * (b - a)
        const colors = ['#1a73e8', '#1a73e8', '#34a853', '#f59e0b']

        const resize = () => { cv.width = window.innerWidth; cv.height = window.innerHeight }
        resize()
        window.addEventListener('resize', resize)
        this._removeResizeListener = () => window.removeEventListener('resize', resize)

        for (let i = 0; i < 60; i++) {
          this.particles.push({
            x: rnd(0, cv.width),
            y: rnd(0, cv.height),
            r: rnd(1.2, 3.5),
            vx: rnd(-0.22, 0.22),
            vy: rnd(-0.18, 0.18),
            a: rnd(0.12, 0.42),
            c: colors[Math.floor(Math.random() * colors.length)]
          })
        }

        const draw = () => {
          cx.clearRect(0, 0, cv.width, cv.height)
          const pts = this.particles
          pts.forEach(p => {
            p.x += p.vx; p.y += p.vy
            if (p.x < 0) p.x = cv.width; if (p.x > cv.width) p.x = 0
            if (p.y < 0) p.y = cv.height; if (p.y > cv.height) p.y = 0
            cx.beginPath(); cx.arc(p.x, p.y, p.r, 0, Math.PI * 2)
            cx.fillStyle = p.c; cx.globalAlpha = p.a; cx.fill()
          })
          cx.globalAlpha = 1
          for (let i = 0; i < pts.length; i++) {
            for (let j = i + 1; j < pts.length; j++) {
              const dx = pts[i].x - pts[j].x
              const dy = pts[i].y - pts[j].y
              const d = Math.sqrt(dx * dx + dy * dy)
              if (d < 130) {
                cx.beginPath()
                cx.moveTo(pts[i].x, pts[i].y)
                cx.lineTo(pts[j].x, pts[j].y)
                cx.strokeStyle = '#1a73e8'
                cx.globalAlpha = (1 - d / 130) * 0.07
                cx.lineWidth = 0.8
                cx.stroke()
              }
            }
          }
          this.animFrameId = requestAnimationFrame(draw)
        }
        draw()
      },

      /* ── Toast ── */
      showToast (msg, type) {
        const tc = this.$refs.toastContainer
        if (!tc) return
        const t = document.createElement('div')
        t.className = 'toast'
        const d = document.createElement('div')
        d.className = 't-dot ' + (type || 'ok')
        t.appendChild(d)
        t.appendChild(document.createTextNode(msg))
        tc.appendChild(t)
        setTimeout(() => {
          t.classList.add('out')
          t.addEventListener('animationend', () => t.remove())
        }, 2800)
      },

      /* ── Field errors ── */
      clearFieldErr (key) {
        this.fieldErr[key] = false
        this.fieldErr[key + 'Msg'] = ''
      },
      showFieldErr (key, msg) {
        this.fieldErr[key] = true
        this.fieldErr[key + 'Msg'] = msg
        this.$nextTick(() => {
          const ref = key === 'user' ? this.$refs.inputUser : this.$refs.inputPass
          if (ref) ref.focus()
        })
      },

      /* ── Password toggle ── */
      togglePw () { this.pwVisible = !this.pwVisible },

      /* ── Login ── */
      handleLogin () {
        const user = this.formLogin.username.trim()
        const pass = this.formLogin.password
        if (!user) { this.showFieldErr('user', '请输入用户名'); return }
        if (!pass) { this.showFieldErr('pass', '请输入密码'); return }
        if (pass.length < 6) { this.showFieldErr('pass', '密码至少 6 位'); return }

        this.btnLoading = true
        this.btnText = '登录中...'

        let formData = Object.assign({}, this.formLogin)
        if (!this.tfaRequired) delete formData.tfa_code

        api.login(formData).then(res => {
          this.btnLoading = false
          this.loginSuccess = true
          this.btnText = '登录成功，跳转中...'
          this.changeModalStatus({ visible: false })
          this.showToast('欢迎回来，' + user + ' !')
          setTimeout(() => this.showToast('正在跳转到主页...', 'warn'), 1200)

          this.getProfile().then(() => {
            setTimeout(() => {
              const redirect = typeof this.$route.query.redirect === 'string' ? this.$route.query.redirect : '/'
              this.$router.replace(redirect).catch(() => {})
            }, 2000)
          }).catch(() => {})
        }, _ => {
          this.btnLoading = false
          this.btnText = '登录'
          this.showFieldErr('user', '用户名或密码错误')
          this.showFieldErr('pass', '请检查账号信息')
          this.showToast('登录失败，请检查账号信息', 'err')
        })
      },

      /* ── TFA check ── */
      checkTfa () {
        const u = this.formLogin.username
        if (u && document.cookie.indexOf('csrftoken=') !== -1) {
          api.tfaRequiredCheck(u).then(res => {
            this.tfaRequired = res.data.data.result
          }).catch(() => {})
        }
      },

      /* ── Navigation ── */
      goRegister () {
        const redirect = typeof this.$route.query.redirect === 'string' ? this.$route.query.redirect : '/'
        this.$router.push({ name: 'register', query: { redirect } })
      },
      goResetPassword () {
        this.$router.push({ name: 'apply-reset-password' })
      },

      /* ── Snake cursor ── */
      onSnakeMove (e) {
        const rect = this.$refs.brandPanel.getBoundingClientRect()
        this._mouse.x = e.clientX - rect.left
        this._mouse.y = e.clientY - rect.top
        this._mouse.active = true
      },

      onSnakeLeave () {
        this._mouse.active = false
      },

      _resizeSnakeCanvas () {
        const panel = this.$refs.brandPanel
        const canvas = this.$refs.snakeCanvas
        if (!panel || !canvas) return
        canvas.width = panel.offsetWidth
        canvas.height = panel.offsetHeight
      },

      /**
       * Measure each .ltr glyph's center position relative to the brand panel.
       * Applies the pretext character-measurement concept via DOM bounding rects,
       * giving us per-character 2-D coordinates for the physics scatter.
       */
      _measureLetters () {
        const panel = this.$refs.brandPanel
        if (!panel) return
        const panelRect = panel.getBoundingClientRect()
        this._letterData = []
        panel.querySelectorAll('.ltr').forEach(el => {
          const r = el.getBoundingClientRect()
          const ox = r.left - panelRect.left + r.width / 2
          const oy = r.top - panelRect.top + r.height / 2
          this._letterData.push({ el, ox, oy, dx: 0, dy: 0, vx: 0, vy: 0 })
        })
      },

      _startSnakeLoop () {
        const tick = () => {
          this._snakeTick()
          this._snakeRaf = requestAnimationFrame(tick)
        }
        this._snakeRaf = requestAnimationFrame(tick)
      },

      _snakeTick () {
        const canvas = this.$refs.snakeCanvas
        const ctx = canvas && canvas.getContext('2d')
        if (!ctx) return
        ctx.clearRect(0, 0, canvas.width, canvas.height)

        if (this._mouse.active) {
          this._updateSnake()
          this._drawSnake(ctx)
        }

        this._updateLetters()
      },

      _updateSnake () {
        const { x, y } = this._mouse
        const s = this._snake
        s[0].x += (x - s[0].x) * 0.28
        s[0].y += (y - s[0].y) * 0.28

        for (let i = 1; i < s.length; i++) {
          const p = s[i - 1]
          const c = s[i]
          const dx = p.x - c.x
          const dy = p.y - c.y
          const d = Math.sqrt(dx * dx + dy * dy)
          if (d > LINK_DIST) {
            const f = (d - LINK_DIST) / d * 0.5
            c.x += dx * f
            c.y += dy * f
          }
        }
      },

      _drawSnake (ctx) {
        const s = this._snake
        const n = s.length

        // Python brand palette: #FFD43B yellow / #3776AB blue-purple, alternating per segment
        const PY_YELLOW = [255, 212, 59]
        const PY_PURPLE = [55, 118, 171]

        // Body — draw tail-to-head so head overlaps
        for (let i = n - 1; i >= 0; i--) {
          const t = i / (n - 1)
          const radius = Math.max(8 - t * 5, 2.5)
          const alpha = 0.96 - t * 0.32
          const col = i % 2 === 0 ? PY_YELLOW : PY_PURPLE

          // Dark outline for contrast on blue banner
          ctx.strokeStyle = `rgba(0, 0, 0, ${0.22 * (1 - t * 0.5)})`
          ctx.lineWidth = 1
          ctx.beginPath()
          ctx.arc(s[i].x, s[i].y, radius, 0, Math.PI * 2)
          ctx.fillStyle = `rgba(${col[0]}, ${col[1]}, ${col[2]}, ${alpha})`
          ctx.fill()
          ctx.stroke()

          // Inner highlight shimmer
          ctx.fillStyle = `rgba(255, 255, 255, ${0.18 * (1 - t)})`
          ctx.beginPath()
          ctx.arc(s[i].x - radius * 0.2, s[i].y - radius * 0.2, radius * 0.38, 0, Math.PI * 2)
          ctx.fill()
        }

        if (n < 2) return
        const head = s[0]
        const neck = s[1]
        const faceAngle = Math.atan2(head.y - neck.y, head.x - neck.x)

        // Eyes
        ctx.fillStyle = '#ffffff'
        ;[-0.52, 0.52].forEach(da => {
          ctx.beginPath()
          ctx.arc(head.x + Math.cos(faceAngle + da) * 5, head.y + Math.sin(faceAngle + da) * 5, 2.2, 0, Math.PI * 2)
          ctx.fill()
        })

        // Pupils
        ctx.fillStyle = '#111827'
        ;[-0.52, 0.52].forEach(da => {
          ctx.beginPath()
          ctx.arc(head.x + Math.cos(faceAngle + da) * 5.5, head.y + Math.sin(faceAngle + da) * 5.5, 1.0, 0, Math.PI * 2)
          ctx.fill()
        })

        // Tongue
        const tBase = { x: head.x + Math.cos(faceAngle) * 9, y: head.y + Math.sin(faceAngle) * 9 }
        const tTip = { x: head.x + Math.cos(faceAngle) * 21, y: head.y + Math.sin(faceAngle) * 21 }

        ctx.strokeStyle = '#f43f5e'
        ctx.lineWidth = 1.8
        ctx.lineCap = 'round'
        ctx.beginPath()
        ctx.moveTo(tBase.x, tBase.y)
        ctx.lineTo(tTip.x, tTip.y)
        ctx.stroke()

        ;[-1, 1].forEach(dir => {
          ctx.beginPath()
          ctx.moveTo(tTip.x, tTip.y)
          ctx.lineTo(tTip.x + Math.cos(faceAngle + dir * 0.45) * 6, tTip.y + Math.sin(faceAngle + dir * 0.45) * 6)
          ctx.stroke()
        })
      },

      _updateLetters () {
        const head = this._snake[0]
        const active = this._mouse.active

        for (const ld of this._letterData) {
          if (active) {
            const wx = ld.ox + ld.dx
            const wy = ld.oy + ld.dy
            const ddx = wx - head.x
            const ddy = wy - head.y
            const dist = Math.sqrt(ddx * ddx + ddy * ddy)

            if (dist < SCATTER_R && dist > 0.5) {
              const force = (1 - dist / SCATTER_R) * 3.5
              ld.vx += (ddx / dist) * force
              ld.vy += (ddy / dist) * force
            }
          }

          // Spring toward origin
          ld.vx += -ld.dx * SPRING_K
          ld.vy += -ld.dy * SPRING_K

          // Damp
          ld.vx *= DAMPING
          ld.vy *= DAMPING

          ld.dx += ld.vx
          ld.dy += ld.vy

          // Clamp displacement
          const MAX_D = 55
          if (ld.dx > MAX_D) ld.dx = MAX_D
          if (ld.dx < -MAX_D) ld.dx = -MAX_D
          if (ld.dy > MAX_D) ld.dy = MAX_D
          if (ld.dy < -MAX_D) ld.dy = -MAX_D

          ld.el.style.transform = `translate(${ld.dx.toFixed(2)}px,${ld.dy.toFixed(2)}px)`
        }
      }
    },
    watch: {
      'formLogin.username' () { this.checkTfa() }
    }
  }
</script>

<style scoped>
:root {
  --blue: #1a73e8; --blue-d: #1558d6; --blue-l: #f0f6ff; --blue-b: #d2e3fc;
  --green: #34a853; --amber: #f59e0b; --red: #ea4335;
  --text: #1a1d2e; --text-2: #5f6368; --text-3: #9aa0ab;
  --border: #e8eaed; --radius: 14px; --radius-s: 10px;
}

.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  position: relative;
  font-family: -apple-system, 'PingFang SC', 'Microsoft YaHei', sans-serif;
  background: #f0f4ff;
}

/* ── Background ── */
.bg-mesh {
  position: fixed; inset: 0; z-index: 0;
  background:
    radial-gradient(ellipse 80% 60% at 10% 20%, rgba(26,115,232,.14) 0%, transparent 60%),
    radial-gradient(ellipse 60% 80% at 90% 80%, rgba(52,168,83,.11) 0%, transparent 60%),
    radial-gradient(ellipse 50% 50% at 50% 50%, rgba(245,158,11,.07) 0%, transparent 70%),
    #f0f4ff;
  animation: mesh-shift 12s ease-in-out infinite alternate;
}
@keyframes mesh-shift { 0% { filter: hue-rotate(0deg); } 100% { filter: hue-rotate(16deg); } }

.bg-orb { position: fixed; border-radius: 50%; filter: blur(44px); pointer-events: none; z-index: 0; }
.orb-1 { width: 420px; height: 420px; top: -80px; left: -80px;
  background: radial-gradient(circle, rgba(26,115,232,.18), transparent 70%);
  animation: orb1 18s ease-in-out infinite; }
.orb-2 { width: 320px; height: 320px; bottom: -60px; right: -60px;
  background: radial-gradient(circle, rgba(52,168,83,.15), transparent 70%);
  animation: orb2 22s ease-in-out infinite; }
.orb-3 { width: 200px; height: 200px; top: 40%; right: 20%;
  background: radial-gradient(circle, rgba(245,158,11,.12), transparent 70%);
  animation: orb3 16s ease-in-out infinite; }
@keyframes orb1 { 0%,100% { transform: translate(0,0) scale(1); } 33% { transform: translate(50px,70px) scale(1.22); } 66% { transform: translate(-25px,35px) scale(.85); } }
@keyframes orb2 { 0%,100% { transform: translate(0,0) scale(1); } 50% { transform: translate(-60px,-50px) scale(1.18); } }
@keyframes orb3 { 0%,100% { transform: translate(0,0) scale(1); } 50% { transform: translate(40px,-60px) scale(1.14); } }

.particle-canvas { position: fixed; inset: 0; z-index: 0; pointer-events: none; opacity: .5; }

/* ── Card ── */
.card-wrap {
  position: relative; z-index: 1;
  display: grid; grid-template-columns: 1fr 1fr;
  width: 860px; min-height: 520px;
  border-radius: 22px;
  box-shadow: 0 24px 80px rgba(26,115,232,.18), 0 8px 32px rgba(0,0,0,.08);
  overflow: hidden;
  opacity: 0; transform: translateY(24px) scale(.98);
  animation: card-in .65s cubic-bezier(.22,1,.36,1) .1s forwards;
}
@keyframes card-in { to { opacity: 1; transform: translateY(0) scale(1); } }

/* ── Brand panel (left) ── */
.brand-panel {
  background: linear-gradient(145deg, #1a73e8 0%, #1250c4 55%, #0e3fa0 100%);
  padding: 48px 44px;
  display: flex; flex-direction: column; justify-content: space-between;
  position: relative; overflow: hidden;
  cursor: none;
}

.snake-canvas {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 3;
}

.ltr {
  display: inline-block;
  will-change: transform;
  position: relative;
  z-index: 2;
}

.ltr-accent {
  color: rgba(255, 220, 80, 1);
}
.brand-deco { position: absolute; border-radius: 50%; background: rgba(255,255,255,.08); pointer-events: none; }
.deco-1 { width: 260px; height: 260px; top: -80px; right: -60px; animation: dspin 30s linear infinite; }
.deco-2 { width: 180px; height: 180px; bottom: 30px; left: -60px; animation: dspin 24s linear infinite reverse; }
.deco-3 { width: 80px; height: 80px; top: 55%; right: 40px; animation: dpulse 4s ease-in-out infinite; }
@keyframes dspin { to { transform: rotate(360deg); } }
@keyframes dpulse { 0%,100% { transform: scale(1); opacity: .06; } 50% { transform: scale(1.4); opacity: .24; } }

.brand-top { position: relative; z-index: 1; }
.brand-logo {
  display: flex; align-items: center; gap: 10px; margin-bottom: 36px;
  opacity: 0; transform: translateY(12px); animation: fadeup .5s .35s ease forwards;
}
.brand-logo-ring {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.95);
  padding: 4px;
  object-fit: contain;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.18);
}
.brand-logo-text { font-size: 12px; font-weight: 600; color: rgba(255,255,255,.7); letter-spacing: 1.5px; text-transform: uppercase; }

.brand-headline {
  font-size: 38px; font-weight: 800; color: #fff; line-height: 1.15;
  letter-spacing: -.5px; margin-bottom: 18px;
  opacity: 0; transform: translateY(12px); animation: fadeup .5s .45s ease forwards;
}

.brand-desc {
  font-size: 13px; color: rgba(255,255,255,.72); line-height: 1.7; max-width: 260px;
  opacity: 0; transform: translateY(12px); animation: fadeup .5s .55s ease forwards;
}
.brand-tags {
  display: flex; gap: 8px; flex-wrap: wrap; position: relative; z-index: 1;
  opacity: 0; transform: translateY(8px); animation: fadeup .5s .65s ease forwards;
}
.brand-tag {
  padding: 7px 16px; border-radius: 20px; font-size: 12px; font-weight: 600;
  cursor: default; transition: transform .2s, box-shadow .2s; backdrop-filter: blur(6px);
}
.brand-tag:hover { transform: translateY(-2px); box-shadow: 0 6px 16px rgba(0,0,0,.2); }
.tag-judge { background: rgba(255,255,255,.15); color: #fff; border: 1px solid rgba(255,255,255,.25); }
.tag-track { background: #f59e0b; color: #fff; border: 1px solid rgba(255,255,255,.1); }
.tag-improve { background: #1a1d2e; color: #fff; border: 1px solid rgba(255,255,255,.1); }

@keyframes fadeup { to { opacity: 1; transform: translateY(0); } }

/* ── Form panel (right) ── */
.form-panel { background: #fff; display: flex; flex-direction: column; }

.form-top {
  background: linear-gradient(135deg, #1a73e8, #1f83f8);
  padding: 28px 40px 24px; position: relative; overflow: hidden;
  opacity: 0; animation: fadein .4s .3s ease forwards;
}
.form-top::after { content: ''; position: absolute; bottom: 0; left: 0; right: 0; height: 1px; background: rgba(255,255,255,.15); }
.form-top-brand { font-size: 10px; font-weight: 600; color: rgba(255,255,255,.6); letter-spacing: 1.5px; text-transform: uppercase; margin-bottom: 6px; }
.form-top-title { font-size: 26px; font-weight: 800; color: #fff; letter-spacing: -.3px; margin-bottom: 14px; }
.step-dots { display: flex; gap: 6px; align-items: center; }
.step-dot { height: 4px; border-radius: 2px; transition: all .3s; }
.step-dot.active { width: 28px; background: #f59e0b; }
.step-dot.inactive { width: 16px; background: rgba(255,255,255,.3); }

@keyframes fadein { to { opacity: 1; } }

.form-body {
  flex: 1; padding: 28px 40px 32px; display: flex; flex-direction: column;
  opacity: 0; animation: fadein .4s .45s ease forwards;
}

/* ── Fields ── */
.field { margin-bottom: 16px; }
.field-label {
  font-size: 11px; font-weight: 600; color: #5f6368;
  margin-bottom: 6px; display: flex; align-items: center; gap: 5px;
  letter-spacing: .3px; text-transform: uppercase;
}
.field-wrap { position: relative; }
.f-input {
  width: 100%; height: 46px; padding: 0 14px 0 40px;
  font-size: 14px; color: #1a1d2e; font-family: inherit;
  background: #f8f9ff; border: 1.5px solid #e8eaed;
  border-radius: 10px; outline: none;
  transition: all .22s cubic-bezier(.4,0,.2,1);
}
.f-input:hover { background: #f0f4ff; border-color: #d2e3fc; }
.f-input:focus { background: #fff; border-color: #1a73e8; box-shadow: 0 0 0 3px rgba(26,115,232,.12); }
.f-input.error { border-color: #ea4335; background: #fff5f5; box-shadow: 0 0 0 3px rgba(234,67,53,.1); animation: shake .4s ease; }
@keyframes shake { 0%,100% { transform: translateX(0); } 20% { transform: translateX(-6px); } 40% { transform: translateX(6px); } 60% { transform: translateX(-4px); } 80% { transform: translateX(4px); } }
.f-input::placeholder { color: #9aa0ab; }

.field-icon {
  position: absolute; left: 12px; top: 50%; transform: translateY(-50%);
  color: #9aa0ab; transition: color .2s; pointer-events: none; display: flex; align-items: center;
}
.field-wrap:focus-within .field-icon { color: #1a73e8; }

.pw-toggle {
  position: absolute; right: 12px; top: 50%; transform: translateY(-50%);
  color: #9aa0ab; cursor: pointer; padding: 4px; transition: color .15s; display: flex; align-items: center;
}
.pw-toggle:hover { color: #1a73e8; }

.field-underline {
  position: absolute; bottom: 0; left: 0; right: 0; height: 2px;
  background: #1a73e8; border-radius: 0 0 10px 10px;
  transform: scaleX(0); transform-origin: left;
  transition: transform .25s cubic-bezier(.4,0,.2,1); pointer-events: none;
}
.field-wrap:focus-within .field-underline { transform: scaleX(1); }

.field-error {
  font-size: 11px; color: #ea4335; margin-top: 5px;
  display: flex; align-items: center; gap: 4px;
  opacity: 0; transform: translateY(-4px); transition: all .2s;
}
.field-error.show { opacity: 1; transform: translateY(0); }

/* ── Login button ── */
.login-btn {
  width: 100%; height: 48px; border-radius: 10px;
  background: linear-gradient(135deg, #1a73e8, #1f83f8);
  color: #fff; font-size: 15px; font-weight: 700;
  border: none; cursor: pointer; outline: none;
  position: relative; overflow: hidden;
  transition: all .22s; font-family: inherit; margin-top: 8px;
  box-shadow: 0 4px 16px rgba(26,115,232,.3);
}
.login-btn::before {
  content: ''; position: absolute; inset: 0;
  background: linear-gradient(135deg, rgba(255,255,255,.12), rgba(255,255,255,0));
  transition: opacity .2s;
}
.login-btn:hover { transform: translateY(-2px); box-shadow: 0 8px 24px rgba(26,115,232,.4); }
.login-btn:active { transform: translateY(0) scale(.98); box-shadow: 0 2px 8px rgba(26,115,232,.25); }
.login-btn:disabled { cursor: default; }
.login-btn:disabled:hover { transform: none; }
.btn-inner { display: flex; align-items: center; justify-content: center; gap: 8px; transition: all .2s; }
.btn-spinner {
  width: 16px; height: 16px; border-radius: 50%;
  border: 2px solid rgba(255,255,255,.3); border-top-color: #fff;
  animation: spin .6s linear infinite; display: none;
}
.login-btn.loading .btn-spinner { display: block; }
.login-btn.loading .btn-text { opacity: .7; }
@keyframes spin { to { transform: rotate(360deg); } }
.login-btn.success { background: linear-gradient(135deg, #34a853, #2bbd4e); box-shadow: 0 4px 16px rgba(52,168,83,.35); pointer-events: none; }

/* ── Aux ── */
.form-aux { display: flex; align-items: center; justify-content: space-between; margin-top: 4px; }
.remember-row { display: flex; align-items: center; gap: 7px; cursor: pointer; }
.custom-check {
  width: 16px; height: 16px; border-radius: 4px;
  border: 1.5px solid #e8eaed; background: #fff;
  display: flex; align-items: center; justify-content: center;
  transition: all .15s; flex-shrink: 0;
}
.remember-row:hover .custom-check { border-color: #1a73e8; }
.custom-check.checked { background: #1a73e8; border-color: #1a73e8; }
.custom-check.checked svg { opacity: 1; }
.custom-check svg { opacity: 0; transition: opacity .15s; }
.remember-label { font-size: 12px; color: #5f6368; user-select: none; }
.forgot-link { font-size: 12px; color: #1a73e8; cursor: pointer; transition: color .15s; }
.forgot-link:hover { color: #1558d6; text-decoration: underline; }

.register-row { text-align: center; margin-top: 20px; font-size: 12px; color: #9aa0ab; }
.register-link { color: #1a73e8; font-weight: 500; cursor: pointer; transition: color .15s; }
.register-link:hover { color: #1558d6; }

/* ── Toast ── */
.toasts {
  position: fixed; bottom: 28px; left: 50%; transform: translateX(-50%);
  z-index: 9999; display: flex; flex-direction: column; gap: 8px;
  align-items: center; pointer-events: none;
}
</style>

<style>
.toast {
  background: #1a1d2e; color: #fff; padding: 10px 18px; border-radius: 10px;
  font-size: 12px; font-weight: 500; white-space: nowrap;
  box-shadow: 0 6px 20px rgba(0,0,0,.2);
  display: flex; align-items: center; gap: 8px; animation: t-in .3s ease;
}
.toast.out { animation: t-out .3s ease forwards; }
@keyframes t-in { from { opacity: 0; transform: translateY(12px); } to { opacity: 1; transform: none; } }
@keyframes t-out { to { opacity: 0; transform: translateY(-8px); } }
.t-dot { width: 6px; height: 6px; border-radius: 50%; }
.t-dot.ok { background: #34a853; }
.t-dot.warn { background: #f59e0b; }
.t-dot.err { background: #ea4335; }

/* ── Ripple ── */
.rip { position: relative; overflow: hidden; }
.ripple {
  position: absolute; border-radius: 50%;
  background: rgba(255,255,255,.25); transform: scale(0);
  animation: rip .5s linear; pointer-events: none;
}
@keyframes rip { to { transform: scale(4); opacity: 0; } }
</style>

<style scoped>
@media (max-width: 700px) {
  .brand-panel { display: none; }
  .card-wrap { grid-template-columns: 1fr; width: 95vw; }
}
</style>
