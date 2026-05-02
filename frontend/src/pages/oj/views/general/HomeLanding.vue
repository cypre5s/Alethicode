<template>
  <div class="landing">
    <section class="hero">
      <h1 class="hero-title">让编程学习更简单</h1>
      <p class="hero-sub">AI 辅导 · 课件体系 · 智能复习</p>
      <button class="hero-cta" @click="goRegister">免费注册，开始学习</button>
      <p class="hero-login">已有账号？<a href="/login" @click.prevent="goLogin">登录</a></p>
    </section>

    <section class="features">
      <div class="feature-card">
        <svg class="feature-icon" viewBox="0 0 24 24" width="36" height="36" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
        <h3 class="feature-title">AI 智能辅导</h3>
        <p class="feature-desc">AI 导师全程陪练，精准诊断你的编程错误，给出个性化指导。</p>
      </div>
      <div class="feature-card">
        <svg class="feature-icon" viewBox="0 0 24 24" width="36" height="36" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>
        <h3 class="feature-title">课件体系</h3>
        <p class="feature-desc">结构化课程配合知识图谱，逐步进阶，系统掌握 Python。</p>
      </div>
      <div class="feature-card">
        <svg class="feature-icon" viewBox="0 0 24 24" width="36" height="36" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><polyline points="23 4 23 10 17 10"/><polyline points="1 20 1 14 7 14"/><path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/></svg>
        <h3 class="feature-title">错题复习</h3>
        <p class="feature-desc">间隔重复算法驱动，精准消灭薄弱点，越练越轻松。</p>
      </div>
    </section>

    <section class="bottom-cta">
      <h2 class="bottom-title">准备好了吗？</h2>
      <button class="hero-cta" @click="goRegister">免费注册</button>
    </section>
  </div>
</template>

<script>
  export default {
    name: 'HomeLanding',
    methods: {
      goRegister () {
        this.$router.push('/register')
      },
      goLogin () {
        this.$router.push('/login')
      }
    }
  }
</script>

<style lang="less" scoped>
  .landing {
    min-height: 100vh;
    background: linear-gradient(180deg, #f7f9fc 0%, #fff 50%, #f7f9fc 100%);
  }

  /* ── Hero ── */
  .hero {
    display: flex;
    flex-direction: column;
    align-items: center;
    text-align: center;
    padding: 100px 24px 72px;
  }
  .hero-title {
    font-size: 42px;
    font-weight: 800;
    color: #1a1a1a;
    margin: 0 0 16px;
    line-height: 1.2;
    letter-spacing: -0.02em;
  }
  .hero-sub {
    font-size: 18px;
    color: #555;
    margin: 0 0 36px;
    letter-spacing: 0.08em;
  }
  .hero-cta {
    height: 48px;
    padding: 0 36px;
    border: none;
    border-radius: 10px;
    background: linear-gradient(135deg, #1a73e8, #1557b0);
    color: #fff;
    font-size: 16px;
    font-weight: 700;
    cursor: pointer;
    transition: transform 0.15s, box-shadow 0.2s;
    box-shadow: 0 4px 14px rgba(26, 115, 232, 0.3);
    &:hover {
      transform: translateY(-2px);
      box-shadow: 0 6px 20px rgba(26, 115, 232, 0.4);
    }
    &:active { transform: scale(0.97); }
  }
  .hero-login {
    margin-top: 16px;
    font-size: 14px;
    color: #888;
    a {
      color: #1a73e8;
      font-weight: 600;
      text-decoration: none;
      &:hover { text-decoration: underline; }
    }
  }

  /* ── Features ── */
  .features {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 24px;
    max-width: 900px;
    margin: 0 auto;
    padding: 0 24px 80px;
  }
  .feature-card {
    display: flex;
    flex-direction: column;
    align-items: center;
    text-align: center;
    padding: 32px 24px;
    background: #fff;
    border: 1px solid #eaedf0;
    border-radius: 14px;
    transition: box-shadow 0.2s, transform 0.15s;
    &:hover {
      box-shadow: 0 6px 24px rgba(0, 0, 0, 0.06);
      transform: translateY(-3px);
    }
  }
  .feature-icon {
    color: #1a73e8;
    margin-bottom: 16px;
    flex-shrink: 0;
  }
  .feature-title {
    font-size: 17px;
    font-weight: 700;
    color: #1a1a1a;
    margin: 0 0 10px;
  }
  .feature-desc {
    font-size: 14px;
    color: #666;
    line-height: 1.65;
    margin: 0;
  }

  /* ── Bottom CTA ── */
  .bottom-cta {
    text-align: center;
    padding: 48px 24px 80px;
  }
  .bottom-title {
    font-size: 24px;
    font-weight: 700;
    color: #1a1a1a;
    margin: 0 0 24px;
  }

  /* ── Responsive ── */
  @media (max-width: 640px) {
    .hero { padding: 60px 20px 48px; }
    .hero-title { font-size: 28px; }
    .hero-sub { font-size: 15px; }
    .features { grid-template-columns: 1fr; padding: 0 20px 48px; }
    .bottom-cta { padding: 32px 20px 60px; }
  }
</style>
