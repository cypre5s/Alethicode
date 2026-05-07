<template>
  <div class="login-color-card">
    <div class="login-banner" ref="bannerRef" @mousemove="onMouseMove" @mouseleave="onMouseLeave">
      <canvas ref="snakeCanvas" class="snake-canvas" />

      <div class="orb orb-tr" />
      <div class="orb orb-bl" />

      <div class="banner-logo">
        <img class="logo-badge" src="/logo.png" alt="Alethicode logo">
        <span class="logo-name">
          <span v-for="(c, i) in logoLetters" :key="'l' + i" class="ltr ltr-logo">{{ c }}</span>
        </span>
      </div>

      <div class="banner-copy">
        <p class="headline">
          <span v-for="(c, i) in headlineLetters" :key="'m' + i" class="ltr">{{ c === ' ' ? '\u00A0' : c }}</span>
        </p>
        <p class="headline headline-accent">
          <span v-for="(c, i) in accentLetters" :key="'a' + i" class="ltr ltr-accent">{{ c }}</span>
        </p>
      </div>

      <p class="banner-sub">
        <span v-for="(c, i) in subLetters" :key="'s' + i" class="ltr ltr-sub">{{ c === '\n' ? '' : c }}</span>
      </p>

      <div class="banner-pills">
        <span class="pill pill-ghost"><span v-for="(c, i) in [...'Judge']" :key="'j' + i" class="ltr ltr-pill">{{ c }}</span></span>
        <span class="pill pill-amber"><span v-for="(c, i) in [...'Track']" :key="'t' + i" class="ltr ltr-pill">{{ c }}</span></span>
        <span class="pill pill-dark"><span v-for="(c, i) in [...'Improve']" :key="'i' + i" class="ltr ltr-pill">{{ c }}</span></span>
      </div>
    </div>

    <div class="login-form-wrap">
      <ElForm ref="formLogin" :model="formLogin" :rules="ruleLogin">
        <ElFormItem prop="username">
          <ElInput
            type="text"
            v-model="formLogin.username"
            :placeholder="$t('m.LoginUsername')"
            size="large"
            @keyup.enter="handleLogin">
            <template #prepend><ElIcon><User /></ElIcon></template>
          </ElInput>
        </ElFormItem>
        <ElFormItem prop="password">
          <ElInput
            type="password"
            v-model="formLogin.password"
            :placeholder="$t('m.LoginPassword')"
            size="large"
            @keyup.enter="handleLogin">
            <template #prepend><ElIcon><Lock /></ElIcon></template>
          </ElInput>
        </ElFormItem>
        <ElFormItem prop="tfa_code" v-if="tfaRequired">
          <ElInput v-model="formLogin.tfa_code" :placeholder="$t('m.TFA_Code')">
            <template #prepend><ElIcon><Sunny /></ElIcon></template>
          </ElInput>
        </ElFormItem>
      </ElForm>
      <div class="footer">
        <ElButton
          type="primary"
          @click="handleLogin"
          class="btn"
          style="width:100%"
          :loading="btnLoginLoading">
          {{ $t('m.UserLogin') }}
        </ElButton>
        <a v-if="website.allow_register" @click.stop="handleBtnClick('register')">{{ $t('m.No_Account') }}</a>
        <a @click.stop="goResetPassword" style="float: right">{{ $t('m.Forget_Password') }}</a>
      </div>
    </div>
  </div>
</template>

<script>
import { mapGetters, mapActions } from 'vuex'
import api from '@oj/api'
import { User, Lock, Sunny } from '@element-plus/icons-vue'

const SNAKE_LEN = 14
const LINK_DIST = 8
const SCATTER_R = 72
const SPRING_K = 0.11
const DAMPING = 0.80

