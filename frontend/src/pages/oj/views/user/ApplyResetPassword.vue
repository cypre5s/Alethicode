<template>
  <div class="recover-page">
    <div class="bg-mesh"></div>
    <div class="bg-orb orb-1"></div>
    <div class="bg-orb orb-2"></div>
    <div class="bg-orb orb-3"></div>
    <canvas ref="particleCanvas" class="particle-canvas"></canvas>

    <div class="toasts" ref="toastContainer"></div>

    <div class="card-wrap">
      <div class="brand-panel">
        <div class="brand-deco deco-1"></div>
        <div class="brand-deco deco-2"></div>
        <div class="brand-deco deco-3"></div>
        <div class="brand-top">
          <div class="brand-logo">
            <img class="brand-logo-ring" src="/logo.png" alt="Alethicode logo">
            <span class="brand-logo-text">Alethicode</span>
          </div>
          <div class="brand-headline">Reset your<br><span class="accent">password.</span></div>
          <div class="brand-desc">输入注册邮箱并完成验证码校验，我们将发送找回密码链接。</div>
        </div>
        <div class="brand-tags">
          <div class="brand-tag tag-judge">Secure</div>
          <div class="brand-tag tag-track">Fast</div>
        </div>
      </div>

      <div class="form-panel">
        <div class="form-top">
          <div class="form-top-brand">Alethicode</div>
          <div class="form-top-title">{{ $t('m.Reset_Password') }}</div>
          <div class="step-dots">
            <div class="step-dot active"></div>
            <div class="step-dot inactive"></div>
          </div>
        </div>

        <div class="form-body" v-if="!successApply">
          <div class="field">
            <div class="field-label">
              <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"/><polyline points="22,6 12,13 2,6"/></svg>
              {{ $t('m.ApplyEmail') }}
            </div>
            <div class="field-wrap">
              <span class="field-icon">
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"/><polyline points="22,6 12,13 2,6"/></svg>
              </span>
              <input class="f-input"
                :class="{error: fieldErr.email}"
                v-model="formReset.email" type="email"
                :placeholder="$t('m.ApplyEmail')"
                @input="clearFieldErr('email')"
                @keydown.enter="sendEmail">
              <div class="field-underline"></div>
            </div>
            <div class="field-error" :class="{show: fieldErr.email}">
              <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
              <span>{{ fieldErr.emailMsg }}</span>
            </div>
          </div>

          <div class="field">
            <div class="field-label">
              <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
              {{ $t('m.RCaptcha') }}
            </div>
            <div class="captcha-row">
              <div class="field-wrap captcha-input-wrap">
                <span class="field-icon">
                  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
                </span>
                <input class="f-input"
                  :class="{error: fieldErr.captcha}"
                  v-model="formReset.captcha" type="text"
                  :placeholder="$t('m.RCaptcha')"
                  @input="clearFieldErr('captcha')"
                  @keydown.enter="sendEmail">
                <div class="field-underline"></div>
              </div>
              <div class="captcha-img" @click="getCaptchaSrc">
                <img :src="captchaSrc" alt="captcha">
              </div>
            </div>
            <div class="field-error" :class="{show: fieldErr.captcha}">
              <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
              <span>{{ fieldErr.captchaMsg }}</span>
            </div>
          </div>

          <button class="login-btn rip"
            :class="{loading: btnLoading}"
            @click="sendEmail"
            :disabled="btnLoading">
            <div class="btn-inner">
              <div class="btn-spinner"></div>
              <span class="btn-text">{{ $t('m.Send_Password_Reset_Email') }}</span>
            </div>
          </button>

          <div class="register-row">
            <span class="register-link" @click="$router.push({name: 'login'})">{{ $t('m.Login') }}</span>
          </div>
        </div>

        <div class="form-body success-body" v-else>
          <div class="success-icon-wrap">
            <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="#34a853" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><polyline points="16 8.5 10 15 7.5 12.5"/></svg>
          </div>
          <h3 class="success-title">{{ $t('Success') }}</h3>
          <p class="success-desc">{{ $t('Password_reset_mail_sent') }}</p>
          <button class="login-btn" @click="$router.push({name: 'login'})">
            <div class="btn-inner">
              <span class="btn-text">{{ $t('m.Login') }}</span>
            </div>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
  import api from '@oj/api'

  export default {
    name: 'ApplyResetPassword',
    data () {
      return {
        captchaSrc: '',
        formReset: { email: '', captcha: '' },
        successApply: false,
        btnLoading: false,
        fieldErr: {
          email: false,
          emailMsg: '',
          captcha: false,
          captchaMsg: ''
        },
        particles: [],
        animFrameId: null
      }
    },
    mounted () {
      this.getCaptchaSrc()
      this.initParticles()
    },
    beforeUnmount () {
      if (this.animFrameId) cancelAnimationFrame(this.animFrameId)
      if (this._removeResizeListener) this._removeResizeListener()
    },
    methods: {
      getCaptchaSrc () {
        api.getCaptcha().then(res => { this.captchaSrc = res.data.data })
      },

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
              const dx = pts[i].x - pts[j].x, dy = pts[i].y - pts[j].y
              const d = Math.sqrt(dx * dx + dy * dy)
              if (d < 130) {
                cx.beginPath(); cx.moveTo(pts[i].x, pts[i].y); cx.lineTo(pts[j].x, pts[j].y)
                cx.strokeStyle = '#1a73e8'; cx.globalAlpha = (1 - d / 130) * 0.07
                cx.lineWidth = 0.8; cx.stroke()
              }
            }
          }
          this.animFrameId = requestAnimationFrame(draw)
        }
        draw()
      },

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
        setTimeout(() => { t.classList.add('out'); t.addEventListener('animationend', () => t.remove()) }, 2800)
      },

      clearFieldErr (key) { this.fieldErr[key] = false; this.fieldErr[key + 'Msg'] = '' },
      showFieldErr (key, msg) { this.fieldErr[key] = true; this.fieldErr[key + 'Msg'] = msg },

      sendEmail () {
        const f = this.formReset
        if (!f.email.trim()) { this.showFieldErr('email', '请输入邮箱'); return }
        if (!/\S+@\S+\.\S+/.test(f.email)) { this.showFieldErr('email', '邮箱格式不正确'); return }
        if (!f.captcha.trim()) { this.showFieldErr('captcha', '请输入验证码'); return }

        this.btnLoading = true
        api.applyResetPassword(this.formReset).then(res => {
          setTimeout(() => {
            this.btnLoading = false
            this.successApply = true
            this.showToast('重置邮件已发送！')
          }, 2000)
        }, _ => {
          this.btnLoading = false
          this.formReset.captcha = ''
          this.getCaptchaSrc()
          this.showToast('发送失败，请重试', 'err')
        })
      }
    }
  }
