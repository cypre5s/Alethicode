function normalizeQuestionType(problem = {}) {
  return String(problem.question_type || '').trim().toLowerCase()
}

function normalizeStatus(problem = {}) {
  return String(problem.status || problem.validation_status || 'pending').trim().toLowerCase()
}

function normalizeLessonExtension(lesson = {}) {
  const filePath = String(lesson.file_path || lesson.path || '').trim().toLowerCase()
  if (!filePath) {
    return ''
  }
  if (filePath.endsWith('.pdf')) return '.pdf'
  if (filePath.endsWith('.pptx')) return '.pptx'
  if (filePath.endsWith('.ppt')) return '.ppt'
  if (filePath.endsWith('.docx')) return '.docx'
  if (filePath.endsWith('.doc')) return '.doc'
  return ''
}

function isCodingProblem(problem = {}) {
  return normalizeQuestionType(problem) === 'coding'
}

function isObjectiveProblem(problem = {}) {
  const questionType = normalizeQuestionType(problem)
  return questionType === 'choice' || questionType === 'fill_blank'
}

function isLessonSupportedForAiGeneration(lesson = {}) {
  const extension = normalizeLessonExtension(lesson)
  if (extension) {
    return extension === '.pdf' || extension === '.pptx'
  }
  const lessonType = String(lesson.lesson_type || lesson.file_type || lesson.type || '').trim().toLowerCase()
  return lessonType === 'pdf' || lessonType === 'ppt'
}

function canEditAiGeneratedProblem(problem = {}) {
  const status = normalizeStatus(problem)
  if (isCodingProblem(problem)) {
    return status === 'pending' || status === 'failed'
  }
  if (isObjectiveProblem(problem)) {
    return status === 'pending' || status === 'failed'
  }
  return false
}

function canDeleteAiGeneratedProblem(problem = {}) {
  return canEditAiGeneratedProblem(problem)
}

function canReviewPassAiGeneratedProblem(problem = {}) {
  if (!isObjectiveProblem(problem)) {
    return false
  }
  const status = normalizeStatus(problem)
  return status === 'pending' || status === 'failed'
}

function canReviewRejectAiGeneratedProblem(problem = {}) {
  return isObjectiveProblem(problem) && normalizeStatus(problem) === 'passed'
}

function canValidateAiGeneratedProblem(problem = {}) {
  if (!isCodingProblem(problem)) {
    return false
  }
  const status = normalizeStatus(problem)
  return status === 'pending' || status === 'failed'
}

function canPublishAiGeneratedProblem(problem = {}) {
  return normalizeStatus(problem) === 'passed'
}

export {
  isLessonSupportedForAiGeneration,
  canEditAiGeneratedProblem,
  canDeleteAiGeneratedProblem,
  canReviewPassAiGeneratedProblem,
  canReviewRejectAiGeneratedProblem,
  canValidateAiGeneratedProblem,
  canPublishAiGeneratedProblem
}
