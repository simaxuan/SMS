import { describe, it, expect, vi, beforeEach } from 'vitest'

// mock 底层 http 实例，隔离 axios，纯测 api/index.js 的「方法 → http 调用」契约。
// 工厂被提升到顶部，故 httpMock 必须经 vi.hoisted 创建，避免 "Cannot access before initialization"。
const { httpMock } = vi.hoisted(() => ({
  httpMock: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() }
}))
vi.mock('../src/api/http', () => ({ default: httpMock }))

// 需在 mock 之后再 import，确保拿到 mocked module
import {
  studentApi,
  dictApi,
  examApi,
  gradeApi,
  statApi,
  settingsApi,
  authApi,
  schoolApi,
  levelApi,
  teacherClassApi,
  parentApi
} from '../src/api'

describe('API 契约（api/index.js → http 调用一致）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('studentApi：list 带 params、get/create/update/remove 路径正确', () => {
    studentApi.list({ page: 1, size: 10 })
    expect(httpMock.get).toHaveBeenCalledWith('/students', { params: { page: 1, size: 10 } })

    studentApi.get(5)
    expect(httpMock.get).toHaveBeenCalledWith('/students/5')

    studentApi.create({ name: '张三' })
    expect(httpMock.post).toHaveBeenCalledWith('/students', { name: '张三' })

    studentApi.update(5, { name: '李四' })
    expect(httpMock.put).toHaveBeenCalledWith('/students/5', { name: '李四' })

    studentApi.remove(5)
    expect(httpMock.delete).toHaveBeenCalledWith('/students/5')
  })

  it('dictApi：班级/课程/考试类型 CRUD 映射正确', () => {
    dictApi.classes()
    expect(httpMock.get).toHaveBeenCalledWith('/classes')

    dictApi.createClass({ name: '一班', schoolId: 1 })
    expect(httpMock.post).toHaveBeenCalledWith('/classes', { name: '一班', schoolId: 1 })

    dictApi.updateCourse(3, { name: '数学', fullScore: 100 })
    expect(httpMock.put).toHaveBeenCalledWith('/courses/3', { name: '数学', fullScore: 100 })

    dictApi.examTypes()
    expect(httpMock.get).toHaveBeenCalledWith('/exam-types')

    dictApi.removeExamType(2)
    expect(httpMock.delete).toHaveBeenCalledWith('/exam-types/2')
  })

  it('examApi：基本 CRUD + 自定义 + 科目组合契约一致', () => {
    examApi.create({ name: '期中' })
    expect(httpMock.post).toHaveBeenCalledWith('/exams', { name: '期中' })

    examApi.customCreate({ name: '小测' })
    expect(httpMock.post).toHaveBeenCalledWith('/exams/custom', { name: '小测' })

    examApi.courseGroups({ examId: 1 })
    expect(httpMock.get).toHaveBeenCalledWith('/exams/course-groups', { params: { examId: 1 } })

    examApi.saveCourseGroups({ examId: 1, courses: [1, 2] })
    expect(httpMock.put).toHaveBeenCalledWith('/exams/course-groups', { examId: 1, courses: [1, 2] })
  })

  it('gradeApi：list 带 params、batch 走 post 直传 body', () => {
    gradeApi.list({ examId: 1, courseId: 2 })
    expect(httpMock.get).toHaveBeenCalledWith('/grades', { params: { examId: 1, courseId: 2 } })

    gradeApi.batch([{ studentId: 1, score: 90 }])
    expect(httpMock.post).toHaveBeenCalledWith('/grades/batch', [{ studentId: 1, score: 90 }])

    gradeApi.create({ studentId: 1, score: 88 })
    expect(httpMock.post).toHaveBeenCalledWith('/grades', { studentId: 1, score: 88 })

    gradeApi.remove(7)
    expect(httpMock.delete).toHaveBeenCalledWith('/grades/7')
  })

  it('statApi：统计各端点路径与三口径相关参数契约一致', () => {
    statApi.course({ examId: 1, courseId: 2 })
    expect(httpMock.get).toHaveBeenCalledWith('/statistics/course', { params: { examId: 1, courseId: 2 } })

    statApi.rank({ examId: 1, courseId: 2, classScope: true })
    expect(httpMock.get).toHaveBeenCalledWith('/statistics/rank', { params: { examId: 1, courseId: 2, classScope: true } })

    statApi.comparison({ examId: 1, courseId: 2, semester: '2026春' })
    expect(httpMock.get).toHaveBeenCalledWith('/statistics/comparison', {
      params: { examId: 1, courseId: 2, semester: '2026春' }
    })

    statApi.progress({ courseId: 2, limit: 10, regress: false })
    expect(httpMock.get).toHaveBeenCalledWith('/statistics/progress', {
      params: { courseId: 2, limit: 10, regress: false }
    })

    statApi.student({ examId: 1, studentId: 5 })
    expect(httpMock.get).toHaveBeenCalledWith('/statistics/student', { params: { examId: 1, studentId: 5 } })

    statApi.studentTrend({ studentId: 5 })
    expect(httpMock.get).toHaveBeenCalledWith('/statistics/student-trend', { params: { studentId: 5 } })

    statApi.semesters()
    expect(httpMock.get).toHaveBeenCalledWith('/statistics/semesters')
  })

  it('settingsApi：读列表/按 key 更新', () => {
    settingsApi.list()
    expect(httpMock.get).toHaveBeenCalledWith('/settings')

    settingsApi.update('std_weight_mode', 'full-weighted')
    expect(httpMock.put).toHaveBeenCalledWith('/settings/std_weight_mode', { value: 'full-weighted' })
  })

  it('authApi：注册/登录/登出/me/老师列表骨架一致', () => {
    authApi.login({ username: 'a', password: 'x' })
    expect(httpMock.post).toHaveBeenCalledWith('/auth/login', { username: 'a', password: 'x' })

    authApi.logout()
    expect(httpMock.post).toHaveBeenCalledWith('/auth/logout')

    authApi.me()
    expect(httpMock.get).toHaveBeenCalledWith('/auth/me')

    authApi.teachers()
    expect(httpMock.get).toHaveBeenCalledWith('/auth/teachers')
  })

  it('多租户组织 API：school/level/teacherClass 映射一致', () => {
    schoolApi.list()
    expect(httpMock.get).toHaveBeenCalledWith('/schools')

    schoolApi.create({ name: '一中' })
    expect(httpMock.post).toHaveBeenCalledWith('/schools', { name: '一中' })

    levelApi.list({ schoolId: 1 })
    expect(httpMock.get).toHaveBeenCalledWith('/levels', { params: { schoolId: 1 } })

    teacherClassApi.assign({ teacherId: 1, classIds: [1, 2] })
    expect(httpMock.post).toHaveBeenCalledWith('/teacher-classes/assign', { teacherId: 1, classIds: [1, 2] })
  })

  it('parentApi：绑定/解绑/偏好契约一致', () => {
    parentApi.bind({ studentId: 5, phone: '138' })
    expect(httpMock.post).toHaveBeenCalledWith('/parent/bind', { studentId: 5, phone: '138' })

    parentApi.binds()
    expect(httpMock.get).toHaveBeenCalledWith('/parent/binds')

    parentApi.unbind(5)
    expect(httpMock.delete).toHaveBeenCalledWith('/parent/binds/5')

    parentApi.savePreference({ classScope: true })
    expect(httpMock.put).toHaveBeenCalledWith('/parent/preference', { classScope: true })
  })
})
