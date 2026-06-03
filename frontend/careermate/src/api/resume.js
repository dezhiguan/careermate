import { request } from './http'

export function listResumes() {
  return request('/resumes', { method: 'GET' })
}

export function createResume(payload) {
  return request('/resumes', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function getResume(id) {
  return request(`/resumes/${id}`, { method: 'GET' })
}

export function updateResume(id, payload) {
  return request(`/resumes/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function deleteResume(id) {
  return request(`/resumes/${id}`, { method: 'DELETE' })
}

export function setDefaultResume(id) {
  return request(`/resumes/${id}/default`, { method: 'POST' })
}
