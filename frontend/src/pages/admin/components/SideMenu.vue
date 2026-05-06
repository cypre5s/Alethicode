<template>
  <div class="admin-side-shell">
    <div class="admin-side-brand">
      <img class="admin-side-brand-icon" src="/logo.png" alt="Alethicode logo">
      <span class="admin-side-brand-text">Alethicode</span>
    </div>
    <el-menu class="vertical_menu admin-side-menu"
             :router="true"
             :default-active="currentPath">
      <el-sub-menu v-if="adminManager" index="system">
        <template #title>
          <span class="menu-title-content">
            <i class="el-icon-setting menu-leading-icon"></i>
            <span class="menu-title-text">{{$t('m.System_Admin')}}</span>
          </span>
        </template>
        <el-menu-item index="/user">{{$t('m.User')}}</el-menu-item>
        <el-menu-item index="/announcement">{{$t('m.Announcement')}}</el-menu-item>
        <el-menu-item index="/secrets/ai">AI 服务配置</el-menu-item>
      </el-sub-menu>

      <el-sub-menu v-if="adminManager" index="insights">
        <template #title>
          <span class="menu-title-content">
            <i class="el-icon-fa-line-chart menu-leading-icon"></i>
            <span class="menu-title-text">数据洞察</span>
          </span>
        </template>
        <el-menu-item index="/usage-stats">学生学习数据</el-menu-item>
        <el-menu-item index="/secrets/observability">辅导总控</el-menu-item>
        <el-menu-item index="/secrets/system-monitor">系统监控</el-menu-item>
        <el-menu-item index="/beta-feedback">公测反馈</el-menu-item>
      </el-sub-menu>

      <!-- 密钥与配置：暂时隐藏 -->
      <!--
      <el-sub-menu v-if="adminManager" index="secrets">
        <template #title>
          <span class="menu-title-content">
            <i class="el-icon-fa-key menu-leading-icon"></i>
            <span class="menu-title-text">密钥与配置</span>
          </span>
        </template>
        <el-menu-item index="/secrets/paths">系统路径</el-menu-item>
        <el-menu-item index="/secrets/infra">数据库与基础设施</el-menu-item>
      </el-sub-menu>
      -->

      <el-sub-menu v-if="adminManager" index="judge">
        <template #title>
          <span class="menu-title-content">
            <i class="el-icon-fa-cogs menu-leading-icon"></i>
            <span class="menu-title-text">{{$t('m.Judge_Admin')}}</span>
          </span>
        </template>
        <el-menu-item index="/judge-server">{{$t('m.Judge_Server')}}</el-menu-item>
        <el-menu-item index="/prune-test-case">{{$t('m.Prune_Test_Case')}}</el-menu-item>
      </el-sub-menu>

      <el-sub-menu v-if="canAccessAiTeaching" index="ai-teaching">
        <template #title>
          <span class="menu-title-content">
            <i class="el-icon-fa-graduation-cap menu-leading-icon"></i>
            <span class="menu-title-text">{{$t('m.AI_Teaching')}}</span>
          </span>
        </template>
        <el-menu-item index="/kc-management">知识图谱管理</el-menu-item>
        <el-menu-item index="/ai-variant-review">AI 变体题审核</el-menu-item>
        <el-menu-item index="/language-pack-init">课程内容包管理</el-menu-item>
      </el-sub-menu>

      <el-sub-menu index="problem" v-if="showProblemMenu">
        <template #title>
          <span class="menu-title-content">
            <i class="el-icon-fa-bars menu-leading-icon"></i>
            <span class="menu-title-text">{{$t('m.Problem')}}</span>
          </span>
        </template>
        <el-menu-item index="/problems">{{$t('m.Problem_List')}}</el-menu-item>
        <el-menu-item index="/problem/create">{{$t('m.Create_Problem')}}</el-menu-item>
        <el-menu-item index="/problem/batch_ops">{{$t('m.Export_Import_Problem')}}</el-menu-item>
      </el-sub-menu>

    </el-menu>

    <div class="menu-footer">
      <button class="logout-btn" type="button" @click="handleLogout">
        <span>退出登录</span>
      </button>
    </div>
  </div>
