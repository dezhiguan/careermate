import { request } from './http'

// B3 断点续跑：运行历史 / 发起 / 详情 / 续跑 / 分叉
export function startRun(topic) {
  return request('/agent/checkpoint/runs', { method: 'POST', body: JSON.stringify({ topic }) })
}

export function listRuns() {
  return request('/agent/checkpoint/runs', { method: 'GET' })
}

export function getRun(runId) {
  return request(`/agent/checkpoint/runs/${encodeURIComponent(runId)}`, { method: 'GET' })
}

export function resumeRun(runId, decision) {
  return request(`/agent/checkpoint/runs/${encodeURIComponent(runId)}/resume`, {
    method: 'POST',
    body: JSON.stringify({ decision }),
  })
}

export function forkRun(runId) {
  return request(`/agent/checkpoint/runs/${encodeURIComponent(runId)}/fork`, { method: 'POST' })
}
