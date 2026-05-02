function mergeLessonIntoList (lessons = [], lesson = {}) {
  const sourceLessons = Array.isArray(lessons) ? lessons : []
  if (!lesson || !lesson.id) {
    return sourceLessons.slice()
  }

  let matched = false
  const nextLessons = sourceLessons.map(item => {
    if (item && item.id === lesson.id) {
      matched = true
      return {
        ...item,
        ...lesson
      }
    }
    return item
  })

  if (!matched) {
    nextLessons.push(lesson)
  }

  return nextLessons
}

function getLessonPageCount (lesson = {}) {
  return lesson.total_pages || lesson.page_count || 0
}

async function fetchFreshLessonDetail ({ api, classroomId, lesson, lessons, normalizeLesson }) {
  const response = await api.getLesson(classroomId, lesson.id)
  const responseData = response && response.data ? response.data.data : null
  const freshLesson = normalizeLesson({
    ...lesson,
    ...(responseData || {})
  })

  return {
    freshLesson,
    lessons: mergeLessonIntoList(lessons, freshLesson)
  }
}

async function resolveSelectedLessonPages ({ api, classroomId, lessonId, lessons, normalizeLesson }) {
  const sourceLessons = Array.isArray(lessons) ? lessons : []
  const selectedLesson = sourceLessons.find(lesson => lesson && lesson.id === lessonId)
  if (!selectedLesson) {
    return {
      freshLesson: null,
      lessons: sourceLessons.slice(),
      maxPages: 0
    }
  }

  const { freshLesson, lessons: nextLessons } = await fetchFreshLessonDetail({
    api,
    classroomId,
    lesson: selectedLesson,
    lessons: sourceLessons,
    normalizeLesson
  })

  return {
    freshLesson,
    lessons: nextLessons,
    maxPages: getLessonPageCount(freshLesson)
  }
}

export {
  getLessonPageCount,
  mergeLessonIntoList,
  fetchFreshLessonDetail,
  resolveSelectedLessonPages
}
