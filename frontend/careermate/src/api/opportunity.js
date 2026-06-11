import { request } from './http'

export async function listOpportunities(params = {}) {
  const query = new URLSearchParams()
  if (params.keyword) query.set('keyword', params.keyword)
  if (params.page) query.set('page', String(params.page))
  if (params.size) query.set('size', String(params.size))
  const qs = query.toString()
  return request(`/opportunity/list${qs ? `?${qs}` : ''}`, { method: 'GET' })
}

export async function getOpportunityDetail(jdId) {
  return request(`/opportunity/${encodeURIComponent(jdId)}`, { method: 'GET' })
}

export async function prepareWithAi(jdId) {
  return request(`/opportunity/${encodeURIComponent(jdId)}/prepare`, { method: 'POST' })
}
