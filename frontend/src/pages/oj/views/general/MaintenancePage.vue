<template>
  <div class="maint-page">
    <div class="maint-bg-grid"></div>
    <div class="maint-bg-glow"></div>
    <div class="maint-particles" ref="particlesRef"></div>

    <div class="maint-container">
      <div class="maint-logo">
        <img class="maint-logo__icon" src="/logo.png" alt="Alethicode logo">
        <div class="maint-logo__text">Alethicode</div>
      </div>

      <h1 class="maint-title">系统升级维护中</h1>
      <p class="maint-subtitle">
        我们正在进行系统升级以提升您的使用体验。<br>
        升级期间所有服务暂停，您的数据不会受到影响。
      </p>

      <div class="maint-countdown-card">
        <div class="maint-countdown-label">预计恢复倒计时</div>
        <div class="maint-countdown-grid">
          <div v-if="countdown.days > 0" class="maint-countdown-unit">
            <span class="maint-countdown-number">{{ countdown.days }}</span>
            <span class="maint-countdown-text">天</span>
          </div>
          <div class="maint-countdown-unit">
            <span class="maint-countdown-number">{{ pad(countdown.hours) }}</span>
            <span class="maint-countdown-text">小时</span>
          </div>
          <div class="maint-countdown-unit">
            <span class="maint-countdown-number">{{ pad(countdown.minutes) }}</span>
            <span class="maint-countdown-text">分钟</span>
          </div>
          <div class="maint-countdown-unit">
            <span class="maint-countdown-number">{{ pad(countdown.seconds) }}</span>
            <span class="maint-countdown-text">秒</span>
          </div>
        </div>
      </div>

      <div class="maint-reopen-time">
        <span>预计开放时间：</span>
        <strong>{{ reopenDisplayStr }}</strong>
      </div>

      <div class="maint-progress">
        <div class="maint-progress__fill" :style="{ width: progressPct + '%' }"></div>
      </div>

      <div v-if="isDone" class="maint-done">
        升级已完成，正在为您跳转……
      </div>

      <router-link v-if="isDone" to="/" class="maint-action-link">
        进入 Alethicode →
      </router-link>
    </div>
  </div>
</template>

<script>
export default {
  name: 'MaintenancePage',
  props: {
    reopenTime: {
      type: String,
      default: ''
    }
  },
  data () {
    return {
      countdown: { days: 0, hours: 0, minutes: 0, seconds: 0 },
      progressPct: 0,
      isDone: false,
      rafId: null,
      reopenDate: null,
      startDate: new Date()
    }
  },
  computed: {
    reopenDisplayStr () {
      if (!this.reopenDate) return '--'
      return this.reopenDate.toLocaleString('zh-CN', {
        year: 'numeric', month: '2-digit', day: '2-digit',
        hour: '2-digit', minute: '2-digit', weekday: 'short'
      })
    }
  },
  mounted () {
    this.parseParams()
    this.tick()
    this.createParticles()
  },
  beforeUnmount () {
    if (this.rafId) cancelAnimationFrame(this.rafId)
  },
  methods: {
    parseParams () {
      const query = this.$route?.query || {}
      const reopenStr = query.reopen || this.reopenTime || '2026-05-02T14:00:00+08:00'
      this.reopenDate = new Date(reopenStr)
      if (isNaN(this.reopenDate.getTime())) {
        this.reopenDate = new Date(Date.now() + 3600000)
      }
      const startStr = query.start
      if (startStr) {
        const s = new Date(startStr)
        if (!isNaN(s.getTime())) this.startDate = s
      }
    },
    pad (n) {
      return n < 10 ? '0' + n : '' + n
    },
    tick () {
      const now = Date.now()
      const diff = this.reopenDate.getTime() - now

      if (diff <= 0) {
        this.countdown = { days: 0, hours: 0, minutes: 0, seconds: 0 }
        this.progressPct = 100
        this.isDone = true
        setTimeout(() => {
          this.$router.push('/').catch(() => {})
        }, 5000)
        return
      }

      this.countdown = {
        days: Math.floor(diff / 86400000),
        hours: Math.floor((diff % 86400000) / 3600000),
        minutes: Math.floor((diff % 3600000) / 60000),
        seconds: Math.floor((diff % 60000) / 1000)
      }

      const totalDuration = this.reopenDate.getTime() - this.startDate.getTime()
      const elapsed = now - this.startDate.getTime()
      this.progressPct = totalDuration > 0 ? Math.min(100, (elapsed / totalDuration) * 100) : 0

      this.rafId = requestAnimationFrame(() => this.tick())
    },
    createParticles () {
      const container = this.$refs.particlesRef
      if (!container) return
      const prefersReduced = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches
      if (prefersReduced) return

      for (let i = 0; i < 20; i++) {
        const p = document.createElement('div')
        p.className = 'maint-particle'
        p.style.left = Math.random() * 100 + '%'
        p.style.animationDuration = (8 + Math.random() * 12) + 's'
        p.style.animationDelay = Math.random() * 10 + 's'
        const size = (2 + Math.random() * 3) + 'px'
        p.style.width = size
        p.style.height = size
        p.style.background = Math.random() > 0.5 ? '#6366f1' : '#ec4899'
        container.appendChild(p)
      }
    }
  }
}
</script>

