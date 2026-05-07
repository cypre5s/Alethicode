/**
 * 聚合 OJ 端所有 API 子模块并保持统一调用入口。
 */

import common from './api/common'
import account from './api/account'
import problem from './api/problem'
import submission from './api/submission'
import skill from './api/skill'
import notebook from './api/notebook'
import aiTutor from './api/aiTutor'
import classroom from './api/classroom'
import languagePack from './api/languagePack'
import profile from './api/profile'
import conversation from './api/conversation'
import parsons from './api/parsons'
import beta from './api/beta'

export default {
  ...common,
  ...account,
  ...problem,
  ...submission,
  ...skill,
  ...notebook,
  ...aiTutor,
  ...classroom,
  ...languagePack,
  ...profile,
  ...conversation,
  ...parsons,
  ...beta
}
