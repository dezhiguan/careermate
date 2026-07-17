// 卡片区分项（设计 v2.3：公司·职位·区分项，优先级 职级 > 产品线 > 城市 > 序号）。
// 职级/产品线用正则从标题抽（无后端字段）；仅在「同公司·同职位」碰撞组内生成区分项。

const LEVEL_RE = /(资深|高级|初级|中级|专家|首席|principal|staff|senior|junior|架构师|主管|负责人|总监|P\d{1,2}|T\d(?:\.\d)?|M\d)/i

/** 从标题抽职级修饰词（区分项优先级最高）。 */
export function extractLevel(title) {
  if (!title) return ''
  const m = String(title).match(LEVEL_RE)
  return m ? m[1] : ''
}

const LINE_TOKENS = [
  '抖音', '电商', '支付', '国际化', '广告', '搜索', '推荐', '直播', '游戏', '金融',
  '出行', '本地生活', '社区', '短视频', '商业化', '增长', '基础架构', '中台', '大数据',
  '风控', '客户端', '服务端', '平台', '云',
]
const BRACKET_RE = /[【(（]([^】)）]{1,10})[】)）]/

/** 从标题抽产品线：优先【】/()内短语，否则已知产品线 token。 */
export function extractProductLine(title) {
  if (!title) return ''
  const s = String(title)
  const b = s.match(BRACKET_RE)
  if (b) return b[1].trim()
  for (const t of LINE_TOKENS) {
    if (s.includes(t)) return t
  }
  return ''
}

const CIRCLED = '②③④⑤⑥⑦⑧⑨'

function seqSuffix(i) {
  return CIRCLED[i - 1] || (i > 0 ? `·${i + 1}` : '')
}

/**
 * 计算每张卡片的区分项。返回 { [id]: 区分项字符串 }（仅碰撞组内非空）。
 * accessors: getId/getCompany/getRole/getCity。
 */
export function cardDistinguishers(items, accessors = {}) {
  const getId = accessors.getId || ((x) => x.id)
  const getCompany = accessors.getCompany || ((x) => x.company)
  const getRole = accessors.getRole || ((x) => x.roleTitle || x.title)
  const getCity = accessors.getCity || ((x) => x.city)

  const map = {}
  const groups = {}
  for (const it of items || []) {
    const key = `${getCompany(it) || ''}|${getRole(it) || ''}`
    ;(groups[key] = groups[key] || []).push(it)
  }
  for (const grp of Object.values(groups)) {
    if (grp.length < 2) continue
    // 每张卡的候选区分项：职级 > 产品线 > 城市
    const cands = grp.map((it) => extractLevel(getRole(it)) || extractProductLine(getRole(it)) || getCity(it) || '')
    const counts = {}
    cands.forEach((c) => { if (c) counts[c] = (counts[c] || 0) + 1 })
    grp.forEach((it, i) => {
      const c = cands[i]
      // 候选能唯一区分该卡 → 用候选；否则回退序号
      map[getId(it)] = (c && counts[c] === 1) ? c : seqSuffix(i)
    })
  }
  return map
}
