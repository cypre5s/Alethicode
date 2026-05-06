// all routes here.
import {
  ApplyResetPassword,
  Home,
  Logout,
  NotFound,
  Problem,
  ProblemList,
  ResetPassword,
  SubmissionDetails,
  SubmissionList,
  UserHome,
  LearnerNotebook,
  ClassroomList,
  ClassroomDetail,
  JoinClassroom,
  LanguagePackQaPage,
  PdfViewerPage,
  ErrorReviewPackagePage,
  ManualPage,
  MaintenancePage,
  CareerProfilePage,
  CareerReportPage,
  MicroProjectListPage,
  MicroProjectDetailPage,
  CareerPreferencesPanel
} from '../views'

import * as Setting from '@oj/views/setting'
const LoginPage = () => import('@oj/views/user/StandaloneLogin.vue')
const RegisterPage = () => import('@oj/views/user/StandaloneRegister.vue')

export default [
  {
    name: 'login',
    path: '/login',
    meta: { title: 'Login' },
    component: LoginPage
  },
  {
    name: 'register',
    path: '/register',
    meta: { title: 'Register' },
    component: RegisterPage
  },
  {
    name: 'home',
    path: '/',
    meta: { title: 'Home' },
    component: Home
  },
  {
    name: 'logout',
    path: '/logout',
    meta: { title: 'Logout' },
    component: Logout
  },
  {
    name: 'apply-reset-password',
    path: '/apply-reset-password',
    meta: { title: 'Apply Reset Password' },
    component: ApplyResetPassword
  },
  {
    name: 'reset-password',
    path: '/reset-password/:token',
    meta: { title: 'Reset Password' },
    component: ResetPassword
  },
  {
    name: 'problem-list',
    path: '/problem',
    meta: { title: 'Problem List' },
    component: ProblemList
  },
  {
    name: 'problem-details',
    path: '/problem/:problemID',
    meta: { title: 'Problem Details' },
    component: Problem
  },
  {
    name: 'manual',
    path: '/guide',
    meta: { title: '新手指南' },
    component: ManualPage
  },
  {
    name: 'submission-list',
    path: '/status',
    meta: { title: 'Submission List' },
    component: SubmissionList
  },
  {
    name: 'submission-details',
    path: '/status/:id/',
    meta: { title: 'Submission Details' },
    component: SubmissionDetails
  },
  {
    name: 'user-home',
    path: '/user-home',
    component: UserHome,
    meta: { requiresAuth: true, title: 'User Home' }
  },
  {
    name: 'learner-notebook',
    path: '/learner-notebook',
    component: LearnerNotebook,
    meta: { requiresAuth: true, title: 'Learner Notebook' }
  },
  {
    path: '/setting',
    component: Setting.Settings,
    children: [
      {
        name: 'default-setting',
        path: '',
        meta: { requiresAuth: true, title: 'Default Settings' },
        component: Setting.ProfileSetting
      },
      {
        name: 'profile-setting',
        path: 'profile',
        meta: { requiresAuth: true, title: 'Profile Settings' },
        component: Setting.ProfileSetting
      },
      {
        name: 'account-setting',
        path: 'account',
        meta: { requiresAuth: true, title: 'Account Settings' },
        component: Setting.AccountSetting
      },
      {
        name: 'career-setting',
        path: 'career',
        meta: { requiresAuth: true, title: 'Career 模块设置' },
        component: CareerPreferencesPanel
      }
    ]
  },
  {
    path: '/classroom',
    name: 'classroom-list',
    meta: { title: '我的班级', requiresAuth: true },
    component: ClassroomList
  },
  {
    path: '/classroom/join',
    name: 'classroom-join',
    meta: { title: '加入班级', requiresAuth: true },
    component: JoinClassroom
  },
  {
    path: '/classroom/detail',
    name: 'classroom-detail',
    meta: { title: '班级详情', requiresAuth: true },
    component: ClassroomDetail
  },
  {
    path: '/language-pack-qa',
    name: 'language-pack-qa',
    meta: { title: '课件问答助手', requiresAuth: true },
    component: LanguagePackQaPage
  },
  {
    path: '/language-pack-qa/viewer',
    name: 'pdf-viewer',
    meta: { title: '课件页预览', requiresAuth: true },
    component: PdfViewerPage
  },
  {
    path: '/review-package',
    name: 'error-review-package',
    meta: { title: '专项复习', requiresAuth: true },
    component: ErrorReviewPackagePage
  },
  {
    path: '/career/profile',
    name: 'career-profile',
    meta: { title: '专业档案', requiresAuth: true },
    component: CareerProfilePage
  },
  {
    path: '/career/reports',
    name: 'career-reports',
    meta: { title: '专业报告', requiresAuth: true },
    component: CareerReportPage
  },
  {
    path: '/career/studio',
    name: 'career-studio',
    meta: { title: '微项目工作室', requiresAuth: true },
    component: MicroProjectListPage
  },
  {
    path: '/career/studio/projects/:projectId',
    name: 'career-project-detail',
    meta: { title: '微项目详情', requiresAuth: true },
    component: MicroProjectDetailPage
  },
  {
    name: 'maintenance',
    path: '/maintenance',
    meta: { title: '系统维护中' },
    component: MaintenancePage
  },
  {
    path: '/:pathMatch(.*)*',
    meta: { title: '404' },
    component: NotFound
  }
]
