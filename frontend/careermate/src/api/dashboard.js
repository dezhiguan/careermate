import { request } from './http'

export function getDashboardOverview() {
  return request('/dashboard/overview', { method: 'GET' })
}