</script>

<style scoped>
.recover-page {
  min-height: 100vh;
  display: flex; align-items: center; justify-content: center;
  overflow: hidden; position: relative;
  font-family: -apple-system, 'PingFang SC', 'Microsoft YaHei', sans-serif;
  background: #f0f4ff;
}

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
  background: radial-gradient(circle, rgba(26,115,232,.18), transparent 70%); animation: orb1 18s ease-in-out infinite; }
.orb-2 { width: 320px; height: 320px; bottom: -60px; right: -60px;
  background: radial-gradient(circle, rgba(52,168,83,.15), transparent 70%); animation: orb2 22s ease-in-out infinite; }
.orb-3 { width: 200px; height: 200px; top: 40%; right: 20%;
  background: radial-gradient(circle, rgba(245,158,11,.12), transparent 70%); animation: orb3 16s ease-in-out infinite; }
@keyframes orb1 { 0%,100% { transform: translate(0,0) scale(1); } 33% { transform: translate(50px,70px) scale(1.22); } 66% { transform: translate(-25px,35px) scale(.85); } }
@keyframes orb2 { 0%,100% { transform: translate(0,0) scale(1); } 50% { transform: translate(-60px,-50px) scale(1.18); } }
@keyframes orb3 { 0%,100% { transform: translate(0,0) scale(1); } 50% { transform: translate(40px,-60px) scale(1.14); } }

.particle-canvas { position: fixed; inset: 0; z-index: 0; pointer-events: none; opacity: .5; }

.card-wrap {
  position: relative; z-index: 1;
  display: grid; grid-template-columns: 1fr 1fr;
  width: 860px; min-height: 480px;
  border-radius: 22px;
  box-shadow: 0 24px 80px rgba(26,115,232,.18), 0 8px 32px rgba(0,0,0,.08);
  overflow: hidden;
  opacity: 0; transform: translateY(24px) scale(.98);
  animation: card-in .65s cubic-bezier(.22,1,.36,1) .1s forwards;
}
@keyframes card-in { to { opacity: 1; transform: translateY(0) scale(1); } }

.brand-panel {
  background: linear-gradient(145deg, #1a73e8 0%, #1250c4 55%, #0e3fa0 100%);
  padding: 48px 44px;
  display: flex; flex-direction: column; justify-content: space-between;
  position: relative; overflow: hidden;
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
.brand-headline .accent { color: rgba(255,220,80,1); }

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

@keyframes fadeup { to { opacity: 1; transform: translateY(0); } }

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
.step-dot { height: 4px; border-radius: 2px; }
.step-dot.active { width: 28px; background: #f59e0b; }
.step-dot.inactive { width: 16px; background: rgba(255,255,255,.3); }

@keyframes fadein { to { opacity: 1; } }

.form-body {
  flex: 1; padding: 28px 40px 32px; display: flex; flex-direction: column;
  opacity: 0; animation: fadein .4s .45s ease forwards;
}

.success-body { align-items: center; justify-content: center; text-align: center; }
.success-icon-wrap { margin-bottom: 20px; animation: fadeup .5s ease forwards; }
.success-title { font-size: 22px; font-weight: 700; color: #1a1d2e; margin-bottom: 8px; }
.success-desc { font-size: 13px; color: #5f6368; margin-bottom: 28px; line-height: 1.6; }

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

.captcha-row { display: flex; gap: 10px; align-items: stretch; }
.captcha-input-wrap { flex: 1; }
.captcha-img {
  flex-shrink: 0; cursor: pointer; border-radius: 10px; overflow: hidden;
  display: flex; align-items: center; border: 1.5px solid #e8eaed;
}
.captcha-img img { height: 46px; display: block; }

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

.register-row { text-align: center; margin-top: 20px; font-size: 12px; color: #9aa0ab; }
.register-link { color: #1a73e8; font-weight: 500; cursor: pointer; transition: color .15s; }
.register-link:hover { color: #1558d6; }

.toasts {
  position: fixed; bottom: 28px; left: 50%; transform: translateX(-50%);
  z-index: 9999; display: flex; flex-direction: column; gap: 8px;
  align-items: center; pointer-events: none;
}

@media (max-width: 700px) {
  .brand-panel { display: none; }
  .card-wrap { grid-template-columns: 1fr; width: 95vw; }
}
</style>
