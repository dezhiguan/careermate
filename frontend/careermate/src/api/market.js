import { request } from './http'

/** 行情查询维度字典：岗位分组（含 AI / 大模型）/ 城市 / 经验 + 默认口径。前端不再硬编码清单。 */
export function getMarketDimensions() {
  return request('/market/dimensions')
}

function query(params) {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([k, v]) => {
    const s = v == null ? '' : String(v).trim()
    if (s) search.append(k, s)
  })
  const qs = search.toString()
  return qs ? `?${qs}` : ''
}

/**
 * 薪资分位。years 传「不限」或不传都表示全经验段——不要在这里补默认年限区间，
 * 否则用户选「不限」会被静默换成某个具体区间（历史 bug）。
 */
export function getSalaryInsight({ role, city, years } = {}) {
  return request(`/market/salary-insight${query({ role, city, years })}`)
}

export function getSkillTrends({ city, role } = {}) {
  return request(`/market/skill-trends${query({ city, role })}`)
}

export function getResumeGap() {
  return request('/market/resume-gap')
}

export function getCompanyInsight(company) {
  // company 是后端必填参数，空值也要显式带上（后端自行降级），不能省略成 400
  return request(`/market/company-insight?company=${encodeURIComponent(company == null ? '' : company)}`)
}
