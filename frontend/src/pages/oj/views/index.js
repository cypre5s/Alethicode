const ProblemList = () => import('./problem/ProblemList.vue')
const Logout = () => import('./user/Logout.vue')
const UserHome = () => import('./user/UserHome.vue')
const NotFound = () => import('./general/NotFound.vue')
const Home = () => import('./general/Home.vue')
const Announcements = () => import('./general/Announcements.vue')
const LearnerNotebook = () => import('./user/LearnerNotebook.vue')

const SubmissionList = () => import('./submission/SubmissionList.vue')
const SubmissionDetails = () => import('./submission/SubmissionDetails.vue')

const ApplyResetPassword = () => import('./user/ApplyResetPassword.vue')
const ResetPassword = () => import('./user/ResetPassword.vue')

const Problem = () => import('./problem/Problem.vue')

const ManualPage = () => import('./manual/ManualPage.vue')
const MaintenancePage = () => import('./general/MaintenancePage.vue')

export {
  Home, NotFound, Announcements,
  Logout, UserHome,
  LearnerNotebook,
  ProblemList, Problem,
  SubmissionList, SubmissionDetails,
  ApplyResetPassword, ResetPassword,
  ManualPage,
  MaintenancePage
}

export * from './classroom'
export * from './languagepack'
export * from './review'
export * from './career'
