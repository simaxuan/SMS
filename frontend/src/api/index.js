import http from './http'

export const studentApi = {
  list: (params) => http.get('/students', { params }),
  get: (id) => http.get(`/students/${id}`),
  create: (data) => http.post('/students', data),
  update: (id, data) => http.put(`/students/${id}`, data),
  remove: (id) => http.delete(`/students/${id}`)
}

export const dictApi = {
  classes: (params) => http.get('/classes', { params }),
  createClass: (data) => http.post('/classes', data),
  updateClass: (id, data) => http.put(`/classes/${id}`, data),
  removeClass: (id) => http.delete(`/classes/${id}`),
  courses: () => http.get('/courses'),
  createCourse: (data) => http.post('/courses', data),
  updateCourse: (id, data) => http.put(`/courses/${id}`, data),
  removeCourse: (id) => http.delete(`/courses/${id}`),
  examTypes: () => http.get('/exam-types'),
  createExamType: (data) => http.post('/exam-types', data),
  updateExamType: (id, data) => http.put(`/exam-types/${id}`, data),
  removeExamType: (id) => http.delete(`/exam-types/${id}`)
}

export const examApi = {
  list: () => http.get('/exams'),
  create: (data) => http.post('/exams', data),
  update: (id, data) => http.put(`/exams/${id}`, data),
  remove: (id) => http.delete(`/exams/${id}`),
  customCreate: (data) => http.post('/exams/custom', data),
  customList: () => http.get('/exams/custom'),
  // S1 多科总分：读取/批量保存考试科目组合
  courseGroups: (params) => http.get('/exams/course-groups', { params }),
  saveCourseGroups: (data) => http.put('/exams/course-groups', data)
}

export const gradeApi = {
  list: (params) => http.get('/grades', { params }),
  create: (data) => http.post('/grades', data),
  batch: (data) => http.post('/grades/batch', data),
  update: (id, data) => http.put(`/grades/${id}`, data),
  remove: (id) => http.delete(`/grades/${id}`)
}

export const statApi = {
  course: (params) => http.get('/statistics/course', { params }),
  student: (params) => http.get('/statistics/student', { params }),
  studentTrend: (params) => http.get('/statistics/student-trend', { params }),
  comparison: (params) => http.get('/statistics/comparison', { params }),
  rank: (params) => http.get('/statistics/rank', { params }),
  progress: (params) => http.get('/statistics/progress', { params }),
  semesters: () => http.get('/statistics/semesters')
}

export const settingsApi = {
  list: () => http.get('/settings'),
  update: (key, value) => http.put(`/settings/${key}`, { value })
}

export const authApi = {
  register: (data) => http.post('/auth/register', data),
  login: (data) => http.post('/auth/login', data),
  logout: () => http.post('/auth/logout'),
  me: () => http.get('/auth/me'),
  teachers: () => http.get('/auth/teachers'),
  createTeacher: (data) => http.post('/auth/teachers', data),
  removeTeacher: (id) => http.delete(`/auth/teachers/${id}`),
  // 三遗留处理：全局超管切换登录校；schoolId 为 null 表示"全部学校总览"
  switchSchool: (schoolId) => http.post('/auth/switch-school', { schoolId })
}

// ===== 多租户组织管理（A1 新端点）=====
export const schoolApi = {
  list: () => http.get('/schools'),
  create: (data) => http.post('/schools', data),
  update: (id, data) => http.put(`/schools/${id}`, data),
  remove: (id) => http.delete(`/schools/${id}`)
}

export const levelApi = {
  list: (params) => http.get('/levels', { params }),
  create: (data) => http.post('/levels', data),
  update: (id, data) => http.put(`/levels/${id}`, data),
  remove: (id) => http.delete(`/levels/${id}`)
}

export const teacherClassApi = {
  list: (params) => http.get('/teacher-classes', { params }),
  assign: (data) => http.post('/teacher-classes/assign', data),
  remove: (id) => http.delete(`/teacher-classes/${id}`)
}

export const parentApi = {
  bind: (data) => http.post('/parent/bind', data),
  binds: () => http.get('/parent/binds'),
  unbind: (studentId) => http.delete(`/parent/binds/${studentId}`),
  preference: () => http.get('/parent/preference'),
  savePreference: (data) => http.put('/parent/preference', data)
}