export default {
  name: 'Login',
  components: { User, Lock, Sunny },

  data () {
    const checkTFA = (rule, value, callback) => {
      if (value !== '' && document.cookie.indexOf('csrftoken=') !== -1) {
        api.tfaRequiredCheck(value).then(res => {
          this.tfaRequired = res.data.data.result
        }).catch(() => {})
      }
      callback()
    }

    return {
      tfaRequired: false,
      btnLoginLoading: false,
      logoLetters: [...'ALETHICODE'],
      headlineLetters: [...'Code with'],
      accentLetters: [...'focus.'],
      subLetters: [...'先登录，再进入系统。使用统一账号完成做题、评测与学习分析。'],
      formLogin: { username: '', password: '', tfa_code: '' },
      ruleLogin: {
        username: [
          { required: true, trigger: 'blur' },
          { validator: checkTFA, trigger: 'blur' }
        ],
        password: [{ required: true, trigger: 'change', min: 6, max: 20 }]
      }
    }
  },

  computed: {
    ...mapGetters(['website', 'modalStatus']),
    visible: {
      get () { return this.modalStatus.visible },
      set (value) { this.changeModalStatus({ visible: value }) }
    }
  },

  methods: {
    ...mapActions(['changeModalStatus', 'getProfile']),

    handleBtnClick (mode) {
      if (mode === 'register' && this.$route.name === 'login') {
        const redirect = typeof this.$route.query.redirect === 'string' ? this.$route.query.redirect : '/'
        this.$router.push({ name: 'register', query: { redirect } })
        return
      }
      this.changeModalStatus({ mode, visible: true })
    },

    handleLogin () {
      this.$refs.formLogin.validate().then(() => {
        this.btnLoginLoading = true
        const formData = Object.assign({}, this.formLogin)
        if (!this.tfaRequired) delete formData.tfa_code
        api.login(formData).then(() => {
          this.btnLoginLoading = false
          this.changeModalStatus({ visible: false })
          this.getProfile().then(() => {
            this.$success(this.$t('m.Welcome_back'))
            if (this.$route.name === 'login') {
              const redirect = typeof this.$route.query.redirect === 'string' ? this.$route.query.redirect : '/'
              this.$router.replace(redirect).catch(() => {})
            }
          }).catch(() => {})
        }, () => {
          this.btnLoginLoading = false
        })
      })
    },

    goResetPassword () {
      this.changeModalStatus({ visible: false })
      this.$router.push({ name: 'apply-reset-password' })
    },

    onMouseMove (e) {
      const rect = this.$refs.bannerRef.getBoundingClientRect()
      this._mouse.x = e.clientX - rect.left
      this._mouse.y = e.clientY - rect.top
      this._mouse.active = true
    },

    onMouseLeave () {
      this._mouse.active = false
    },

    _resizeCanvas () {
      const banner = this.$refs.bannerRef
      const canvas = this.$refs.snakeCanvas
      if (!banner || !canvas) return
      canvas.width = banner.offsetWidth
      canvas.height = banner.offsetHeight
    },

    /**
     * 测量每个 `.ltr` 字形中心点，为物理散开动画提供二维坐标。
     */
    _measureLetters () {
      const banner = this.$refs.bannerRef
      if (!banner) return
      const bannerRect = banner.getBoundingClientRect()
      this._letterData = []
      banner.querySelectorAll('.ltr').forEach(el => {
        const r = el.getBoundingClientRect()
        const ox = r.left - bannerRect.left + r.width / 2
        const oy = r.top - bannerRect.top + r.height / 2
        this._letterData.push({ el, ox, oy, dx: 0, dy: 0, vx: 0, vy: 0 })
      })
    },

    _startLoop () {
      const tick = () => {
        this._tick()
        this._rafId = requestAnimationFrame(tick)
      }
      this._rafId = requestAnimationFrame(tick)
    },

    _tick () {
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

      // 使用 Python 品牌黄蓝配色，让每节身体交替着色。
      const PY_YELLOW = [255, 212, 59]
      const PY_PURPLE = [55, 118, 171]

      // 从尾到头绘制，保证头部覆盖身体。
      for (let i = n - 1; i >= 0; i--) {
        const t = i / (n - 1)
        const radius = Math.max(7 - t * 4.5, 2)
        const alpha = 0.96 - t * 0.32
        const col = i % 2 === 0 ? PY_YELLOW : PY_PURPLE

        ctx.strokeStyle = `rgba(0, 0, 0, ${0.18 * (1 - t * 0.5)})`
        ctx.lineWidth = 1
        ctx.beginPath()
        ctx.arc(s[i].x, s[i].y, radius, 0, Math.PI * 2)
        ctx.fillStyle = `rgba(${col[0]}, ${col[1]}, ${col[2]}, ${alpha})`
        ctx.fill()
        ctx.stroke()

        // 内部高光强化立体感。
        ctx.fillStyle = `rgba(255, 255, 255, ${0.18 * (1 - t)})`
        ctx.beginPath()
        ctx.arc(s[i].x - radius * 0.2, s[i].y - radius * 0.2, radius * 0.38, 0, Math.PI * 2)
        ctx.fill()
      }

      if (n < 2) return

      const head = s[0]
      const neck = s[1]
      const faceAngle = Math.atan2(head.y - neck.y, head.x - neck.x)

      ctx.fillStyle = '#ffffff'
      ;[-0.52, 0.52].forEach(da => {
        ctx.beginPath()
        ctx.arc(
          head.x + Math.cos(faceAngle + da) * 4,
          head.y + Math.sin(faceAngle + da) * 4,
          1.9, 0, Math.PI * 2
        )
        ctx.fill()
      })

      ctx.fillStyle = '#111827'
      ;[-0.52, 0.52].forEach(da => {
        ctx.beginPath()
        ctx.arc(
          head.x + Math.cos(faceAngle + da) * 4.5,
          head.y + Math.sin(faceAngle + da) * 4.5,
          0.9, 0, Math.PI * 2
        )
        ctx.fill()
      })

      const tBase = {
        x: head.x + Math.cos(faceAngle) * 8,
        y: head.y + Math.sin(faceAngle) * 8
      }
      const tTip = {
        x: head.x + Math.cos(faceAngle) * 19,
        y: head.y + Math.sin(faceAngle) * 19
      }
      const FORK = 5

      ctx.strokeStyle = '#f43f5e'
      ctx.lineWidth = 1.5
      ctx.lineCap = 'round'

      ctx.beginPath()
      ctx.moveTo(tBase.x, tBase.y)
      ctx.lineTo(tTip.x, tTip.y)
      ctx.stroke()

      ;[-1, 1].forEach(dir => {
        ctx.beginPath()
        ctx.moveTo(tTip.x, tTip.y)
        ctx.lineTo(
          tTip.x + Math.cos(faceAngle + dir * 0.45) * FORK,
          tTip.y + Math.sin(faceAngle + dir * 0.45) * FORK
        )
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
            const force = (1 - dist / SCATTER_R) * 3.2
            ld.vx += (ddx / dist) * force
            ld.vy += (ddy / dist) * force
          }
        }

        // 弹回原始字形位置。
        ld.vx += -ld.dx * SPRING_K
        ld.vy += -ld.dy * SPRING_K

        ld.vx *= DAMPING
        ld.vy *= DAMPING

        ld.dx += ld.vx
        ld.dy += ld.vy

        // 限制偏移，避免字形被甩出横幅。
        const MAX_D = 52
        if (ld.dx > MAX_D) ld.dx = MAX_D
        if (ld.dx < -MAX_D) ld.dx = -MAX_D
        if (ld.dy > MAX_D) ld.dy = MAX_D
        if (ld.dy < -MAX_D) ld.dy = -MAX_D

        ld.el.style.transform = `translate(${ld.dx.toFixed(2)}px,${ld.dy.toFixed(2)}px)`
      }
    }
  },

  mounted () {
    this._snake = Array.from({ length: SNAKE_LEN }, () => ({ x: 0, y: 0 }))
    this._letterData = []
    this._mouse = { x: 0, y: 0, active: false }
    this._rafId = null

    this.$nextTick(() => {
      this._resizeCanvas()
      this._measureLetters()
      this._startLoop()

      const banner = this.$refs.bannerRef
      if (banner) {
        const cx = banner.offsetWidth / 2
        const cy = banner.offsetHeight * 0.75
        this._snake.forEach(seg => { seg.x = cx; seg.y = cy })
      }

      this._resizeObs = new ResizeObserver(() => {
        this._resizeCanvas()
        this._measureLetters()
      })
      this._resizeObs.observe(this.$refs.bannerRef)
    })

    api.csrf().catch(() => {})
  },

  beforeUnmount () {
    cancelAnimationFrame(this._rafId)
    if (this._resizeObs) this._resizeObs.disconnect()
  }
}
</script>

