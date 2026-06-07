import { request } from './http'

export function getDashboardOverview() {
  return request('/dashboard/overview', { method: 'GET' })
}

export function getSkillGap() {
  return request('/dashboard/skill-gap')
}
