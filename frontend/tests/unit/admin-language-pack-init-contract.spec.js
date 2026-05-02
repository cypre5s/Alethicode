const fs = require('fs')
const path = require('path')

function readSource (relativePath) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')
}

describe('admin language pack init contract', () => {
  test('should not render the task selection hint banner', () => {
    const source = readSource('../../src/pages/admin/views/general/LanguagePackInit.vue')

    expect(source).not.toContain('task-detail-hint')
    expect(source).not.toContain('点击上方课程内容包名称，查看该任务的阶段进度、课件文档与练习题详情')
  })

  test('details should only open from explicit language pack name clicks', () => {
    const source = readSource('../../src/pages/admin/views/general/LanguagePackInit.vue')

    expect(source).not.toContain('@row-click="selectTask"')
    expect(source).not.toContain('this.selectedTask = this.filteredTasks[0]')
    expect(source).toContain('lp-name-button')
    expect(source).toContain('@click.stop="selectTask(row)"')
  })

  test('current stage should show a running indicator except for publish', () => {
    const source = readSource('../../src/pages/admin/views/general/LanguagePackInit.vue')

    expect(source).toContain('isRunningStep(step.key)')
    expect(source).toContain("step.key !== 'published'")
    expect(source).toContain('step-spinner')
    expect(source).toContain('stage-step-glyph')
    expect(source).toContain('√')
  })

  test('language pack name card should be centered in the table cell', () => {
    const source = readSource('../../src/pages/admin/views/general/LanguagePackInit.vue')

    expect(source).toContain('lp-name-cell')
    expect(source).toContain('justify-content: center')
    expect(source).toContain('text-align: center')
  })

  test('task detail header should not expose a re-embed action button', () => {
    const source = readSource('../../src/pages/admin/views/general/LanguagePackInit.vue')

    expect(source).not.toContain('重建 Embedding')
    expect(source).not.toContain('@click="doReEmbed"')
  })

  test('task stage animation and stage logs should refresh through one synchronized path', () => {
    const source = readSource('../../src/pages/admin/views/general/LanguagePackInit.vue')

    expect(source).toContain('syncTaskProgress')
    expect(source).toContain('Promise.all([')
    expect(source).toContain('api.getLanguagePackInitTask(taskId, { notifyOnError: false })')
    expect(source).toContain('api.listLanguagePackStageLogs(taskId, { notifyOnError: false })')
    expect(source).toContain('await this.syncTaskProgress(taskId)')
  })

  test('failed task should continue through pipeline retry instead of sync step resume', () => {
    const source = readSource('../../src/pages/admin/views/general/LanguagePackInit.vue')

    expect(source).toContain('v-if="canRunAllSteps && (!pipelineJob || !isPipelineJobActive)"')
    expect(source).toContain('retryLanguagePackPipelineJob')
    expect(source).toContain('重试流水线')
    expect(source).not.toContain('RESUMABLE_FAILED_ACTIONS')
    expect(source).not.toContain('return this.failedAction === action')
  })

  test('failure reason should be localized before rendering in banner and popup', () => {
    const source = readSource('../../src/pages/admin/views/general/LanguagePackInit.vue')

    expect(source).toContain('{{ localizedFailureReason }}')
    expect(source).toContain('localizeFailureReason (rawReason)')
    expect(source).toContain('LLM response missing choices')
    expect(source).toContain('大模型响应格式异常（缺少 choices 字段）')
    expect(source).toContain('步骤「${failedStepLabel}」已中断。\\n${localizedReason}')
  })

  test('delete action should use Element Plus message box instead of legacy this.$confirm', () => {
    const source = readSource('../../src/pages/admin/views/general/LanguagePackInit.vue')

    expect(source).toContain("import { ElMessageBox } from 'element-plus'")
    expect(source).toContain('await ElMessageBox.confirm(')
    expect(source).not.toContain('this.$confirm(')
  })

  test('teacher should not be artificially blocked from deleting ai teaching tasks in frontend', () => {
    const source = readSource('../../src/pages/admin/views/general/LanguagePackInit.vue')

    expect(source).toContain('canDeleteRow (row)')
    expect(source).toContain('return !!row')
    expect(source).not.toContain('if (!this.isCurrentUserTeacher) return true')
    expect(source).not.toContain('return creatorId != null && creatorId === this.currentUserId')
  })

  test('new task slug generation should work on public http ecs frontend', () => {
    const source = readSource('../../src/pages/admin/views/general/LanguagePackInit.vue')

    expect(source).toContain('generateLanguagePackSlug ()')
    expect(source).toContain('Date.now().toString(36)')
    expect(source).toContain('Math.random().toString(36)')
    expect(source).toContain('return `lp-${timePart}-${randomPart}`')
    expect(source).not.toContain('crypto.randomUUID()')
  })

  test('language pack init should only expose asynchronous pipeline job actions', () => {
    const source = readSource('../../src/pages/admin/views/general/LanguagePackInit.vue')
    const apiSource = readSource('../../src/pages/admin/api.js')

    expect(source).toContain('startLanguagePackPipelineJob')
    expect(source).toContain('cancelLanguagePackPipelineJob')
    expect(source).toContain('retryLanguagePackPipelineJob')
    expect(source).not.toContain('@click="runStep(')
    expect(source).not.toContain('runStep (action)')
    expect(source).not.toContain('stepping: false')
    expect(apiSource).not.toContain('parseLanguagePackDocuments')
    expect(apiSource).not.toContain('extractLanguagePackKcs')
    expect(apiSource).not.toContain('extractLanguagePackExamples')
    expect(apiSource).not.toContain('generateLanguagePackProblems')
    expect(apiSource).not.toContain('validateLanguagePackProblems')
    expect(apiSource).not.toContain('publishLanguagePack(taskId')
  })
})
