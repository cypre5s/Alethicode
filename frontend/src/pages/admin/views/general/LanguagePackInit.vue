<template>
  <div class="view language-pack-init">
    <Panel title="课程内容包管理">
      <template #header>
        <el-select
          v-model="selectedLanguagePackId"
          size="small"
          class="language-pack-select"
          placeholder="请选择课程内容包"
          @change="handleLanguagePackChange">
          <el-option label="全部课程内容包" value="__all__"></el-option>
          <el-option
            v-for="pack in languagePackOptions"
            :key="pack.id"
            :label="pack.name + ' v' + pack.version"
            :value="String(pack.id)">
          </el-option>
        </el-select>
        <el-button size="small" @click="showCreateDialog = true" type="primary"
                   :disabled="runningTaskId !== null">
          新建课程内容包
        </el-button>
        <el-button size="small" icon="el-icon-refresh" @click="loadTasks" :loading="loadingTasks">
          刷新
        </el-button>
      </template>

      <el-table v-loading="loadingTasks" :data="pagedTasks" stripe style="width: 100%">
        <el-table-column label="课程内容包" min-width="160" align="center">
          <template #default="{ row }">
            <div class="lp-name-cell">
              <button
                type="button"
                :class="['lp-name-button', { 'is-active': isSelectedTask(row) }]"
                @click.stop="selectTask(row)">
                <span class="lp-name">{{ row.language_pack.name }}</span>
                <span class="lp-meta">{{ row.language_pack.primary_language }}</span>
              </button>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="language_pack.primary_language" label="语言" width="90" align="center"></el-table-column>
        <el-table-column label="阶段" width="150" align="center">
          <template #default="{ row }">
            <el-tag :type="stageTagType(row.stage)" size="small" effect="dark">{{ stageLabel(row.stage) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="统计" min-width="200" align="center">
          <template #default="{ row }">
            <span class="stats-badges">
              <span class="stat-badge">文档 {{ row.language_pack.document_count }}</span>
              <span class="stat-badge">页 {{ row.language_pack.page_count }}</span>
              <span class="stat-badge">知识点 {{ row.language_pack.kc_count }}</span>
              <span class="stat-badge">例题 {{ row.language_pack.example_count }}</span>
              <span class="stat-badge stat-badge--highlight">题目 {{ row.language_pack.problem_count }}</span>
            </span>
          </template>
        </el-table-column>
        <el-table-column label="创建者" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="!row.language_pack.creator_id" size="small" type="warning">管理员</el-tag>
            <el-tag v-else-if="row.language_pack.creator_id === currentUserId" size="small" type="success">我</el-tag>
            <el-tag v-else size="small" type="info">其他</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="create_time" label="创建时间" width="170" align="center">
          <template #default="{ row }">
            {{ formatTime(row.create_time) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" align="center" fixed="right">
          <template #default="{ row }">
            <el-button size="small" plain @click.stop="doExportTask(row)" :loading="exportingRowId === row.id">
              导出
            </el-button>
            <el-button size="small" type="danger" plain @click.stop="confirmDeleteTask(row)"
                       :disabled="!canDeleteRow(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="panel-options" v-if="filteredTasks.length > 0">
        <AdminPagination
          :total="filteredTasks.length"
          :current-page="taskPage"
          :page-size="taskPageSize"
          :page-sizes="[10, 20, 50, 100]"
          @update:currentPage="taskPage = $event"
          @update:pageSize="taskPageSize = $event"
          @change="handleTaskPaginationChange">
        </AdminPagination>
      </div>
    </Panel>

    <Panel v-if="selectedTask" class="task-detail-panel" :title="'任务 #' + selectedTask.id + ' — ' + selectedTask.language_pack.name">
      <template #header>
        <el-button size="small" @click="doExportTask(selectedTask)" :loading="exportingRowId === selectedTask.id" plain>
          导出课程内容包
        </el-button>
        <el-button size="small" type="danger" plain @click="confirmDeleteTask(selectedTask)"
                   :disabled="!canDeleteRow(selectedTask)">
          删除课程内容包
        </el-button>
        <el-tag :type="stageTagType(selectedTask.stage)" effect="dark">
          {{ stageLabel(selectedTask.stage) }}
        </el-tag>
      </template>

      <div v-if="selectedTask.failure_reason" class="failure-banner">
        <strong>失败原因：</strong>{{ localizedFailureReason }}
      </div>

      <div class="timeline-section">
        <el-steps :active="activeStepIndex" finish-status="success" :process-status="processStatus" align-center>
          <el-step v-for="step in stageSteps" :key="step.key"
                   :title="step.label"
                   :status="stepStatus(step.key)">
            <template #icon>
              <span
                :class="[
                  'stage-step-marker',
                  'is-' + stepStatus(step.key),
                  { 'is-running': isRunningStep(step.key) }
                ]">
                <span v-if="isRunningStep(step.key) && step.key !== 'published'" class="step-spinner"></span>
                <span v-else-if="stepStatus(step.key) === 'success'" class="stage-step-glyph" aria-hidden="true">√</span>
                <span v-else-if="stepStatus(step.key) === 'error'" class="stage-step-glyph" aria-hidden="true">!</span>
                <span v-else class="stage-step-index">{{ stepDisplayIndex(step.key) }}</span>
              </span>
            </template>
            <template #title>
              <span
                :class="[
                  'stage-step-title',
                  'is-' + stepStatus(step.key),
                  { 'is-running': isRunningStep(step.key) }
                ]">
                {{ step.label }}
              </span>
            </template>
          </el-step>
        </el-steps>
        <div v-if="activeExecutionText" class="active-progress-line">
          {{ activeExecutionText }}
        </div>
      </div>

      <div class="action-bar">
        <div class="pipeline-job-bar">
          <div class="pipeline-job-meta">
            <span class="pipeline-job-title">异步流水线</span>
            <span v-if="pipelineJob" class="pipeline-job-text">
              {{ pipelineJob.job_id }} · {{ pipelineJob.status || 'unknown' }} · 当前步骤 {{ pipelineJob.current_step || '-' }}
            </span>
            <span v-else class="pipeline-job-text">尚未启动</span>
          </div>
          <div class="pipeline-job-actions">
            <el-button
              v-if="canRunAllSteps && (!pipelineJob || !isPipelineJobActive)"
              size="small"
              type="danger"
              plain
              @click="runAllSteps"
              :loading="runningAll">
              启动异步流水线
            </el-button>
            <el-button
              v-if="pipelineJob && isPipelineJobActive"
              size="small"
              plain
              @click="cancelPipelineJob"
              :loading="pipelineJobLoading">
              取消流水线
            </el-button>
            <el-button
              v-if="pipelineJob && !isPipelineJobActive"
              size="small"
              plain
              @click="retryPipelineJob"
              :loading="pipelineJobLoading">
              重试流水线
            </el-button>
          </div>
        </div>
      </div>

      <el-tabs v-model="detailTab" class="detail-tabs" @tab-click="onTabClick">
        <el-tab-pane label="课件文档" name="documents">
          <div class="doc-sort-hint" v-if="canReorderDocuments">
            拖动调整教学顺序，保存后 AI 将按此顺序读取课件
          </div>
          <el-table :data="pagedDocumentList" stripe size="small" style="width: 100%">
            <el-table-column label="序号" width="70" align="center">
              <template #default="{ $index }">{{ $index + 1 }}</template>
            </el-table-column>
            <el-table-column prop="original_filename" label="文件名" min-width="200" align="center"></el-table-column>
            <el-table-column prop="page_count" label="页数" width="80" align="center"></el-table-column>
            <el-table-column prop="status" label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 'normalized' ? 'success' : 'warning'" size="small">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="140" align="center" v-if="canReorderDocuments">
              <template #default="{ $index }">
                <el-button size="small" :disabled="$index === 0" @click="moveDocument($index, -1)">上移</el-button>
                <el-button size="small" :disabled="$index === documentList.length - 1" @click="moveDocument($index, 1)">下移</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="panel-options" v-if="documentList.length > 0">
            <AdminPagination
              :total="documentList.length"
              :current-page="documentsPage"
              :page-size="documentsPageSize"
              :page-sizes="[10, 20, 50, 100]"
              @update:currentPage="documentsPage = $event"
              @update:pageSize="documentsPageSize = $event"
              @change="handleDocumentsPaginationChange">
            </AdminPagination>
          </div>
          <div class="doc-sort-actions" v-if="canReorderDocuments && documentOrderDirty">
            <el-button size="small" type="primary" @click="saveDocumentOrder" :loading="savingOrder">保存排序</el-button>
          </div>
        </el-tab-pane>
        <el-tab-pane label="阶段日志" name="logs">
          <el-table :data="pagedStageLogs" stripe size="small" style="width: 100%">
            <el-table-column label="从" width="140" align="center">
              <template #default="{ row }">{{ stageLabel(row.from_stage) }}</template>
            </el-table-column>
            <el-table-column label="到" width="140" align="center">
              <template #default="{ row }">{{ stageLabel(row.to_stage) }}</template>
            </el-table-column>
            <el-table-column label="消息" show-overflow-tooltip align="center">
              <template #default="{ row }">{{ formatStageLogMessage(row.message) }}</template>
            </el-table-column>
            <el-table-column prop="create_time" label="时间" width="170" align="center">
              <template #default="{ row }">{{ formatTime(row.create_time) }}</template>
            </el-table-column>
          </el-table>
          <div class="panel-options" v-if="stageLogs.length > 0">
            <AdminPagination
              :total="stageLogs.length"
              :current-page="logsPage"
              :page-size="logsPageSize"
              :page-sizes="[10, 20, 50, 100]"
              @update:currentPage="logsPage = $event"
              @update:pageSize="logsPageSize = $event"
              @change="handleLogsPaginationChange">
            </AdminPagination>
          </div>
        </el-tab-pane>
        <el-tab-pane label="知识图谱" name="kcs">
          <el-table :data="pagedKcList" stripe size="small" style="width: 100%">
            <el-table-column prop="chapter_title" label="章节" width="160" align="center"></el-table-column>
            <el-table-column prop="name" label="名称" width="180" align="center"></el-table-column>
            <el-table-column prop="name_en" label="英文" width="180" align="center">
              <template #default="{ row }">
                <span class="mono-text">{{ row.name_en }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="description" label="描述" show-overflow-tooltip align="center"></el-table-column>
          </el-table>
          <div class="panel-options" v-if="kcList.length > 0">
            <AdminPagination
              :total="kcList.length"
              :current-page="kcsPage"
              :page-size="kcsPageSize"
              :page-sizes="[10, 20, 50, 100]"
              @update:currentPage="kcsPage = $event"
              @update:pageSize="kcsPageSize = $event"
              @change="handleKcsPaginationChange">
            </AdminPagination>
          </div>
        </el-tab-pane>
        <el-tab-pane label="例题" name="examples">
          <el-table :data="pagedExampleList" stripe size="small" style="width: 100%">
            <el-table-column prop="id" label="ID" width="70" align="center"></el-table-column>
            <el-table-column prop="source_title" label="标题" width="200" show-overflow-tooltip align="center"></el-table-column>
            <el-table-column prop="unit_type" label="类型" width="100" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="unitTypeTag(row.unit_type)">{{ row.unit_type }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="kc_names" label="绑定知识点" min-width="180" show-overflow-tooltip align="center"></el-table-column>
            <el-table-column prop="kc_count" label="知识点数" width="70" align="center"></el-table-column>
            <el-table-column label="OJ" width="70" align="center">
              <template #default="{ row }">
                <el-tag :type="row.oj_convertible ? 'success' : 'info'" size="small">{{ row.oj_convertible ? '是' : '否' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="page_range_start" label="页码" width="80" align="center">
              <template #default="{ row }">{{ row.page_range_start }}-{{ row.page_range_end }}</template>
            </el-table-column>
          </el-table>
          <div class="panel-options" v-if="exampleList.length > 0">
            <AdminPagination
              :total="exampleList.length"
              :current-page="examplesPage"
              :page-size="examplesPageSize"
              :page-sizes="[10, 20, 50, 100]"
              @update:currentPage="examplesPage = $event"
              @update:pageSize="examplesPageSize = $event"
              @change="handleExamplesPaginationChange">
            </AdminPagination>
          </div>
        </el-tab-pane>
        <el-tab-pane label="NFK 数据" name="nfk">
          <LanguagePackNfkCard :language-pack-id="selectedTask && selectedTask.language_pack ? selectedTask.language_pack.id : null" />
        </el-tab-pane>
        <el-tab-pane label="练习题" name="candidates">
          <el-table :data="pagedCandidateList" stripe size="small" style="width: 100%">
            <el-table-column prop="id" label="ID" width="70" align="center"></el-table-column>
            <el-table-column prop="candidate_title" label="标题" show-overflow-tooltip align="center"></el-table-column>
            <el-table-column prop="kc_name" label="知识点" width="150" align="center"></el-table-column>
            <el-table-column label="教学解析" min-width="220" show-overflow-tooltip align="center">
              <template #default="{ row }">
                {{ row.teaching_explanation || '-' }}
              </template>
            </el-table-column>
            <el-table-column label="来源页码" width="120" align="center">
              <template #default="{ row }">
                {{ formatSourcePages(row.source_pages_json) }}
              </template>
            </el-table-column>
            <el-table-column label="易错点数" width="100" align="center">
              <template #default="{ row }">
                {{ parseJsonCount(row.common_mistakes_json) }}
              </template>
            </el-table-column>
            <el-table-column label="状态" width="120" align="center">
              <template #default="{ row }">
                <el-tag :type="validationTagType(row.validation_status)" size="small">
                  {{ row.validation_status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="validation_message" label="验证消息" show-overflow-tooltip align="center"></el-table-column>
          </el-table>
          <div class="panel-options" v-if="candidateList.length > 0">
            <AdminPagination
              :total="candidateList.length"
              :current-page="candidatesPage"
              :page-size="candidatesPageSize"
              :page-sizes="[10, 20, 50, 100]"
              @update:currentPage="candidatesPage = $event"
              @update:pageSize="candidatesPageSize = $event"
              @change="handleCandidatesPaginationChange">
            </AdminPagination>
          </div>
        </el-tab-pane>
      </el-tabs>
    </Panel>

    <el-dialog title="新建课程内容包" v-model="showCreateDialog" width="560px"
               :close-on-click-modal="false" @close="onCreateDialogClose">
      <div v-if="createStep === 'mode'" class="init-mode-select">
        <div class="init-mode-desc">请选择初始化模式</div>
        <el-radio-group v-model="initMode" class="init-mode-group">
          <el-radio value="step_review" class="init-mode-radio">
            <div class="init-mode-label">逐步审核</div>
            <div class="init-mode-hint">每完成一个阶段后手动审核，确认无误再推进下一步</div>
          </el-radio>
          <el-radio value="batch_review" class="init-mode-radio">
            <div class="init-mode-label">一键初始化</div>
            <div class="init-mode-hint">自动执行全部阶段，完成后统一审核最终结果</div>
          </el-radio>
          <el-radio value="import" class="init-mode-radio">
            <div class="init-mode-label">导入课程内容包</div>
            <div class="init-mode-hint">导入已导出的课程内容包 JSON 文件，跳过 AI 生成阶段</div>
          </el-radio>
        </el-radio-group>
      </div>

      <el-form v-if="createStep === 'form'" :model="createForm" label-width="120px" ref="createFormRef">
        <el-form-item label="内容包名称" required>
          <el-input v-model="createForm.name" placeholder="如：Python 3 基础"></el-input>
        </el-form-item>
        <el-form-item label="主语言" required>
          <el-select v-model="createForm.primary_language" placeholder="选择编程语言" style="width: 100%;">
            <el-option label="Python3" value="Python3"></el-option>
            <el-option label="C" value="C"></el-option>
            <el-option label="C++" value="C++"></el-option>
            <el-option label="Java" value="Java"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="课件文件" required>
          <el-upload
            ref="createUploadRef"
            :action="''"
            :auto-upload="false"
            :on-change="onCreateFileChange"
            :on-remove="onCreateFileRemove"
            :file-list="createFiles"
            :show-file-list="false"
            multiple
            accept=".pdf,.pptx,.docx,.ppt,.doc">
            <template #trigger>
              <el-button size="small" type="info" plain>选择本次课件集合</el-button>
            </template>
            <span class="form-tip" style="margin-left: 8px;">至少选择 1 个文件</span>
          </el-upload>
          <div v-if="createFiles.length" class="file-sort-list">
            <div class="file-sort-hint">拖动调整文件顺序</div>
            <div
              v-for="(file, idx) in createFiles"
              :key="file.uid || idx"
              class="file-sort-item"
              draggable="true"
              @dragstart="onFileDragStart(idx, $event)"
              @dragover.prevent="onFileDragOver(idx, $event)"
              @drop="onFileDrop(idx)"
              @dragend="fileDragIdx = null"
              :class="{ 'is-dragging': fileDragIdx === idx }"
            >
              <span class="file-sort-handle">☰</span>
              <span class="file-sort-icon">📄</span>
              <span class="file-sort-name">{{ file.name }}</span>
              <button type="button" class="file-sort-remove" @click="removeCreateFile(idx)">✕</button>
            </div>
          </div>
        </el-form-item>
      </el-form>

      <div v-if="createStep === 'import'" class="init-import-section">
        <el-upload
          ref="importUploadRef"
          :action="''"
          :auto-upload="false"
          :on-change="onImportFileChange"
          :on-remove="onImportFileRemove"
          :file-list="importFileList"
          :limit="1"
          accept=".json"
          drag>
          <i class="el-icon-upload"></i>
          <div class="el-upload__text">将课程内容包 JSON 文件拖到此处，或 <em>点击上传</em></div>
          <template #tip>
            <div class="form-tip" style="margin-top: 8px;">仅支持由本系统导出的 .json 课程内容包文件，最大 10MB</div>
          </template>
        </el-upload>
        <div v-if="importPreview" class="import-preview">
          <div class="import-preview-title">文件预览</div>
          <div class="import-preview-grid">
            <div class="import-preview-item">
              <span class="import-preview-label">名称</span>
              <span class="import-preview-value">{{ importPreview.name }}</span>
            </div>
            <div class="import-preview-item">
              <span class="import-preview-label">Slug</span>
              <span class="import-preview-value mono-text">{{ importPreview.slug }}</span>
            </div>
            <div class="import-preview-item">
              <span class="import-preview-label">语言</span>
              <span class="import-preview-value">{{ importPreview.primary_language }}</span>
            </div>
            <div class="import-preview-item">
              <span class="import-preview-label">章节</span>
              <span class="import-preview-value">{{ importPreview.chapter_count }}</span>
            </div>
            <div class="import-preview-item">
              <span class="import-preview-label">知识图谱</span>
              <span class="import-preview-value">{{ importPreview.kc_count }}</span>
            </div>
            <div class="import-preview-item">
              <span class="import-preview-label">例题</span>
              <span class="import-preview-value">{{ importPreview.example_count }}</span>
            </div>
            <div class="import-preview-item">
              <span class="import-preview-label">练习题</span>
              <span class="import-preview-value">{{ importPreview.candidate_count }}</span>
            </div>
          </div>
        </div>
      </div>

      <template #footer>
        <el-button v-if="createStep !== 'mode'" @click="createStep = 'mode'">上一步</el-button>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button v-if="createStep === 'mode'" type="primary" @click="advanceCreateStep">
          下一步
        </el-button>
        <span v-if="createStep === 'form'" @click="onCreateBtnNativeClick">
          <el-button type="primary" :loading="creating" :disabled="createFiles.length === 0">
            {{ initMode === 'batch_review' ? '创建并一键初始化' : '创建任务' }}
          </el-button>
        </span>
        <span v-if="createStep === 'import'" @click="onImportBtnNativeClick">
          <el-button type="primary" :loading="importing" :disabled="!importFileRaw">
            导入
          </el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { ElMessageBox } from 'element-plus'
import api from '@admin/api'
import {
  appendLanguagePackQuery,
  resolveCurrentLanguagePackId
} from '@admin/utils/languagePackContext'

const STAGE_LABELS = {
  created: '已创建',
  normalizing: '规范化中',
  parsing: '解析中',
  kc_extraction: '知识点提取',
  kc_ready: '知识点就绪',
  segments_ready: '分段就绪',
  units_ready: '教学单元就绪',
  oj_candidates_ready: 'OJ 候选就绪',
  problem_packages_ready: '题包就绪',
  problems_validated: '已验证',
  published: '已发布',
  failed: '失败'
}
const REORDER_ALLOWED_STAGES = ['created', 'normalizing', 'parsing']
const STAGE_TO_STEP_KEY = {
  created: 'created',
  normalizing: 'normalizing',
  parsing: 'parsing',
  kc_ready: 'kc_ready',
  segments_ready: 'oj_candidates_ready',
  units_ready: 'oj_candidates_ready',
  oj_candidates_ready: 'oj_candidates_ready',
  problem_packages_ready: 'problem_packages_ready',
  problems_validated: 'problems_validated',
  published: 'published'
}
const STEP_ACTION_LABELS = {
  'pipeline-job': '异步流水线'
}
const FAILURE_REASON_REPLACEMENTS = [
  { pattern: /Courseware unit extraction failed:/gi, value: '课件单元抽取失败：' },
  { pattern: /Courseware segmentation failed:/gi, value: '课件分段失败：' },
  { pattern: /Example extraction failed:/gi, value: '例题抽取失败：' },
  { pattern: /OJ candidate judgement failed:/gi, value: 'OJ 候选判定失败：' },
  { pattern: /Problem package generation failed:/gi, value: '练习题生成失败：' },
  { pattern: /Problem validation failed:/gi, value: '题目验证失败：' },
  { pattern: /Publish failed:/gi, value: '发布失败：' },
  { pattern: /KC batch extraction failed:/gi, value: '知识点批量抽取失败：' },
  { pattern: /KC reconciliation failed:/gi, value: '知识点归并失败：' },
  { pattern: /LLM KC extraction failed:/gi, value: '知识点大模型抽取失败：' },
  { pattern: /LLM response missing choices/gi, value: '大模型响应格式异常（缺少 choices 字段）' },
  { pattern: /LLM response parsing failed after retries/gi, value: '大模型响应解析失败（重试后仍未通过）' },
  { pattern: /Spring AI callForJson failed:/gi, value: 'Spring AI JSON 调用失败：' },
  { pattern: /No courseware units extracted/gi, value: '未抽取到课件教学单元' },
  { pattern: /No OJ-convertible courseware units available for problem generation/gi, value: '没有可转为 OJ 题目的课件单元，无法生成练习题' },
  { pattern: /No generated problem packages to validate/gi, value: '没有可验证的练习题题包' },
  { pattern: /No validated problem packages to publish/gi, value: '没有可发布的已验证题包' },
  { pattern: /Cannot extract examples in stage:/gi, value: '当前阶段不允许抽取例题：' },
  { pattern: /Cannot generate problems in stage:/gi, value: '当前阶段不允许生成练习题：' },
  { pattern: /Cannot validate problems in stage:/gi, value: '当前阶段不允许验证题目：' },
  { pattern: /Cannot publish in stage:/gi, value: '当前阶段不允许发布：' }
]
const STEP_REQUEST_OPTIONS = {
  notifyOnSuccess: false,
  notifyOnError: false
}

export default {
  name: 'LanguagePackInit',
  data () {
    return {
      tasks: [],
      loadingTasks: false,
      languagePackOptions: [],
      selectedLanguagePackId: '__all__',
      selectedTask: null,
      runningTaskId: null,
      taskPage: 1,
      taskPageSize: 10,
      showCreateDialog: false,
      creating: false,
      createStep: 'mode',
      initMode: 'step_review',
      createForm: {
        name: '',
        slug: '',
        primary_language: 'Python3',
        enable_objective_questions: false
      },
      createFiles: [],
      fileDragIdx: null,
      importFileList: [],
      importFileRaw: null,
      importPreview: null,
      importing: false,
      exportingRowId: null,
      runningAll: false,
      pipelineJob: null,
      pipelineJobLoading: false,
      pipelineJobPollTimer: null,
      detailTab: 'documents',
      stageLogs: [],
      kcList: [],
      exampleList: [],
      candidateList: [],
      documentList: [],
      documentsPage: 1,
      documentsPageSize: 10,
      logsPage: 1,
      logsPageSize: 10,
      kcsPage: 1,
      kcsPageSize: 10,
      examplesPage: 1,
      examplesPageSize: 10,
      candidatesPage: 1,
      candidatesPageSize: 10,
      documentOrderDirty: false,
      savingOrder: false,
      pollTimer: null,
      pendingRefreshTimer: null,
      shownInterruptions: {},
      stageSteps: [
        { key: 'created', label: '创建' },
        { key: 'normalizing', label: '规范化' },
        { key: 'parsing', label: '页解析' },
        { key: 'kc_ready', label: '知识点抽取' },
        { key: 'oj_candidates_ready', label: '例题抽取' },
        { key: 'problem_packages_ready', label: '练习题生成' },
        { key: 'problems_validated', label: '题目验证' },
        { key: 'published', label: '发布' }
      ]
    }
  },
  computed: {
    currentUserId () {
      const user = this.$store && this.$store.getters.user
      return user ? user.id : null
    },
    canDeleteSelectedTask () {
      return this.canDeleteRow(this.selectedTask)
    },
    activeStepIndex () {
      if (!this.selectedTask) return 0
      return this.currentStepIndex
    },
    processStatus () {
      if (!this.selectedTask) return 'process'
      if (this.selectedTask.stage === 'failed') return 'error'
      if (this.selectedTask.stage === 'published') return 'success'
      return 'process'
    },
    stageCheckpointKey () {
      if (!this.selectedTask) return ''
      return STAGE_TO_STEP_KEY[this.selectedTask.stage] || ''
    },
    currentStepKey () {
      if (!this.selectedTask) return ''
      if (this.isTaskRunning(this.selectedTask) && this.selectedTask.active_step_key) {
        return this.selectedTask.active_step_key
      }
      if (this.selectedTask.stage === 'failed' && this.failedStepKey) {
        return this.failedStepKey
      }
      return this.stageCheckpointKey
    },
    currentStepIndex () {
      if (!this.currentStepKey) return 0
      const idx = this.stageSteps.findIndex(step => step.key === this.currentStepKey)
      return idx >= 0 ? idx : 0
    },
    stageCheckpointIndex () {
      if (!this.stageCheckpointKey) return 0
      const idx = this.stageSteps.findIndex(step => step.key === this.stageCheckpointKey)
      return idx >= 0 ? idx : 0
    },
    failedStepKey () {
      if (!this.selectedTask || this.selectedTask.stage !== 'failed') return ''
      const failedLog = [...this.stageLogs].reverse().find(row => row.to_stage === 'failed')
      return failedLog ? (STAGE_TO_STEP_KEY[failedLog.from_stage] || failedLog.from_stage || '') : ''
    },
    activeExecutionText () {
      if (!this.selectedTask || !this.isTaskRunning(this.selectedTask)) return ''
      const baseMessage = this.selectedTask.active_message || ('正在执行：' + this.stageLabel(this.selectedTask.active_step_key || this.selectedTask.stage))
      const { progress_current: current, progress_total: total } = this.selectedTask
      if (Number.isInteger(current) && Number.isInteger(total) && total > 0 && !baseMessage.includes(`${current}/${total}`)) {
        return `${baseMessage} (${current}/${total})`
      }
      return baseMessage
    },
    localizedFailureReason () {
      if (!this.selectedTask || !this.selectedTask.failure_reason) return ''
      return this.localizeFailureReason(this.selectedTask.failure_reason)
    },
    canRunAllSteps () {
      if (!this.selectedTask) return false
      if (this.selectedTask.stage === 'published') return false
      return true
    },
    canReorderDocuments () {
      if (!this.selectedTask) return false
      return REORDER_ALLOWED_STAGES.includes(this.selectedTask.stage)
    },
    filteredTasks () {
      if (!this.selectedLanguagePackId) {
        return []
      }
      if (this.selectedLanguagePackId === '__all__') {
        return this.tasks
      }
      return this.tasks.filter(item => String(item.language_pack && item.language_pack.id) === String(this.selectedLanguagePackId))
    },
    isBackendProcessing () {
      return this.isTaskRunning(this.selectedTask) || this.isPipelineJobActive
    },
    isPipelineJobActive () {
      if (!this.pipelineJob) return false
      const status = String(this.pipelineJob.status || '').toLowerCase()
      return !['completed', 'failed', 'canceled', 'terminated', 'timed_out'].includes(status)
    },
    pagedTasks () {
      const start = (this.taskPage - 1) * this.taskPageSize
      return this.filteredTasks.slice(start, start + this.taskPageSize)
    },
    pagedDocumentList () {
      const start = (this.documentsPage - 1) * this.documentsPageSize
      return this.documentList.slice(start, start + this.documentsPageSize)
    },
    pagedStageLogs () {
      const start = (this.logsPage - 1) * this.logsPageSize
      return this.stageLogs.slice(start, start + this.logsPageSize)
    },
    pagedKcList () {
      const start = (this.kcsPage - 1) * this.kcsPageSize
      return this.kcList.slice(start, start + this.kcsPageSize)
    },
    pagedExampleList () {
      const start = (this.examplesPage - 1) * this.examplesPageSize
      return this.exampleList.slice(start, start + this.examplesPageSize)
    },
    pagedCandidateList () {
      const start = (this.candidatesPage - 1) * this.candidatesPageSize
      return this.candidateList.slice(start, start + this.candidatesPageSize)
    }
  },
  mounted () {
    this.loadLanguagePackOptions()
  },
  beforeUnmount () {
    if (this.pendingRefreshTimer) {
      clearTimeout(this.pendingRefreshTimer)
      this.pendingRefreshTimer = null
    }
    this.stopPolling()
    this.stopPipelineJobPolling()
  },
  methods: {
    onTabClick () {
      const scrollParent = this.$el.closest('.content-app') || document.documentElement
      const pos = scrollParent.scrollTop
      this.$nextTick(() => { scrollParent.scrollTop = pos })
    },
    loadLanguagePackOptions () {
      return api.getPublishedLanguagePacks().then(res => {
        this.languagePackOptions = Array.isArray(res.data.data) ? res.data.data : []
        if (this.$route.query.language_pack_id) {
          this.selectedLanguagePackId = resolveCurrentLanguagePackId(this.$route.query.language_pack_id, this.languagePackOptions)
        } else {
          this.selectedLanguagePackId = '__all__'
        }
        this.syncLanguagePackRoute(true)
        this.loadTasks()
      }).catch(() => {
        this.languagePackOptions = []
        this.selectedLanguagePackId = ''
        this.tasks = []
        this.selectedTask = null
      })
    },
    handleLanguagePackChange () {
      this.taskPage = 1
      this.syncLanguagePackRoute()
      this.loadTasks()
    },
    handleTaskPaginationChange ({ page, pageSize }) {
      this.taskPage = page
      this.taskPageSize = pageSize
    },
    handleDocumentsPaginationChange ({ page, pageSize }) {
      this.documentsPage = page
      this.documentsPageSize = pageSize
    },
    handleLogsPaginationChange ({ page, pageSize }) {
      this.logsPage = page
      this.logsPageSize = pageSize
    },
    handleKcsPaginationChange ({ page, pageSize }) {
      this.kcsPage = page
      this.kcsPageSize = pageSize
    },
    handleExamplesPaginationChange ({ page, pageSize }) {
      this.examplesPage = page
      this.examplesPageSize = pageSize
    },
    handleCandidatesPaginationChange ({ page, pageSize }) {
      this.candidatesPage = page
      this.candidatesPageSize = pageSize
    },
    syncLanguagePackRoute (replace = false) {
      const nextQuery = appendLanguagePackQuery({}, this.selectedLanguagePackId)
      const currentQuery = appendLanguagePackQuery({}, this.$route.query.language_pack_id)
      if (JSON.stringify(nextQuery) === JSON.stringify(currentQuery)) {
        return
      }
      const payload = { name: this.$route.name, query: nextQuery }
      if (replace) {
        this.$router.replace(payload)
        return
      }
      this.$router.push(payload)
    },
    formatTime (ts) {
      if (!ts) return '-'
      return new Date(ts).toLocaleString('zh-CN', { hour12: false })
    },
    stageLabel (stage) {
      return STAGE_LABELS[stage] || stage
    },
    stepDisplayIndex (key) {
      const idx = this.stageSteps.findIndex(step => step.key === key)
      return idx >= 0 ? idx + 1 : '?'
    },
    stageTagType (stage) {
      if (stage === 'published') return 'success'
      if (stage === 'failed') return 'danger'
      if (stage === 'created') return 'info'
      return 'warning'
    },
    validationTagType (status) {
      if (status === 'passed') return 'success'
      if (status === 'failed') return 'danger'
      if (status === 'validating') return 'warning'
      return 'info'
    },
    parseJsonCount (rawJson) {
      try {
        const parsed = JSON.parse(rawJson || '[]')
        return Array.isArray(parsed) ? parsed.length : 0
      } catch {
        return 0
      }
    },
    formatSourcePages (rawJson) {
      try {
        const parsed = JSON.parse(rawJson || '[]')
        if (!Array.isArray(parsed) || parsed.length === 0) return '-'
        return parsed.join(',')
      } catch {
        return '-'
      }
    },
    formatStageLogMessage (message) {
      return this.localizeFailureReason(message)
    },
    localizeFailureReason (rawReason) {
      const source = String(rawReason || '').trim()
      if (!source) return ''
      let localized = source
      FAILURE_REASON_REPLACEMENTS.forEach(({ pattern, value }) => {
        localized = localized.replace(pattern, value)
      })
      return localized
    },
    stepStatus (key) {
      if (!this.selectedTask) return 'wait'
      const targetIdx = this.stageSteps.findIndex(step => step.key === key)
      const currentIdx = this.currentStepIndex
      if (targetIdx < 0) return 'wait'
      if (this.selectedTask.stage === 'failed') {
        const failedIdx = this.failedStepKey ? this.stageSteps.findIndex(step => step.key === this.failedStepKey) : currentIdx
        if (targetIdx < failedIdx) return 'success'
        if (targetIdx === failedIdx) return 'error'
        return 'wait'
      }
      if (this.isTaskRunning(this.selectedTask)) {
        if (targetIdx < currentIdx) return 'success'
        if (targetIdx === currentIdx) return 'process'
        return 'wait'
      }
      if (this.selectedTask.stage === 'published') {
        return targetIdx <= currentIdx ? 'success' : 'wait'
      }
      if (targetIdx < currentIdx) return 'success'
      if (targetIdx === currentIdx) return 'process'
      return 'wait'
    },
    isRunningStep (key) {
      if (!this.selectedTask || !this.currentStepKey) return false
      if (!this.isTaskRunning(this.selectedTask) || this.selectedTask.stage === 'failed') return false
      return this.currentStepKey === key && key !== 'published'
    },
    isTaskRunning (task) {
      return !!(task && task.active_status === 'running')
    },
    isTaskTerminal (task) {
      if (!task) return true
      return (task.stage === 'published' || task.stage === 'failed') && !this.isTaskRunning(task)
    },
    isSelectedTask (row) {
      if (!this.selectedTask || !row) return false
      return this.selectedTask.id === row.id
    },
    unitTypeTag (unitType) {
      const map = { exercise: 'success', assignment: '', worked_example: 'warning', code_snippet: 'info', demo: 'info' }
      return map[unitType] || 'info'
    },
    moveDocument (index, direction) {
      const target = index + direction
      if (target < 0 || target >= this.documentList.length) return
      const temp = this.documentList[index]
      this.documentList.splice(index, 1)
      this.documentList.splice(target, 0, temp)
      this.documentOrderDirty = true
    },
    saveDocumentOrder () {
      if (!this.selectedTask) return
      this.savingOrder = true
      const ids = this.documentList.map(d => d.id)
      api.reorderLanguagePackDocuments(this.selectedTask.id, ids).then(() => {
        this.savingOrder = false
        this.documentOrderDirty = false
      }).catch(() => {
        this.savingOrder = false
      })
    },
    loadTasks () {
      if (!this.selectedLanguagePackId) {
        this.tasks = []
        this.selectedTask = null
        this.runningTaskId = null
        this.loadingTasks = false
        return
      }
      this.loadingTasks = true
      api.listLanguagePackInitTasks({ notifyOnError: false }).then(res => {
        this.tasks = Array.isArray(res.data.data) ? res.data.data : []
        this.updateRunningTaskId()
        this.loadingTasks = false
        const queryTaskId = this.$route.query.task_id
        if (queryTaskId && !this.selectedTask) {
          const match = this.tasks.find(t => String(t.id) === String(queryTaskId))
          if (match) {
            this.selectTask(match)
          }
        }
        if (this.selectedTask) {
          const fresh = this.filteredTasks.find(t => t.id === this.selectedTask.id)
          if (fresh) this.selectedTask = fresh
          if (!fresh) this.selectedTask = null
        }
        if (this.selectedTask) {
          this.startPolling(0)
          this.syncPipelineJob(this.selectedTask.id).catch(() => {})
        } else {
          this.stopPolling()
          this.stopPipelineJobPolling()
          this.pipelineJob = null
        }
        if ((this.taskPage - 1) * this.taskPageSize >= this.filteredTasks.length) {
          this.taskPage = 1
        }
      }).catch(() => {
        this.loadingTasks = false
      })
    },
    openTaskInNewTab (row) {
      const resolved = this.$router.resolve({
        path: this.$route.path,
        query: { task_id: row.id }
      })
      window.open(resolved.href, '_blank')
    },
    selectTask (row) {
      this.selectedTask = row
      this.loadTaskDetails()
      this.startPolling(0)
    },
    pipelineJobStorageKey (taskId) {
      return `language-pack-pipeline-job:${taskId}`
    },
    readPipelineJobId (taskId) {
      if (!taskId) return ''
      try {
        return window.localStorage.getItem(this.pipelineJobStorageKey(taskId)) || ''
      } catch {
        return ''
      }
    },
    persistPipelineJob (taskId, job) {
      if (!taskId) return
      try {
        if (job && job.job_id) {
          window.localStorage.setItem(this.pipelineJobStorageKey(taskId), job.job_id)
          return
        }
        window.localStorage.removeItem(this.pipelineJobStorageKey(taskId))
      } catch {}
    },
    stopPipelineJobPolling () {
      if (this.pipelineJobPollTimer) {
        clearTimeout(this.pipelineJobPollTimer)
        this.pipelineJobPollTimer = null
      }
    },
    startPipelineJobPolling (delay = 0) {
      this.stopPipelineJobPolling()
      if (!this.selectedTask || !this.pipelineJob || !this.pipelineJob.job_id) return
      const poll = async () => {
        if (!this.selectedTask || !this.pipelineJob || !this.pipelineJob.job_id) return
        await this.syncPipelineJob(this.selectedTask.id, this.pipelineJob.job_id)
        if (this.isPipelineJobActive) {
          this.pipelineJobPollTimer = setTimeout(poll, 1500)
          return
        }
        await this.refreshSelected()
      }
      this.pipelineJobPollTimer = setTimeout(poll, delay)
    },
    async syncPipelineJob (taskId, jobId = '') {
      const resolvedJobId = jobId || this.readPipelineJobId(taskId)
      if (!taskId || !resolvedJobId) {
        this.pipelineJob = null
        return null
      }
      const res = await api.getLanguagePackPipelineJob(taskId, resolvedJobId, { notifyOnError: false })
      this.pipelineJob = res.data && res.data.data ? res.data.data : null
      this.persistPipelineJob(taskId, this.pipelineJob)
      return this.pipelineJob
    },
    loadTaskResources (taskId) {
      api.listLanguagePackDocuments(taskId).then(res => {
        this.documentList = res.data.data
        this.documentOrderDirty = false
      }).catch(() => {})
      api.listLanguagePackKcs(taskId).then(res => {
        this.kcList = res.data.data
      }).catch(() => {})
      api.listLanguagePackExamples(taskId).then(res => {
        this.exampleList = res.data.data
      }).catch(() => {})
      api.listLanguagePackCandidates(taskId).then(res => {
        this.candidateList = res.data.data
      }).catch(() => {})
    },
    async syncTaskProgress (taskId) {
      const [taskRes, logsRes] = await Promise.all([
        api.getLanguagePackInitTask(taskId, { notifyOnError: false }),
        api.listLanguagePackStageLogs(taskId, { notifyOnError: false })
      ])
      if (!this.selectedTask || this.selectedTask.id !== taskId) return null
      const task = taskRes.data.data
      const logs = Array.isArray(logsRes.data.data) ? logsRes.data.data : []
      this.selectedTask = task
      const idx = this.tasks.findIndex(t => t.id === taskId)
      if (idx >= 0) this.tasks[idx] = task
      this.stageLogs = logs
      this.updateRunningTaskId()
      this.handleTaskFailureInterrupt(task)
      return task
    },
    loadTaskDetails () {
      if (!this.selectedTask) return
      this.documentsPage = 1
      this.logsPage = 1
      this.kcsPage = 1
      this.examplesPage = 1
      this.candidatesPage = 1
      const taskId = this.selectedTask.id
      this.syncTaskProgress(taskId).catch(() => {})
      this.syncPipelineJob(taskId).catch(() => {})
      this.loadTaskResources(taskId)
    },
    startPolling (delay = 0) {
      this.stopPolling()
      if (!this.selectedTask) return
      const poll = async () => {
        if (!this.selectedTask) return
        const taskId = this.selectedTask.id
        try {
          const task = await this.syncTaskProgress(taskId)
          if (!task) return
          if (this.isTaskTerminal(task)) {
            this.loadTaskResources(taskId)
            this.stopPolling()
            return
          }
          this.pollTimer = setTimeout(poll, this.isTaskRunning(task) ? 1000 : 3000)
        } catch (err) {
          this.handlePollingInterrupt(err)
          if (this.selectedTask) {
            this.pollTimer = setTimeout(poll, this.isTaskRunning(this.selectedTask) ? 1000 : 3000)
          }
        }
      }
      this.pollTimer = setTimeout(poll, delay)
    },
    stopPolling () {
      if (this.pollTimer) {
        clearTimeout(this.pollTimer)
        this.pollTimer = null
      }
    },
    updateRunningTaskId () {
      const runningTask = this.tasks.find(task => this.isTaskRunning(task))
      this.runningTaskId = runningTask ? runningTask.id : null
    },
    queueImmediateRefresh () {
      if (this.pendingRefreshTimer) {
        clearTimeout(this.pendingRefreshTimer)
      }
      this.pendingRefreshTimer = setTimeout(() => {
        this.pendingRefreshTimer = null
        this.refreshSelected()
      }, 200)
    },
    resolveInterruptMessage (err) {
      if (!err) return '请求已中断'
      if (err.response && err.response.data && err.response.data.data) {
        return this.localizeFailureReason(String(err.response.data.data))
      }
      return this.localizeFailureReason(err.message || '请求已中断')
    },
    showInterruptAlert (signature, title, message) {
      if (!signature || this.shownInterruptions[signature]) {
        return Promise.resolve()
      }
      this.shownInterruptions = {
        ...this.shownInterruptions,
        [signature]: true
      }
      return this.$alert(message, title, {
        confirmButtonText: '知道了',
        type: 'error',
        closeOnClickModal: false,
        closeOnPressEscape: false
      }).catch(() => {})
    },
    handleTaskFailureInterrupt (task) {
      if (!task || task.stage !== 'failed' || !task.failure_reason) return
      const failedStepLabel = this.stageLabel(this.failedStepKey || this.currentStepKey || task.active_step_key || task.stage)
      const signature = `failed:${task.id}:${task.failure_reason}`
      const localizedReason = this.localizeFailureReason(task.failure_reason)
      const message = `步骤「${failedStepLabel}」已中断。\n${localizedReason}`
      this.showInterruptAlert(signature, '任务执行失败', message)
    },
    handlePollingInterrupt (err) {
      const taskId = this.selectedTask ? this.selectedTask.id : 'none'
      const signature = `poll:${taskId}:${err && err.message ? err.message : 'network'}`
      const message = '状态轮询请求已中断，后台可能仍在继续执行，页面将继续轮询状态。'
      this.showInterruptAlert(signature, '状态同步中断', message)
    },
    handleStepInterrupt (err, action) {
      const taskId = this.selectedTask ? this.selectedTask.id : 'none'
      const stepLabel = STEP_ACTION_LABELS[action] || '当前步骤'
      const message = this.resolveInterruptMessage(err)
      if (!err || !err.response) {
        return this.showInterruptAlert(
          `network:${taskId}:${action}`,
          '请求已中断',
          `步骤「${stepLabel}」的前端请求已中断，后台可能仍在继续执行，页面将继续轮询状态。`
        )
      }
      if (err.response.status === 409 || (err.response.data && err.response.data.error === 'conflict')) {
        return this.showInterruptAlert(
          `conflict:${taskId}:${action}:${message}`,
          '任务正在执行中',
          message
        )
      }
      return this.showInterruptAlert(
        `request:${taskId}:${action}:${message}`,
        '步骤执行中断',
        `步骤「${stepLabel}」执行中断。\n${message}`
      )
    },
    autoSlug () {
      this.createForm.slug = this.generateLanguagePackSlug()
    },
    generateLanguagePackSlug () {
      const timePart = Date.now().toString(36)
      const randomPart = Math.random().toString(36).slice(2, 10)
      return `lp-${timePart}-${randomPart}`
    },
    doCreateTask () {
      if (!this.createForm.slug) this.autoSlug()
      if (!this.createForm.name) {
        this.$error('请输入内容包名称')
        return
      }
      if (!this.createForm.slug) {
        this.$error('Slug 生成失败，请检查名称')
        return
      }
      if (this.createFiles.length === 0) {
        this.$error('请选择课件文件')
        return
      }
      this.creating = true
      const formData = new FormData()
      formData.append('name', this.createForm.name)
      formData.append('slug', this.createForm.slug)
      formData.append('primary_language', this.createForm.primary_language)
      formData.append('enable_objective_questions', String(this.createForm.enable_objective_questions))
      this.createFiles.forEach(file => {
        if (file && file.raw) {
          formData.append('files', file.raw)
        }
      })

      const shouldBatchRun = this.initMode === 'batch_review'
      api.createLanguagePackInitTask(formData).then(res => {
        this.creating = false
        this.showCreateDialog = false
        this.loadTasks()
        this.selectedTask = res.data.data
        this.createForm = { name: '', slug: '', primary_language: 'Python3', enable_objective_questions: false }
        this.createFiles = []
        if (shouldBatchRun) {
          this.$nextTick(() => this.runAllSteps())
        }
      }).catch(err => {
        this.creating = false
        const msg = (err && err.response && err.response.data && err.response.data.data)
          || (err && err.message)
          || '创建失败，请检查网络或服务端配置'
        this.$error(msg)
      })
    },
    onCreateFileChange (file, fileList) {
      this.createFiles = fileList
    },
    onCreateFileRemove (file, fileList) {
      this.createFiles = fileList
    },
    removeCreateFile (idx) {
      this.createFiles.splice(idx, 1)
    },
    onFileDragStart (idx, evt) {
      this.fileDragIdx = idx
      evt.dataTransfer.effectAllowed = 'move'
    },
    onFileDragOver (idx, evt) {
      evt.dataTransfer.dropEffect = 'move'
    },
    onFileDrop (targetIdx) {
      if (this.fileDragIdx === null || this.fileDragIdx === targetIdx) return
      const item = this.createFiles.splice(this.fileDragIdx, 1)[0]
      this.createFiles.splice(targetIdx, 0, item)
      this.fileDragIdx = null
    },
    onCreateBtnNativeClick () {
      if (this.creating || this.createFiles.length === 0) return
      this.doCreateTask()
    },
    onImportBtnNativeClick () {
      if (this.importing || !this.importFileRaw) return
      this.doImportTask()
    },
    advanceCreateStep () {
      if (this.initMode === 'import') {
        this.createStep = 'import'
      } else {
        this.createStep = 'form'
      }
    },
    onCreateDialogClose () {
      this.createStep = 'mode'
      this.initMode = 'step_review'
      this.importFileList = []
      this.importFileRaw = null
      this.importPreview = null
    },
    onImportFileChange (file) {
      this.importFileList = [file]
      this.importFileRaw = file.raw || null
      if (!this.importFileRaw) return
      const reader = new FileReader()
      reader.onload = (e) => {
        try {
          const data = JSON.parse(e.target.result)
          if (!data || !data.language_pack || data.format_version !== 1) {
            this.importPreview = null
            this.$message.error('文件格式无效：不是有效的课程内容包导出文件')
            this.importFileList = []
            this.importFileRaw = null
            return
          }
          this.importPreview = {
            name: data.language_pack.name || '-',
            slug: data.language_pack.slug || '-',
            primary_language: data.language_pack.primary_language || '-',
            chapter_count: Array.isArray(data.chapters) ? data.chapters.length : 0,
            kc_count: Array.isArray(data.knowledge_components) ? data.knowledge_components.length : 0,
            example_count: Array.isArray(data.examples) ? data.examples.length : 0,
            candidate_count: Array.isArray(data.candidates) ? data.candidates.length : 0
          }
        } catch {
          this.importPreview = null
          this.$message.error('文件解析失败：不是有效的 JSON 文件')
          this.importFileList = []
          this.importFileRaw = null
        }
      }
      reader.readAsText(this.importFileRaw)
    },
    onImportFileRemove () {
      this.importFileList = []
      this.importFileRaw = null
      this.importPreview = null
    },
    doImportTask () {
      if (!this.importFileRaw) return
      this.importing = true
      api.importLanguagePack(this.importFileRaw).then(res => {
        this.importing = false
        this.showCreateDialog = false
        this.loadLanguagePackOptions().then(() => {
          if (res.data && res.data.data) {
            this.selectedTask = res.data.data
            this.loadTaskDetails()
          }
        })
      }).catch(() => {
        this.importing = false
      })
    },
    canDeleteRow (row) {
      return !!row
    },
    async confirmDeleteTask (row) {
      if (!row) return
      const pack = row.language_pack
      const name = pack.name
      const problemCount = pack.problem_count || 0
      const kcCount = pack.kc_count || 0
      const msg = `确定要删除课程内容包「${name}」及其所有数据？\n` +
        `关联数据：${problemCount} 道题目、${kcCount} 个知识点将被同步清理。\n` +
        `此操作不可恢复。`
      try {
        await ElMessageBox.confirm(msg, '删除确认', {
          confirmButtonText: '删除',
          cancelButtonText: '取消',
          type: 'warning'
        })
      } catch {
        return
      }
      api.deleteLanguagePackInitTask(row.id).then(() => {
        if (this.selectedTask && this.selectedTask.id === row.id) {
          this.selectedTask = null
        }
        this.loadLanguagePackOptions()
      }).catch(() => {})
    },
    doExportTask (row) {
      if (!row) return
      this.exportingRowId = row.id
      api.exportLanguagePack(row.id).then(res => {
        this.exportingRowId = null
        const blob = new Blob([res.data], { type: 'application/json' })
        const url = URL.createObjectURL(blob)
        const link = document.createElement('a')
        const slug = row.language_pack.slug || 'language-pack'
        link.href = url
        link.download = 'language-pack-' + slug + '.json'
        link.click()
        URL.revokeObjectURL(url)
      }).catch(() => {
        this.exportingRowId = null
      })
    },
    async runAllSteps () {
      if (!this.selectedTask) return
      this.runningAll = true
      const taskId = this.selectedTask.id
      try {
        const res = await api.startLanguagePackPipelineJob(taskId, STEP_REQUEST_OPTIONS)
        this.pipelineJob = res.data && res.data.data ? res.data.data : null
        this.persistPipelineJob(taskId, this.pipelineJob)
        this.startPipelineJobPolling(0)
        await this.refreshSelected()
      } catch (err) {
        this.handleStepInterrupt(err, 'pipeline-job')
        this.refreshSelected()
      } finally {
        this.runningAll = false
        this.loadTasks()
      }
    },
    async cancelPipelineJob () {
      if (!this.selectedTask || !this.pipelineJob || !this.pipelineJob.job_id) return
      this.pipelineJobLoading = true
      try {
        const res = await api.cancelLanguagePackPipelineJob(this.selectedTask.id, this.pipelineJob.job_id, STEP_REQUEST_OPTIONS)
        this.pipelineJob = res.data && res.data.data ? res.data.data : null
        this.persistPipelineJob(this.selectedTask.id, this.pipelineJob)
        await this.refreshSelected()
      } finally {
        this.pipelineJobLoading = false
      }
    },
    async retryPipelineJob () {
      if (!this.selectedTask || !this.pipelineJob || !this.pipelineJob.job_id) return
      this.pipelineJobLoading = true
      try {
        const res = await api.retryLanguagePackPipelineJob(this.selectedTask.id, this.pipelineJob.job_id, STEP_REQUEST_OPTIONS)
        this.pipelineJob = res.data && res.data.data ? res.data.data : null
        this.persistPipelineJob(this.selectedTask.id, this.pipelineJob)
        this.startPipelineJobPolling(0)
        await this.refreshSelected()
      } finally {
        this.pipelineJobLoading = false
      }
    },
    async refreshSelected () {
      if (!this.selectedTask) return
      try {
        const taskId = this.selectedTask.id
        await this.syncTaskProgress(taskId)
      } catch (err) {
        this.handlePollingInterrupt(err)
      }
    }
  }
}
</script>

<style scoped lang="less">
.language-pack-init {
  .language-pack-select {
    width: 220px;
    margin-right: 8px;
  }

  .lp-name {
    font-weight: 600;
    color: #0f172a;
    display: block;
    line-height: 1.4;
    width: 100%;
  }

  .lp-name-cell {
    display: flex;
    justify-content: center;
    width: 100%;
  }

  .lp-name-button {
    width: 100%;
    max-width: 480px;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 2px;
    padding: 10px 12px;
    border: 1px solid transparent;
    border-radius: 12px;
    background: linear-gradient(135deg, rgba(255, 255, 255, 0.98) 0%, rgba(239, 246, 255, 0.92) 100%);
    box-shadow: 0 8px 18px rgba(148, 163, 184, 0.08);
    cursor: pointer;
    text-align: center;
    transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease;

    &:hover {
      transform: translateY(-1px);
      border-color: rgba(37, 99, 235, 0.2);
      box-shadow: 0 12px 24px rgba(37, 99, 235, 0.12);
    }

    &:focus-visible {
      outline: 2px solid rgba(37, 99, 235, 0.35);
      outline-offset: 2px;
    }

    &.is-active {
      border-color: rgba(37, 99, 235, 0.36);
      background: linear-gradient(135deg, rgba(239, 246, 255, 0.96) 0%, rgba(219, 234, 254, 0.92) 100%);
      box-shadow: 0 16px 30px rgba(37, 99, 235, 0.14);
    }
  }

  .lp-meta {
    font-size: 12px;
    color: #94a3b8;
    width: 100%;
  }

  .stats-badges {
    display: inline-flex;
    gap: 8px;
    flex-wrap: wrap;
    justify-content: center;
  }

  .stat-badge {
    font-size: 12px;
    white-space: nowrap;
    color: #475569;
    background: rgba(148, 163, 184, 0.1);
    padding: 2px 8px;
    border-radius: 6px;

    &--highlight {
      background: rgba(37, 99, 235, 0.1);
      color: #2563eb;
      font-weight: 600;
    }
  }

  .task-detail-panel {
    margin-top: 20px;
  }


  .failure-banner {
    background: linear-gradient(135deg, #fef2f2 0%, #fee2e2 100%);
    border: 1px solid #fca5a5;
    border-radius: 10px;
    padding: 14px 18px;
    margin-bottom: 18px;
    color: #991b1b;
    font-size: 14px;
    line-height: 1.5;

    strong {
      margin-right: 6px;
    }
  }

  .timeline-section {
    margin: 20px 0 24px;
    padding: 0 12px;
  }

  .active-progress-line {
    margin-top: 16px;
    padding: 12px 14px;
    border-radius: 12px;
    background: linear-gradient(135deg, rgba(239, 246, 255, 0.9) 0%, rgba(219, 234, 254, 0.9) 100%);
    border: 1px solid rgba(59, 130, 246, 0.18);
    color: #1d4ed8;
    font-size: 14px;
    line-height: 1.5;
    text-align: center;
    font-weight: 500;
  }

  .stage-step-marker {
    width: 34px;
    height: 34px;
    border-radius: 999px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    border: 2px solid #cbd5e1;
    background: #ffffff;
    color: #64748b;
    box-shadow: 0 8px 20px rgba(148, 163, 184, 0.12);

    &.is-success {
      border-color: #22c55e;
      color: #16a34a;
      background: linear-gradient(135deg, #f0fdf4 0%, #dcfce7 100%);
    }

    &.is-process {
      border-color: #2563eb;
      color: #2563eb;
      background: linear-gradient(135deg, #eff6ff 0%, #dbeafe 100%);
    }

    &.is-error {
      border-color: #ef4444;
      color: #dc2626;
      background: linear-gradient(135deg, #fef2f2 0%, #fee2e2 100%);
    }

    &.is-running {
      box-shadow: 0 12px 28px rgba(37, 99, 235, 0.22);
    }
  }

  .stage-step-index {
    font-size: 13px;
    font-weight: 700;
  }

  .stage-step-glyph {
    font-size: 18px;
    font-weight: 700;
    line-height: 1;
  }

  .stage-step-title {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-height: 24px;
    font-weight: 600;
    color: #64748b;
    transition: color 0.18s ease, transform 0.18s ease;

    &.is-success {
      color: #16a34a;
    }

    &.is-process {
      color: #1d4ed8;
    }

    &.is-error {
      color: #dc2626;
    }

    &.is-running {
      transform: translateY(-1px);
      text-shadow: 0 0 18px rgba(37, 99, 235, 0.16);
    }
  }

  .step-spinner {
    width: 16px;
    height: 16px;
    border-radius: 999px;
    border: 2px solid rgba(37, 99, 235, 0.2);
    border-top-color: #2563eb;
    animation: step-spin 0.78s linear infinite;
  }

  .action-bar {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
    padding: 16px 0;
    border-top: 1px solid rgba(148, 163, 184, 0.12);
    border-bottom: 1px solid rgba(148, 163, 184, 0.12);
    margin-bottom: 16px;
  }

  .pipeline-job-bar {
    width: 100%;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    padding: 10px 12px;
    border-radius: 10px;
    background: linear-gradient(135deg, rgba(37, 99, 235, 0.08), rgba(15, 23, 42, 0.02));
    border: 1px solid rgba(37, 99, 235, 0.14);
  }

  .pipeline-job-meta {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
  }

  .pipeline-job-title {
    font-size: 12px;
    font-weight: 700;
    color: #1d4ed8;
  }

  .pipeline-job-text {
    font-size: 12px;
    color: #475569;
  }

  .pipeline-job-actions {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .detail-tabs {
    margin-top: 8px;
  }

  .mono-text {
    font-family: 'Courier New', Courier, monospace;
    font-size: 12px;
    color: #606266;
  }

  .form-tip {
    font-size: 12px;
    color: #94a3b8;
    line-height: 1.4;
    margin-top: 4px;
  }

  .file-sort-list {
    margin-top: 12px;
    width: 100%;
  }

  .file-sort-hint {
    font-size: 12px;
    color: #94a3b8;
    margin-bottom: 6px;
  }

  .file-sort-item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px 12px;
    border: 1px solid #e2e8f0;
    border-radius: 6px;
    margin-bottom: 4px;
    background: #fff;
    cursor: grab;
    transition: box-shadow 0.15s, border-color 0.15s;

    &:hover {
      border-color: var(--primary-color, #2563eb);
      box-shadow: 0 1px 4px rgba(37, 99, 235, 0.1);
    }

    &.is-dragging {
      opacity: 0.4;
    }
  }

  .file-sort-handle {
    color: #94a3b8;
    cursor: grab;
    user-select: none;
    font-size: 14px;
  }

  .file-sort-icon {
    font-size: 14px;
  }

  .file-sort-name {
    flex: 1;
    font-size: 13px;
    color: #334155;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .file-sort-remove {
    background: none;
    border: none;
    color: #94a3b8;
    cursor: pointer;
    font-size: 14px;
    padding: 2px 4px;
    border-radius: 4px;

    &:hover {
      color: #ef4444;
      background: #fef2f2;
    }
  }

  .doc-sort-hint {
    font-size: 13px;
    color: #64748b;
    padding: 8px 12px;
    margin-bottom: 8px;
    background: #f8fafc;
    border-radius: 6px;
    border: 1px dashed #cbd5e1;
  }

  .doc-sort-actions {
    margin-top: 12px;
    display: flex;
    justify-content: flex-end;
  }

  .init-mode-select {
    padding: 4px 0;
  }

  .init-mode-desc {
    font-size: 14px;
    color: #475569;
    margin-bottom: 16px;
    font-weight: 500;
  }

  .init-mode-group {
    display: flex;
    flex-direction: column;
    gap: 12px;
    width: 100%;
  }

  .init-mode-radio {
    display: flex;
    align-items: flex-start;
    padding: 14px 16px;
    border: 1px solid rgba(148, 163, 184, 0.2);
    border-radius: 10px;
    margin: 0;
    width: 100%;
    transition: border-color 0.2s, background 0.2s;

    &.is-checked,
    &:has(input:checked) {
      border-color: #409eff;
      background: rgba(64, 158, 255, 0.04);
    }

    &:hover {
      border-color: rgba(64, 158, 255, 0.4);
    }
  }

  .init-mode-label {
    font-size: 14px;
    font-weight: 600;
    color: #0f172a;
    line-height: 1.4;
  }

  .init-mode-hint {
    font-size: 12px;
    color: #94a3b8;
    line-height: 1.4;
    margin-top: 2px;
  }

  .init-import-section {
    padding: 4px 0;
  }

  .import-preview {
    margin-top: 16px;
    padding: 16px;
    background: #f8fafc;
    border: 1px solid rgba(148, 163, 184, 0.15);
    border-radius: 10px;
  }

  .import-preview-title {
    font-size: 13px;
    font-weight: 600;
    color: #475569;
    margin-bottom: 12px;
  }

  .import-preview-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
    gap: 10px;
  }

  .import-preview-item {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  .import-preview-label {
    font-size: 12px;
    color: #94a3b8;
  }

  .import-preview-value {
    font-size: 14px;
    color: #0f172a;
    font-weight: 500;
  }
}

@keyframes step-spin {
  from {
    transform: rotate(0deg);
  }

  to {
    transform: rotate(360deg);
  }
}
</style>
