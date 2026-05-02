<template>
  <div class="login-wrapper">
    <canvas ref="particleCanvas" class="particle-canvas"></canvas>
    <div class="login-surface">
      <section class="brand-panel">
        <div class="brand-logo">
          <img class="brand-logo-icon" src="/logo.png" alt="Alethicode logo">
          <span class="brand-logo-text">Alethicode</span>
        </div>
        <p class="brand-caption">Alethicode 管理台</p>
        <h1 class="brand-title">教学管理入口</h1>
        <p class="brand-subtitle">统一处理题库、用户与教学运营，让后台和 OJ 前台保持一致的教学体验。</p>
        <div class="shape-row">
          <span class="shape shape-primary"></span>
          <span class="shape shape-accent"></span>
          <span class="shape shape-dark"></span>
        </div>
      </section>

      <section class="form-panel">
        <el-form
          :model="ruleForm2"
          :rules="rules2"
          ref="ruleForm2"
          label-position="left"
          label-width="0px"
          class="demo-ruleForm login-container"
        >
          <h3 class="title">{{$t('m.Welcome_to_Login')}}</h3>
          <el-form-item prop="account">
            <el-input
              type="text"
              v-model="ruleForm2.account"
              auto-complete="off"
              placeholder="用户名"
              @keyup.enter="handleLogin"
            ></el-input>
          </el-form-item>
          <el-form-item prop="password">
            <el-input
              type="password"
              v-model="ruleForm2.password"
              auto-complete="off"
              placeholder="密码"
              @keyup.enter="handleLogin"
            ></el-input>
          </el-form-item>
          <el-form-item style="width:100%;">
            <el-button type="primary" style="width:100%;" @click.prevent="handleLogin" :loading="logining">
              登录管理台
            </el-button>
          </el-form-item>
        </el-form>
      </section>
    </div>
  </div>
</template>

<script>
  import api from '../../api'

  export default {
    name: 'Login',
    data () {
      return {
        logining: false,
        ruleForm2: {
          account: '',
          password: ''
        },
        rules2: {
          account: [
            {required: true, trigger: 'blur'}
          ],
          password: [
            {required: true, trigger: 'blur'}
          ]
        },
        checked: true,
        _raf: null
      }
    },
    mounted () {
      this.initParticles()
      api.csrf().catch(() => {})
      this._onResize = () => this.resizeCanvas()
      window.addEventListener('resize', this._onResize)
    },
    beforeUnmount () {
      if (this._raf) cancelAnimationFrame(this._raf)
      window.removeEventListener('resize', this._onResize)
    },
    methods: {
      resizeCanvas () {
        const canvas = this.$refs.particleCanvas
        if (!canvas) return
        canvas.width = window.innerWidth
        canvas.height = window.innerHeight
      },
      initParticles () {
        const canvas = this.$refs.particleCanvas
        if (!canvas) return
        const ctx = canvas.getContext('2d')
        this.resizeCanvas()

        const PARTICLE_COUNT = 60
        const MAX_CONNECT_DIST = 120
        const particles = []

        for (let i = 0; i < PARTICLE_COUNT; i++) {
          particles.push({
            x: Math.random() * canvas.width,
            y: Math.random() * canvas.height,
            vx: (Math.random() - 0.5) * 0.6,
            vy: (Math.random() - 0.5) * 0.6,
            r: Math.random() * 2.5 + 1,
            opacity: Math.random() * 0.4 + 0.15
          })
        }

        const animate = () => {
          ctx.clearRect(0, 0, canvas.width, canvas.height)

          for (let i = 0; i < particles.length; i++) {
            const p = particles[i]
            p.x += p.vx
            p.y += p.vy

            if (p.x < 0) p.x = canvas.width
            if (p.x > canvas.width) p.x = 0
            if (p.y < 0) p.y = canvas.height
            if (p.y > canvas.height) p.y = 0

            ctx.beginPath()
            ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2)
            ctx.fillStyle = `rgba(37, 99, 235, ${p.opacity})`
            ctx.fill()

            for (let j = i + 1; j < particles.length; j++) {
              const q = particles[j]
              const dx = p.x - q.x
              const dy = p.y - q.y
              const dist = Math.sqrt(dx * dx + dy * dy)
              if (dist < MAX_CONNECT_DIST) {
                ctx.beginPath()
                ctx.moveTo(p.x, p.y)
                ctx.lineTo(q.x, q.y)
                ctx.strokeStyle = `rgba(37, 99, 235, ${0.12 * (1 - dist / MAX_CONNECT_DIST)})`
                ctx.lineWidth = 0.8
                ctx.stroke()
              }
            }
          }

          this._raf = requestAnimationFrame(animate)
        }

        animate()
      },
      handleLogin (ev) {
        this.$refs.ruleForm2.validate((valid) => {
          if (valid) {
            this.logining = true
            api.login(this.ruleForm2.account, this.ruleForm2.password).then(data => {
              this.logining = false
              this.$router.push({name: 'problem-list'})
            }, () => {
              this.logining = false
            })
          } else {
            this.$error('请先完整填写用户名和密码')
          }
        })
      }
    }
  }
