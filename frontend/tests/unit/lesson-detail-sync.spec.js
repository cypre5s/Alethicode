/* eslint-env jest */

const {
  fetchFreshLessonDetail,
  mergeLessonIntoList,
  resolveSelectedLessonPages
} = require('../../src/pages/oj/views/classroom/lessonDetailSync')

describe('lessonDetailSync', () => {
  test('fetchFreshLessonDetail should replace stale total_pages with lesson detail', async () => {
    const staleLesson = {
      id: 'lesson-1',
      title: '第二章：Python 语言基础.pptx',
      file_type: 'ppt',
      total_pages: 1
    }
    const api = {
      getLesson: jest.fn().mockResolvedValue({
        data: {
          data: {
            id: 'lesson-1',
            title: '第二章：Python 语言基础.pptx',
            file_type: 'ppt',
            total_pages: 73
          }
        }
      })
    }
    const normalizeLesson = jest.fn(item => ({
      ...item,
      lesson_type: item.lesson_type || item.file_type
    }))

    const result = await fetchFreshLessonDetail({
      api,
      classroomId: 'classroom-1',
      lesson: staleLesson,
      lessons: [staleLesson],
      normalizeLesson
    })

    expect(api.getLesson).toHaveBeenCalledWith('classroom-1', 'lesson-1')
    expect(result.freshLesson.total_pages).toBe(73)
    expect(result.lessons).toHaveLength(1)
    expect(result.lessons[0].total_pages).toBe(73)
  })

  test('mergeLessonIntoList should append lesson when local list is missing it', () => {
    const merged = mergeLessonIntoList([], {
      id: 'lesson-2',
      title: '第三章：控制流.pptx',
      total_pages: 45
    })

    expect(merged).toHaveLength(1)
    expect(merged[0].total_pages).toBe(45)
  })

  test('resolveSelectedLessonPages should refresh selected lesson even when stale page count is 1', async () => {
    const lessons = [{
      id: 'lesson-3',
      title: '第二章：Python 语言基础.pptx',
      file_type: 'ppt',
      total_pages: 1
    }]
    const api = {
      getLesson: jest.fn().mockResolvedValue({
        data: {
          data: {
            id: 'lesson-3',
            title: '第二章：Python 语言基础.pptx',
            file_type: 'ppt',
            total_pages: 73
          }
        }
      })
    }
    const normalizeLesson = jest.fn(item => item)

    const result = await resolveSelectedLessonPages({
      api,
      classroomId: 'classroom-1',
      lessonId: 'lesson-3',
      lessons,
      normalizeLesson
    })

    expect(result.maxPages).toBe(73)
    expect(result.lessons[0].total_pages).toBe(73)
  })
})
