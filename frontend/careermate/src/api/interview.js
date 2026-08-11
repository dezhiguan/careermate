import { request } from './http'

export function listInterviewSessions() {
  return request('/interview-sessions', { method: 'GET' })
}

export function createInterviewSession(payload) {
  return request('/interview-sessions', {
    method: 'POST',
    body: JSON.stringify(payload ?? {}),
  })
}

export function getInterviewSession(id) {
  return request(`/interview-sessions/${id}`, { method: 'GET' })
}

export function submitInterviewAnswer(sessionId, questionId, payload) {
  return request(`/interview-sessions/${sessionId}/questions/${questionId}/answer`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function completeInterviewSession(sessionId) {
  return request(`/interview-sessions/${sessionId}/complete`, { method: 'POST' })
}

export function renameInterviewSession(sessionId, title) {
  return request(`/interview-sessions/${sessionId}`, {
    method: 'PATCH',
    body: JSON.stringify({ title }),
  })
}

export function deleteInterviewSession(sessionId) {
  return request(`/interview-sessions/${sessionId}`, { method: 'DELETE' })
}

export function getKbQuestions(query) {
  return request(`/interview/kb-questions?query=${encodeURIComponent(query)}`)
}

export function getCompanyPrep(company) {
  return request(`/interview/company-prep?company=${encodeURIComponent(company)}`)
}
