import { request } from './http'

/** 暂存区列表。 */
export async function listSavedJobs() {
  const resp = await request('/saved-jobs')
  const data = resp?.data ?? resp
  return Array.isArray(data) ? data : []
}

/** 收藏一个 JD（幂等）。 */
export function saveJob(payload) {
  return request('/saved-jobs', { method: 'POST', body: JSON.stringify(payload || {}) })
}

/** 取消收藏（按 jdDocId）。 */
export function unsaveJob(jdDocId) {
  return request(`/saved-jobs/${jdDocId}`, { method: 'DELETE' })
}

/** 一键转为机会。 */
export function promoteJob(jdDocId) {
  return request(`/saved-jobs/${jdDocId}/promote`, { method: 'POST' })
}