<style scoped lang="less">
.login-color-card {
  --c-primary: var(--primary-color, #2563eb);
  --c-accent: #fbbf24;
  border-radius: 16px;
  overflow: hidden;
  border: 1px solid rgba(15, 23, 42, 0.08);
  box-shadow: 0 14px 36px rgba(15, 23, 42, 0.12);
  background: #ffffff;
}

.login-banner {
  position: relative;
  overflow: hidden;
  cursor: none;
  background: linear-gradient(145deg, #1e40af 0%, #2563eb 52%, #3b82f6 100%);
  padding: 22px 22px 26px;
  min-height: 290px;
  display: flex;
  flex-direction: column;
  color: #fff;
  user-select: none;
}

.snake-canvas {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 5;
}

.orb {
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.13);
  pointer-events: none;
}

.orb-tr {
  width: 155px;
  height: 155px;
  top: -44px;
  right: -32px;
}

.orb-bl {
  width: 195px;
  height: 195px;
  bottom: -78px;
  left: -58px;
}

.banner-logo {
  display: flex;
  align-items: center;
  gap: 8px;
  position: relative;
  z-index: 2;
  margin-bottom: 30px;
}

.logo-badge {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.95);
  padding: 3px;
  object-fit: contain;
  flex-shrink: 0;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12);
}

