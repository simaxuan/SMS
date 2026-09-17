import http from './http'

export const studentApi = {
  list: (params) => http.get('/students', { params }),
  get: (id) => http.get(`/students/${id}`),
  create: (data) => http.post('/students', data),
  update: (id, data) => http.put(`/students/${id}`, data),
  remove: (id) => http.delete(`/students/${id}`)
}

export const dictApi = {
  classes: () => http.get('/classes'),
  createClass: (data) => http.post('/classes', data),
  updateClass: (id, data) => http.put(`/classes/${id}`, data),
  removeClass: (id) => http.delete(`/classes/${id}`),
  courses: () => http.get('/courses'),
  createCourse: (data) => http.post('/courses', data),
  updateCourse: (id, data) => http.put(`/courses/${id}`, data),
  removeCourse: (id) => http.delete(`/courses/${id}`)
}

export const examApi = {
  list: () => http.get('/exams'),
  create: (data) => http.post('/exams', data),
  update: (id, data) => http.put(`/exams/${id}`, data),
  remove: (id) => http.delete(`/exams/${id}`)
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
  comparison: (params) => http.get('/statistics/comparison', { params })
}
