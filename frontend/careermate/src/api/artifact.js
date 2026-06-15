import { request } from './http'

export async function listRecentArtifacts(limit = 5) {
  const qs = limit ? `?limit=${encodeURIComponent(String(limit))}` : ''
  return request(`/artifacts/recent${qs}`, { method: 'GET' })
}

export async function listArtifacts({ type, sessionId, limit } = {}) {
  const query = new URLSearchParams()
  if (type) query.set('type', type)
  if (sessionId) query.set('sessionId', sessionId)
  if (limit != null) query.set('limit', String(limit))
  const qs = query.toString()
  return request(`/artifacts${qs ? `?${qs}` : ''}`, { method: 'GET' })
}
