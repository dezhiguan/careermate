import { request } from './http'

export function getSalaryInsight({ role = 'Java后端', city = '广州', years = '3-5年' } = {}) {
  return request(`/market/salary-insight?role=${encodeURIComponent(role)}&city=${encodeURIComponent(city)}&years=${encodeURIComponent(years)}`)
}

export function getSkillTrends({ role = 'Java后端' } = {}) {
  return request(`/market/skill-trends?role=${encodeURIComponent(role)}`)
}

export function getResumeGap() {
  return request('/market/resume-gap')
}

export function getCompanyInsight(company) {
  return request(`/market/company-insight?company=${encodeURIComponent(company)}`)
}
