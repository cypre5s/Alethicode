/**
 * Alethicode 前端冒烟测试。
 * 运行：cd frontend && node tests/test_frontend_smoke.js
 */
const hljs = require('highlight.js')
const path = require('path')
const fs = require('fs')

let passed = 0
let failed = 0
const errors = []

function test(name, fn) {
  try {
    fn()
    console.log(`  PASS: ${name}`)
    passed++
  } catch (e) {
    console.log(`  FAIL: ${name} — ${e.message}`)
    errors.push([name, e.message])
    failed++
  }
}

function assert(cond, msg) {
  if (!cond) throw new Error(msg || 'assertion failed')
}

console.log('\n=== Alethicode Frontend Smoke Tests ===\n')

// 检查 highlight.js。
console.log('--- highlight.js ---')

test('hljs: Python highlighting works', () => {
  const r = hljs.highlight('print("hello")', { language: 'python' })
  assert(r.value.includes('span'), 'should contain markup')
})

test('hljs: highlightAuto does not crash', () => {
  const r = hljs.highlightAuto('def foo(): pass')
  assert(r.value.length > 0)
})

test('hljs: wrapped highlight handles unknown lang gracefully', () => {
  function safe(code, lang) {
    try {
      if (lang && hljs.getLanguage(lang)) return hljs.highlight(code, { language: lang }).value
      return hljs.highlightAuto(code).value
    } catch (_) { return code }
  }
  const r = safe('some code', 'nonexistent_xyz')
  assert(typeof r === 'string')
})

test('hljs: Chinese content does not break highlighting', () => {
  const code = 'import tkinter\nroot.title("商品单选与价格回显")\n'
  const r = hljs.highlightAuto(code)
  assert(r.value.length > 0)
})

// 检查关键文件是否存在。
console.log('\n--- Key Files ---')

const keyFiles = [
  'src/pages/oj/views/problem/Problem.vue',
  'src/pages/oj/components/CodeMirror.vue',
  'src/pages/oj/api.js',
  'src/plugins/highlight.js',
  'src/pages/oj/views/submission/SubmissionDetails.vue',
  'src/pages/oj/views/classroom/ClassroomList.vue',
]

for (const f of keyFiles) {
  test(`File exists: ${f}`, () => {
    assert(fs.existsSync(path.join(__dirname, '..', f)), `Missing: ${f}`)
  })
}

// 检查 API 模块结构。
console.log('\n--- API Module ---')

test('api.js exports skill profile methods', () => {
  const content = fs.readFileSync(path.join(__dirname, '..', 'src/pages/oj/api.js'), 'utf8')
  assert(content.includes('getSkillRadar'), 'missing getSkillRadar')
  assert(content.includes('getProblemRecommendations'), 'missing getProblemRecommendations')
})

// 检查组件文件。
console.log('\n--- Component Checks ---')

test('CodeMirror.vue has extra-tools slot', () => {
  const content = fs.readFileSync(
    path.join(__dirname, '..', 'src/pages/oj/components/CodeMirror.vue'), 'utf8')
  assert(content.includes('extra-tools'), 'should have extra-tools slot')
})

test('UserHome hides radar card', () => {
  const content = fs.readFileSync(
    path.join(__dirname, '..', 'src/pages/oj/views/user/UserHome.vue'), 'utf8')
  assert(!content.includes('import SkillRadar'), 'should not import SkillRadar')
  assert(!content.includes('<SkillRadar'), 'should not render SkillRadar')
  assert(!content.includes('技能雷达'), 'should not render radar tab')
  assert(!content.includes('ForgottenSkills'), 'ForgottenSkills should be removed')
})

test('UserHome has bottom-grid with recent submissions and misconceptions', () => {
  const content = fs.readFileSync(
    path.join(__dirname, '..', 'src/pages/oj/views/user/UserHome.vue'), 'utf8')
  assert(content.includes('最近提交'), 'should have recent submissions card')
  assert(content.includes('我的易错点'), 'should have misconceptions card')
})

test('SubmissionDetails sanitizes AI guidance HTML before v-html', () => {
  const content = fs.readFileSync(
    path.join(__dirname, '..', 'src/pages/oj/views/submission/SubmissionDetails.vue'), 'utf8')
  assert(content.includes("import { sanitize } from '@/utils/sanitize'"), 'should import sanitize helper')
  assert(content.includes('sanitize(marked('), 'should sanitize rendered markdown html')
})

test('highlight.js plugin has try-catch wrapping', () => {
  const content = fs.readFileSync(
    path.join(__dirname, '..', 'src/plugins/highlight.js'), 'utf8')
  assert(content.includes('try {') || content.includes('try{'), 'should have try-catch')
})

console.log(`\n${'='.repeat(50)}`)
console.log(`TOTAL: ${passed + failed} | PASS: ${passed} | FAIL: ${failed}`)
if (errors.length) {
  console.log('\nFailed tests:')
  errors.forEach(([n, e]) => console.log(`  - ${n}: ${e}`))
}
console.log('='.repeat(50))
process.exit(failed > 0 ? 1 : 0)
