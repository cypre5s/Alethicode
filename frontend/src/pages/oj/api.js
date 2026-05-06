/**
 * OJ 前端 API 入口。
 *
 * 本文件是 `@oj/api` 的统一导出点，具体业务方法按"业务域"分散到 `./api/*.js` 里：
 *
 * | 子模块                 | 负责的业务                              |
 * |------------------------|-----------------------------------------|
 * | `./api/common`         | 站点配置 / CSRF / 公告 / 编程语言列表   |
 * | `./api/account`        | 登录 / 注册 / 资料 / 2FA / 会话 / 密码  |
 * | `./api/problem`        | 题目列表 / 详情 / 标签 / 相关例题       |
 * | `./api/submission`     | 提交 / 调试 / 重判 / 列表               |
 * | `./api/skill`          | 技能画像（雷达 / 热力图 / 推荐）        |
 * | `./api/notebook`       | 学习笔记本                              |
 * | `./api/aiTutor`        | AI 导学、Ideate、Frustration、Workflow  |
 * | `./api/classroom`      | 班级 / 邀请 / 成员 / 题目 / 作业 / 监测 |
 * | `./api/languagePack`   | 语言包 / 课件 QA / 课程结构             |
 *
 * 合并方式：所有子模块各自 `export default {...methods}`，这里用对象展开语法组合到一起。
 * 对外保持与历史 `api.js` 完全一致的调用形态（`import api from '@oj/api'`; `api.foo()`），
 * 因此全站调用方无需做任何改动。`this.xxx` 类别名方法仍可在合并后对象内互相访问。
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
import career from './api/career'

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
  ...beta,
  ...career
}