</template>

<script>
  import { mapGetters } from 'vuex'
  import api from '@admin/api'

  export default {
    name: 'SideMenu',
    computed: {
      ...mapGetters(['user', 'hasProblemPermission', 'isAdminRole']),
      currentPath () {
        return this.$route.path
      },
      adminManager () {
        return this.user && this.user.admin_type === 'Admin'
      },
      isTeacher () {
        return this.user && this.user.admin_type === 'Teacher'
      },
      canAccessAiTeaching () {
        return this.isAdminRole
      },
      showProblemMenu () {
        return this.hasProblemPermission || this.isTeacher
      }
    },
    methods: {
      handleLogout () {
        api.logout().finally(() => {
          this.$router.push({ name: 'login' })
        })
      }
    }
  }
</script>

<style scoped lang="less">
  .admin-side-shell {
    width: 240px;
    height: 100%;
    position: fixed;
    z-index: 100;
    top: 0;
    bottom: 0;
    left: 0;
    display: flex;
    flex-direction: column;
    background: rgba(255, 255, 255, 0.92);
    backdrop-filter: blur(18px);
    border-right: 1px solid rgba(148, 163, 184, 0.14);
    box-shadow: 10px 0 30px rgba(15, 23, 42, 0.04);
  }

  .admin-side-brand {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 18px 20px;
    border-bottom: 1px solid rgba(148, 163, 184, 0.18);
    background: rgba(255, 255, 255, 0.6);
  }

  .admin-side-brand-icon {
    width: 36px;
    height: 36px;
    object-fit: contain;
    flex-shrink: 0;
  }

  .admin-side-brand-text {
    font-size: 16px;
    font-weight: 700;
    color: #0f172a;
    letter-spacing: 0.5px;
  }

  .vertical_menu {
    flex: 1;
    overflow: auto;
    border-right: 0;
    background: transparent;
    padding-top: 12px;
  }

  .menu-footer {
    border-top: 1px solid rgba(148, 163, 184, 0.18);
    padding: 12px;
    background: rgba(255, 255, 255, 0.9);
  }

  .logout-btn {
    width: 100%;
    border: 0;
    border-radius: 12px;
    height: 42px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    background: rgba(239, 68, 68, 0.1);
    color: #dc2626;
    font-weight: 600;
    cursor: pointer;
    transition: all 0.2s ease;

    &:hover {
      background: rgba(239, 68, 68, 0.16);
      color: #b91c1c;
    }
  }

  :deep(.el-menu-item),
  :deep(.el-submenu__title) {
    height: 46px;
    line-height: 46px;
    margin: 4px 12px;
    padding: 0 16px !important;
    border-radius: 12px;
    color: #475569;
    font-size: 15px;
    display: flex;
    align-items: center;

    &:hover {
      background-color: rgba(37, 99, 235, 0.08);
      color: #2563eb;

      .menu-leading-icon,
      .el-submenu__icon-arrow {
        color: #2563eb;
      }
    }
  }

  :deep(.menu-title-content) {
    display: inline-flex;
    align-items: center;
    min-width: 0;
  }

  :deep(.menu-leading-icon) {
    width: 18px;
    margin-right: 10px;
    color: #94a3b8;
    font-size: 16px;
    text-align: center;
    transition: color 0.3s;
    flex-shrink: 0;
  }

  :deep(.menu-title-text) {
    font-size: 16px;
    line-height: 1;
    color: inherit;
  }

  :deep(.el-submenu__icon-arrow) {
    margin-right: 0;
    color: #94a3b8;
    transition: color 0.3s;
  }

  :deep(.el-menu-item.is-active) {
    background: linear-gradient(90deg, #2563eb 0%, #3b82f6 100%);
    color: #fff;
    box-shadow: 0 12px 24px rgba(37, 99, 235, 0.22);

    .menu-leading-icon {
      color: #fff;
    }
  }
</style>
