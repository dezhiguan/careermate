import { request } from './http'

export function listJobMatches() {
  return request('/job-matches', { method: 'GET' })
}

export function analyzeJobMatch(payload) {
  return request('/job-matches/analyze', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function getJobMatch(id) {
  return request(`/job-matches/${id}`, { method: 'GET' })
}

export function deleteJobMatch(id) {
  return request(`/job-matches/${id}`, { method: 'DELETE' })
}