.logo-name {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.15em;
  color: rgba(255, 255, 255, 0.88);
}

.banner-copy {
  position: relative;
  z-index: 2;
}

.headline {
  margin: 0;
  font-size: 38px;
  font-weight: 900;
  line-height: 1.08;
  color: #fff;
  letter-spacing: -0.025em;

  &.headline-accent {
    color: var(--c-accent);
  }
}

.ltr {
  display: inline-block;
  will-change: transform;
  position: relative;
  z-index: 2;
}

.ltr-logo {
  letter-spacing: 0.18em;
}

.ltr-sub {
  font-size: inherit;
  line-height: inherit;
}

.ltr-pill {
  font-size: inherit;
  font-weight: inherit;
}

.banner-sub {
  margin: 16px 0 0;
  font-size: 12.5px;
  line-height: 1.75;
  color: rgba(255, 255, 255, 0.72);
  position: relative;
  z-index: 2;
}

.banner-pills {
  margin-top: auto;
  padding-top: 24px;
  display: flex;
  gap: 8px;
  position: relative;
  z-index: 2;
}

.pill {
  display: inline-flex;
  align-items: center;
  height: 30px;
  padding: 0 14px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.01em;
  cursor: none;
}

.pill-ghost {
  border: 1.5px solid rgba(255, 255, 255, 0.45);
  color: rgba(255, 255, 255, 0.9);
}

.pill-amber {
  background: #f59e0b;
  color: #1c1917;
}

.pill-dark {
  background: #0f172a;
  color: #fff;
}

.login-form-wrap {
  padding: 18px 20px 20px;
  background: linear-gradient(160deg, #fff7ed 0%, #ffffff 34%);
}

.footer {
  overflow: auto;
  margin-top: 14px;
  margin-bottom: -6px;
  text-align: left;

  .btn {
    margin: 0 0 16px;
    height: 42px;
    font-size: 14px;
    font-weight: 700;
    border-radius: 999px;
    border: 0;
    background: linear-gradient(90deg, var(--c-primary) 0%, #3b82f6 100%);
    box-shadow: 0 10px 20px rgba(37, 99, 235, 0.25);
  }

  a {
    color: var(--text-secondary);
    transition: color 0.2s;

    &:hover {
      color: var(--primary-color);
    }
  }
}

:deep(.el-input--large .el-input__wrapper) {
  height: 42px;
  border-radius: 12px;
}

:deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--c-primary) inset, 0 0 0 3px rgba(37, 99, 235, 0.15);
}
</style>