</script>

<style lang="less" scoped>
  .login-wrapper {
    --theme-primary: var(--primary-color, #2563eb);
    --contrast-accent: #ff8a00;
    --ink: #0f172a;
    position: fixed;
    inset: 0;
    background: #f8fafc;
    display: flex;
    justify-content: center;
    align-items: center;
    padding: 20px;
    overflow: hidden;
  }

  .particle-canvas {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    pointer-events: none;
    z-index: 0;
  }

  .login-surface {
    position: relative;
    z-index: 1;
    width: 100%;
    max-width: 980px;
    min-height: 580px;
    border-radius: 20px;
    overflow: hidden;
    background: #ffffff;
    box-shadow: 0 22px 60px rgba(15, 23, 42, 0.12);
    border: 1px solid rgba(15, 23, 42, 0.06);
    display: grid;
    grid-template-columns: 1fr 1fr;
  }

  .brand-panel {
    background: var(--theme-primary);
    color: #ffffff;
    padding: 56px 48px;
    display: flex;
    flex-direction: column;
    justify-content: center;
    gap: 20px;
  }

  .brand-logo {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 4px;
  }

  .brand-logo-icon {
    width: 44px;
    height: 44px;
    border-radius: 12px;
    background: rgba(255, 255, 255, 0.95);
    padding: 4px;
    object-fit: contain;
    box-shadow: 0 6px 16px rgba(0, 0, 0, 0.18);
  }

  .brand-logo-text {
    font-size: 16px;
    font-weight: 700;
    color: rgba(255, 255, 255, 0.95);
    letter-spacing: 1.5px;
  }

  .brand-caption {
    margin: 0;
    font-size: 12px;
    letter-spacing: 0.18em;
    text-transform: uppercase;
    opacity: 0.85;
  }

  .brand-title {
    margin: 0;
    font-size: 56px;
    line-height: 1.05;
    font-weight: 800;
    letter-spacing: -0.03em;
  }

  .brand-subtitle {
    margin: 0;
    max-width: 360px;
    font-size: 15px;
    line-height: 1.6;
    color: rgba(255, 255, 255, 0.86);
  }

  .shape-row {
    margin-top: 8px;
    display: flex;
    gap: 14px;
  }

  .shape {
    display: inline-block;
    border-radius: 12px;
  }

  .shape-primary {
    width: 64px;
    height: 64px;
    background: rgba(255, 255, 255, 0.26);
  }

  .shape-accent {
    width: 92px;
    height: 36px;
    border-radius: 999px;
    background: var(--contrast-accent);
  }

  .shape-dark {
    width: 30px;
    height: 76px;
    border-radius: 999px;
    background: rgba(15, 23, 42, 0.8);
  }

  .form-panel {
    position: relative;
    background: linear-gradient(160deg, #fff7ed 0%, #ffffff 38%);
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 42px;
  }

  .form-panel::before {
    content: "";
    position: absolute;
    top: 34px;
    right: 34px;
    width: 46px;
    height: 46px;
    border-radius: 50%;
    background: var(--contrast-accent);
    opacity: 0.84;
  }

  .login-container {
    width: 100%;
    max-width: 360px;
    padding: 0;
    background: transparent;
    border: 0;
    box-shadow: none;

    .title {
      margin: 0 0 32px 0;
      text-align: left;
      color: var(--ink);
      font-weight: 700;
      font-size: 28px;
      letter-spacing: -0.02em;
    }

    :deep(.el-input__inner ) {
      height: 44px;
      border-radius: 12px;
      border: 1px solid #dbe4f0;
      background: #ffffff;
      transition: border-color 0.2s ease, box-shadow 0.2s ease;
    }

    :deep(.el-input__inner:focus ) {
      border-color: var(--theme-primary);
      box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.15);
    }

    :deep(.el-button--primary ) {
      height: 44px;
      border-radius: 999px;
      font-size: 14px;
      font-weight: 700;
      border: 0;
      background: linear-gradient(90deg, var(--theme-primary) 0%, #3b82f6 100%);
      box-shadow: 0 10px 20px rgba(37, 99, 235, 0.28);
      transition: transform 0.2s ease, box-shadow 0.2s ease;
    }

    :deep(.el-button--primary:hover ) {
      transform: translateY(-1px);
      box-shadow: 0 14px 24px rgba(37, 99, 235, 0.32);
    }
  }

  @media (max-width: 920px) {
    .brand-title {
      font-size: 42px;
    }

    .login-surface {
      min-height: auto;
      grid-template-columns: 1fr;
    }

    .brand-panel {
      padding: 36px 28px;
    }

    .form-panel {
      padding: 28px;
    }
  }

  @media (max-width: 480px) {
    .brand-title {
      font-size: 34px;
    }
  }
</style>
