import { reactive } from 'vue'
import { getMarketDimensions } from '../api/market'

/**
 * 行情维度字典（岗位 / 城市 / 经验）的前端单一来源。
 *
 * 此前资产页、行情页、我的页各自硬编码一份岗位与城市清单，三处口径互相漂移且都缺 AI 岗位。
 * 这里统一走 `GET /market/dimensions`，进程内缓存一次。
 *
 * FALLBACK 只在接口不可用时兜底，刻意保持最小——它不是第二份清单，不要往里加岗位。
 */
const FALLBACK = Object.freeze({
  roleGroups: [
    { group: 'AI / 大模型', roles: ['AI应用工程师', '大模型算法工程师', 'AI Agent工程师'] },
    { group: '后端', roles: ['Java后端', 'Go后端', 'Python后端'] },
    { group: '前端 / 客户端', roles: ['前端开发', '全栈工程师'] },
  ],
  cities: ['不限', '北京', '上海', '广州', '深圳', '杭州', '成都'],
  years: ['不限', '应届', '1-3年', '3-5年', '5-10年', '10年以上'],
  defaultRole: 'Java后端',
  defaultCity: '广州',
  defaultYears: '不限',
})

const state = reactive({
  loaded: false,
  roleGroups: FALLBACK.roleGroups,
  cities: FALLBACK.cities,
  years: FALLBACK.years,
  defaultRole: FALLBACK.defaultRole,
  defaultCity: FALLBACK.defaultCity,
  defaultYears: FALLBACK.defaultYears,
})

let inflight = null

function apply(data) {
  if (Array.isArray(data?.roleGroups) && data.roleGroups.length) state.roleGroups = data.roleGroups
  if (Array.isArray(data?.cities) && data.cities.length) state.cities = data.cities
  if (Array.isArray(data?.years) && data.years.length) state.years = data.years
  if (data?.defaultRole) state.defaultRole = data.defaultRole
  if (data?.defaultCity) state.defaultCity = data.defaultCity
  if (data?.defaultYears) state.defaultYears = data.defaultYears
  state.loaded = true
}

async function load() {
  if (state.loaded) return state
  if (inflight) return inflight
  inflight = getMarketDimensions()
    .then((data) => {
      apply(data)
      return state
    })
    .catch(() => state) // 保持 FALLBACK，页面照常可用
    .finally(() => { inflight = null })
  return inflight
}

/** 拉平的岗位列表（datalist 等不支持分组的场景用）。 */
function flatRoles() {
  return state.roleGroups.flatMap((g) => (Array.isArray(g?.roles) ? g.roles : []))
}

/** 经验区间，去掉「不限」——画像里的资历不该出现「不限」。 */
function yearsWithoutAny() {
  return state.years.filter((y) => y !== '不限')
}

export const marketDimensionsStore = {
  state,
  load,
  flatRoles,
  yearsWithoutAny,
  ANY_CITY: '不限',
  ANY_YEARS: '不限',
}
