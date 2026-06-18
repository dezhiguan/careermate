import { request } from './http'

export function getHomeBootstrap() {
  return request('/home/bootstrap', { method: 'GET' })
}
