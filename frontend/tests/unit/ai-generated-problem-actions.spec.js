/* eslint-env jest */

const babel = require('@babel/core')
const fs = require('fs')
const path = require('path')
const vm = require('vm')

const sourcePath = path.resolve(__dirname, '../../src/pages/oj/views/classroom/aiGeneratedProblemActions.js')

function loadAiGeneratedProblemActions() {
  const transformed = babel.transformFileSync(sourcePath, {
    configFile: path.resolve(__dirname, '../../babel.config.js')
  })
  const compiledModule = { exports: {} }
  vm.runInNewContext(transformed.code, {
    module: compiledModule,
    exports: compiledModule.exports,
    require,
    __filename: sourcePath,
    __dirname: path.dirname(sourcePath)
  }, {
    filename: sourcePath
  })

  return compiledModule.exports
}

const {
  isLessonSupportedForAiGeneration,
  canEditAiGeneratedProblem,
  canDeleteAiGeneratedProblem,
  canReviewPassAiGeneratedProblem,
  canReviewRejectAiGeneratedProblem,
  canValidateAiGeneratedProblem,
  canPublishAiGeneratedProblem
} = loadAiGeneratedProblemActions()

describe('aiGeneratedProblemActions', () => {
  test('source module should use named esm exports for vite imports', () => {
    const sourceCode = fs.readFileSync(sourcePath, 'utf8')

    expect(sourceCode).not.toMatch(/module\.exports\s*=/)
    expect(sourceCode).toMatch(/export\s*\{/)
  })

  test('only ppt and pdf lessons should be eligible for ai generation', () => {
    expect(isLessonSupportedForAiGeneration({ lesson_type: 'ppt' })).toBe(true)
    expect(isLessonSupportedForAiGeneration({ lesson_type: 'pdf' })).toBe(true)
    expect(isLessonSupportedForAiGeneration({ lesson_type: 'doc' })).toBe(false)
  })

  test('objective problems should only allow first review actions while pending', () => {
    const problem = { question_type: 'choice', status: 'pending' }

    expect(canEditAiGeneratedProblem(problem)).toBe(true)
    expect(canDeleteAiGeneratedProblem(problem)).toBe(true)
    expect(canReviewPassAiGeneratedProblem(problem)).toBe(true)
    expect(canReviewRejectAiGeneratedProblem(problem)).toBe(false)
    expect(canPublishAiGeneratedProblem(problem)).toBe(false)
  })

  test('objective problems should only allow reject and publish after passing review', () => {
    const problem = { question_type: 'fill_blank', status: 'passed' }

    expect(canEditAiGeneratedProblem(problem)).toBe(false)
    expect(canDeleteAiGeneratedProblem(problem)).toBe(false)
    expect(canReviewPassAiGeneratedProblem(problem)).toBe(false)
    expect(canReviewRejectAiGeneratedProblem(problem)).toBe(true)
    expect(canPublishAiGeneratedProblem(problem)).toBe(true)
  })

  test('objective problems should allow edit and re-review after rejection', () => {
    const problem = { question_type: 'choice', status: 'failed' }

    expect(canEditAiGeneratedProblem(problem)).toBe(true)
    expect(canDeleteAiGeneratedProblem(problem)).toBe(true)
    expect(canReviewPassAiGeneratedProblem(problem)).toBe(true)
    expect(canReviewRejectAiGeneratedProblem(problem)).toBe(false)
  })

  test('coding problems should only allow validate before publish', () => {
    const pending = { question_type: 'coding', status: 'pending' }
    const passed = { question_type: 'coding', status: 'passed' }
    const failed = { question_type: 'coding', status: 'failed' }

    expect(canValidateAiGeneratedProblem(pending)).toBe(true)
    expect(canEditAiGeneratedProblem(pending)).toBe(true)
    expect(canDeleteAiGeneratedProblem(pending)).toBe(true)

    expect(canValidateAiGeneratedProblem(passed)).toBe(false)
    expect(canEditAiGeneratedProblem(passed)).toBe(false)
    expect(canDeleteAiGeneratedProblem(passed)).toBe(false)
    expect(canPublishAiGeneratedProblem(passed)).toBe(true)

    expect(canValidateAiGeneratedProblem(failed)).toBe(true)
    expect(canEditAiGeneratedProblem(failed)).toBe(true)
    expect(canDeleteAiGeneratedProblem(failed)).toBe(true)
  })
})