<style lang="less" scoped>
.maint-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  font-family: var(--font-sans);
  color: #f1f0f5;
  background: #0f0d1a;
  overflow: hidden;
  position: relative;
}

.maint-bg-grid {
  position: fixed;
  inset: 0;
  background-image:
    linear-gradient(rgba(99, 102, 241, 0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(99, 102, 241, 0.04) 1px, transparent 1px);
  background-size: 60px 60px;
  pointer-events: none;
}

.maint-bg-glow {
  position: fixed;
  width: 600px;
  height: 600px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(99, 102, 241, 0.12) 0%, transparent 70%);
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  pointer-events: none;
  animation: maint-glow 6s ease-in-out infinite;
}

@keyframes maint-glow {
  0%, 100% { opacity: 0.6; transform: translate(-50%, -50%) scale(1); }
  50% { opacity: 1; transform: translate(-50%, -50%) scale(1.1); }
}

.maint-particles {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 0;
}

:deep(.maint-particle) {
  position: absolute;
  width: 3px;
  height: 3px;
  border-radius: 50%;
  opacity: 0.3;
  animation: maint-float linear infinite;
}

@keyframes maint-float {
  0% { transform: translateY(100vh) scale(0); opacity: 0; }
  10% { opacity: 0.3; }
  90% { opacity: 0.1; }
  100% { transform: translateY(-10vh) scale(1.5); opacity: 0; }
}

.maint-container {
  position: relative;
  z-index: 1;
  max-width: 520px;
  width: 90%;
  text-align: center;
  padding: 20px;
}

.maint-logo {
  margin-bottom: 32px;
}

.maint-logo__icon {
  width: 80px;
  height: 80px;
  margin: 0 auto 16px;
  border-radius: 50%;
  display: block;
  object-fit: contain;
  box-shadow: 0 8px 32px rgba(99, 102, 241, 0.28);
  animation: maint-logo-float 4s ease-in-out infinite;
}

@keyframes maint-logo-float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-6px); }
}

.maint-logo__text {
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -0.5px;
}

.maint-title {
  font-size: 28px;
  font-weight: 800;
  line-height: 1.3;
  margin-bottom: 12px;
  background: linear-gradient(135deg, #6366f1, #ec4899);
  background-clip: text;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.maint-subtitle {
  font-size: 15px;
  color: rgba(241, 240, 245, 0.6);
  line-height: 1.7;
  margin-bottom: 36px;
}

.maint-countdown-card {
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(99, 102, 241, 0.2);
  border-radius: 16px;
  padding: 28px 24px;
  margin-bottom: 24px;
  backdrop-filter: blur(12px);
}

.maint-countdown-label {
  font-size: 13px;
  color: rgba(241, 240, 245, 0.6);
  margin-bottom: 16px;
  text-transform: uppercase;
  letter-spacing: 2px;
  font-weight: 600;
}

.maint-countdown-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(60px, 1fr));
  gap: 12px;
}

.maint-countdown-unit {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}

.maint-countdown-number {
  font-family: 'JetBrains Mono', 'Fira Code', 'SF Mono', Consolas, monospace;
  font-size: 42px;
  font-weight: 800;
  line-height: 1;
  background: linear-gradient(180deg, #f1f0f5 0%, rgba(241, 240, 245, 0.5) 100%);
  background-clip: text;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  min-width: 60px;
  text-align: center;
}

.maint-countdown-text {
  font-size: 11px;
  color: rgba(241, 240, 245, 0.6);
  font-weight: 500;
  letter-spacing: 1px;
  text-transform: uppercase;
}

.maint-reopen-time {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 14px;
  color: rgba(241, 240, 245, 0.6);
  margin-bottom: 36px;

  strong {
    color: #f1f0f5;
    font-weight: 600;
  }
}

.maint-progress {
  width: 100%;
  height: 4px;
  border-radius: 2px;
  background: rgba(255, 255, 255, 0.06);
  overflow: hidden;
  margin-bottom: 32px;
}

.maint-progress__fill {
  height: 100%;
  border-radius: 2px;
  background: linear-gradient(90deg, #6366f1, #ec4899);
  transition: width 1s linear;
}

.maint-done {
  padding: 16px 24px;
  background: rgba(16, 185, 129, 0.1);
  border: 1px solid rgba(16, 185, 129, 0.3);
  border-radius: 12px;
  font-size: 15px;
  font-weight: 600;
  color: #10b981;
  margin-bottom: 24px;
}

.maint-action-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  color: rgba(241, 240, 245, 0.6);
  text-decoration: none;
  padding: 10px 20px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 999px;
  transition: all 0.2s ease;

  &:hover {
    color: #f1f0f5;
    border-color: #6366f1;
    background: rgba(99, 102, 241, 0.08);
  }
}

@media (max-width: 480px) {
  .maint-title { font-size: 22px; }
  .maint-countdown-number { font-size: 32px; }
  .maint-logo__icon { width: 64px; height: 64px; }
}

@media (prefers-reduced-motion: reduce) {
  .maint-logo__icon, .maint-bg-glow { animation: none; }
}
</style>
