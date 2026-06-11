# CareerMate 设计资产

## 目录结构

```
docs/design/
├── README.md                        ← 你现在看的这份索引
├── 00-design-system.md              ★ 设计系统(写代码前必读)
├── careermate-ux-v5-flow.html       原始原型(完整版,9 个 section 在一起)
├── CareerMate-architecture-v2.1.html (历史架构图,不用看)
├── CareerMate-prototype-design.html  (历史原型,不用看)
└── prototypes/                      ★ 拆分后的单页原型
    ├── 01-shell.html                导航壳(Web sidebar + 移动 5 tab)
    ├── 02-login.html                登录页
    ├── 03-opportunity.html          机会 tab
    ├── 04-chat-with-resume.html     AI 小职 · 有简历场景
    ├── 05-chat-no-resume.html       AI 小职 · 无简历场景
    ├── 06-interview.html            面试题 tab
    ├── 07-market.html               市场行情 tab
    ├── 08-agent-capability.html     Agent 能力图谱(产品参考,不用 1:1 复刻)
    └── 09-mine.html                 我的 tab
```

---

## Cursor 工作流(必看)

### 写后端代码
不需要看 design 目录。看 `docs/execution-plan-v9.md`(如果有)或架构师给的提示词。

### 写前端代码
**严格按顺序读以下文件再动手**:

1. **`00-design-system.md`** — 色板/字号/圆角/间距/组件硬规范
2. **`prototypes/01-shell.html`** — 导航壳通用结构(任何前端任务都要看)
3. **对应业务原型** — 比如写 `OpportunityView.vue` 就 Read `03-opportunity.html`

### 文件 → 视图映射(给 Cursor)

| 你要改/新建的文件 | 必须 Read 的原型 |
|---|---|
| `AppShellMobile.vue` / `AppShellDesktop.vue` | `01-shell.html` |
| `LoginView.vue` | `02-login.html` |
| `OpportunityView.vue`(机会 tab) | `03-opportunity.html` |
| `AgentChat.vue` 改造 | `04-chat-with-resume.html` + `05-chat-no-resume.html` |
| `InterviewView.vue` 改造 | `06-interview.html` |
| `MarketView.vue`(新建) | `07-market.html` |
| `MineView.vue`(新建) | `09-mine.html` |

---

## 铁律(给 Cursor)

1. **不许凭印象写 UI** — 必须 Read 对应原型 + design-system
2. **不许引入新色 / 新字号** — 只能用 design-system 里的
3. **不许改原型** — 这是只读的设计冻结版本
4. **遇到原型没说的细节** — 问架构师,不许自己发挥
5. **写完一个视图后** — 在 PR 描述里贴出对照截图(原型 vs 实现)

---

## 后续(可能的话)

如果设计有迭代,把新版原型放进 `prototypes/v10/` 子目录,不要覆盖现有文件。
保留版本可追溯。
