# CareerMate 账号注销 / 撤销 · 测试用例 V1.0

**拟制日期**：2026-07-12
**版本**：V1.0
**被测功能**：应用级账号注销 / 撤销（委托 auth-gateway membership + 事件驱动清理 + 注销中中间页）
**设计依据**：`docs/design/account-cancellation-redesign-v1.md`（V3.0）
**状态**：已执行（2026-07-12，见 `account-cancellation-test-execution-v1.md`，0 Fail，2 隐藏缺陷已修）
**用例总数**：126

---

## 架构要点（测试前须知）

- **应用级注销**：CareerMate 注销只退 careermate，委托网关把 `user_app_membership(app=careermate)` 置 PENDING_DELETION（30 天冷静期），**不影响 RAGForge 与共享身份**。
- **注销即登出**：CareerMate 侧吊销该用户全部会话（用户级 revoke + 删本地会话行）。
- **冷静期内**：登录放行（网关只拦 DELETED），前端据 status=CANCELLING 落「注销中」中间页。
- **到期清理**：网关每日 cron（行级锁）→ membership DELETED → 发 `user.app_removed{user_id,app}` → CareerMate 收事件 → `LocalAccountPurger` 匿名化 user 主记录 PII + 删个人内容（简历/对话/投递/面试等）。
- **自愈**：CareerMate 登录时 `ensureMembership`（幂等），存量短信用户补建 careermate membership。
- **相关接口**：CareerMate `POST /api/user/account/cancel`、`POST /api/user/account/cancel/revoke`；网关 `POST/DELETE/GET /auth/apps/{app}/deletion-request`。

## 优先级

| 优先级 | 含义 |
|---|---|
| P0 | 核心路径 / 合规 / 数据安全，阻塞发布 |
| P1 | 重要功能 / 跨服务 / 边界 |
| P2 | 极限 / 兜底 / 并发 |

---

## 一、TC-CANCEL 注销申请（20）

| ID | 标题 | 前置 | 步骤 | 预期 | 优先级 |
|---|---|---|---|---|---|
| CANCEL-01 | 正常注销 | 已登录 ACTIVE 用户，有 careermate membership | 输"注销"+短信验证+确认 | code=0；网关 membership PENDING_DELETION；本地 status=CANCELLING；deletion_scheduled_at≈30天后 | P0 |
| CANCEL-02 | 冷静期取网关口径 | 同上 | 注销后查 /me | deletion_scheduled_at ≈ now+30天（非本地 7 天旧值） | P0 |
| CANCEL-03 | 确认字为空 | 已登录 | confirmText="" | code=400「请输入「注销」确认注销操作」 | P0 |
| CANCEL-04 | 确认字错误(删除) | 已登录 | confirmText="删除" | code=400 同上友好提示 | P0 |
| CANCEL-05 | 确认字含空格「注 销」 | 已登录 | confirmText="注 销" | code=400（严格匹配"注销"两字） | P1 |
| CANCEL-06 | 确认字繁体「注銷」 | 已登录 | confirmText="注銷" | code=400 | P1 |
| CANCEL-07 | 确认字前后空格「 注销 」 | 已登录 | confirmText=" 注销 " | 按实现：若未 trim 则 400；确认与设计一致 | P2 |
| CANCEL-08 | 短信验证码错误 | 已登录 | 正确确认字 + 错误 verifyCode | code=400「验证码不正确/已失效」，注销未触发，status 仍 ACTIVE | P0 |
| CANCEL-09 | 短信验证码过期 | 已登录，challenge>5min | 过期码提交 | code=400 友好，未注销 | P1 |
| CANCEL-10 | challengeId 缺失 | 已登录 | 不带 challengeId | code=400「请先获取验证码」 | P1 |
| CANCEL-11 | challengeId 伪造/串号 | 已登录 | 伪造 challengeId | code=400「验证码校验失败/已失效」，未注销 | P2 |
| CANCEL-12 | 未登录调注销 | 无 token | POST /user/account/cancel | code=401「未认证」 | P0 |
| CANCEL-13 | 过期 token 调注销 | AT 已过期 | 用旧 token | code=401（或静默续期后按登录态处理） | P1 |
| CANCEL-14 | 幂等：CANCELLING 再注销 | 已 CANCELLING | 再次注销 | 网关幂等不重置 30 天倒计时；本地 status 仍 CANCELLING；deletion_scheduled_at 不变 | P1 |
| CANCEL-15 | 无 careermate membership 老用户 | 老号无 membership | 注销 | 登录已 ensureMembership 补建；注销成功（回归修复点，防 404「未开通该应用」） | P0 |
| CANCEL-16 | 网关不可达 | mock 网关 5xx/超时 | 注销 | code=5xx 友好「认证服务不可用」；本地事务回滚不置 CANCELLING（不产生本地/网关不一致） | P1 |
| CANCEL-17 | 网关返回体无 deletionScheduledAt | mock 网关返回空 | 注销 | 本地回退 now+30天，不报错 | P2 |
| CANCEL-18 | BANNED 账号注销 | status=BANNED | 注销 | 登录本就被拦；若持 token 调注销 → 按状态拒绝，不进注销流程 | P2 |
| CANCEL-19 | 注销后审计日志 | 已登录 | 注销成功 | 网关记 app.deletion.requested（app=careermate）；本地日志含 app-level | P2 |
| CANCEL-20 | 注销请求体多余字段 | 已登录 | body 带额外字段 | 忽略额外字段，正常处理 | P2 |

## 二、TC-REVOKE 撤销注销（14）

| ID | 标题 | 前置 | 步骤 | 预期 | 优先级 |
|---|---|---|---|---|---|
| REVOKE-01 | 正常撤销 | status=CANCELLING | POST /user/account/cancel/revoke | code=0；网关 membership ACTIVE；本地 status=ACTIVE | P0 |
| REVOKE-02 | 撤销清空时间戳 | CANCELLING | 撤销后查 /me | deletion_scheduled_at=null、pending_deletion_at=null（防 MyBatis null-skip 残留） | P0 |
| REVOKE-03 | 撤销后正常使用 | 已撤销 | 访问业务接口 | 全部恢复正常，不再进中间页 | P0 |
| REVOKE-04 | 非注销中撤销 | status=ACTIVE | 撤销 | code=400「当前账号不在注销流程中」；本地守卫短路，不误调网关 | P0 |
| REVOKE-05 | 未登录撤销 | 无 token | 撤销 | code=401 | P0 |
| REVOKE-06 | 幂等：连续两次撤销 | CANCELLING | 快速撤销两次 | 第一次 code=0；第二次 400「不在注销流程中」 | P1 |
| REVOKE-07 | 网关已 DELETED 后撤销 | membership DELETED（到期已清） | 撤销 | 网关 cancelDeletion 命中 0 行 → 400「不在注销流程中」；本地不误置 ACTIVE | P0 |
| REVOKE-08 | 网关不可达时撤销 | mock 网关 5xx | 撤销 | code=5xx 友好；本地不误置 ACTIVE（事务回滚） | P1 |
| REVOKE-09 | 撤销后再注销 | 撤销回 ACTIVE | 再走注销 | 可再次注销，新 30 天倒计时 | P1 |
| REVOKE-10 | 撤销无需短信 | CANCELLING | 撤销仅凭 token | 不要求短信二次验证（与设计一致，一键恢复） | P1 |
| REVOKE-11 | 中间页"恢复账号"按钮 | CANCELLING 登录落中间页 | 点"恢复账号" | 调撤销成功 → 跳 /chat 主界面 | P0 |
| REVOKE-12 | 中间页"确认离开"按钮 | CANCELLING 中间页 | 点"确认离开" | 登出跳登录页，不撤销（仍 CANCELLING） | P1 |
| REVOKE-13 | 撤销并发（多设备同时） | CANCELLING，多端 | 并发撤销 | 只生效一次，状态一致 ACTIVE，无脏写 | P2 |
| REVOKE-14 | 撤销审计 | CANCELLING | 撤销 | 网关记 app.deletion.cancelled | P2 |

## 三、TC-SESSION 注销即吊销会话（8）

| ID | 标题 | 前置 | 步骤 | 预期 | 优先级 |
|---|---|---|---|---|---|
| SESSION-01 | 注销即当前设备下线 | 已登录 | 注销后用同一 token 调 /me | code=401（会话被吊销） | P0 |
| SESSION-02 | 注销即全设备下线 | 多设备登录 | 设备A注销后设备B调 API | 设备B下次请求 401 | P0 |
| SESSION-03 | 用户级吊销键写入 | 已登录 | 注销 | revoked:user:<authUserId> 写入，注销时刻前签发 token 全失效 | P1 |
| SESSION-04 | 本地会话行清除 | 有会话记录 | 注销 | user_login_sessions 该用户行删除，列表空 | P1 |
| SESSION-05 | 注销后重新登录可用 | 已注销(CANCELLING) | 重新短信登录 | 登录成功（放行 CANCELLING），新会话正常 | P0 |
| SESSION-06 | 撤销不额外吊销 | 撤销 | 撤销后当前会话 | 撤销用的是新登录 token，撤销后仍可用（不被误吊销） | P1 |
| SESSION-07 | Redis 不可用时吊销降级 | Redis down | 注销 | fail-open 记日志不阻断注销；提示仍友好 | P2 |
| SESSION-08 | 静默续期与吊销交互 | 注销后 AT 过期 | 前端静默续期 | refresh 应失败（会话已吊销）→ 登出，不无限续期 | P2 |

## 四、TC-LOGIN 登录准入（CANCELLING / DELETED）（12）

| ID | 标题 | 前置 | 步骤 | 预期 | 优先级 |
|---|---|---|---|---|---|
| LOGIN-01 | CANCELLING 短信登录放行 | membership PENDING_DELETION | 短信登录 careermate | code=0（放行），落中间页 | P0 |
| LOGIN-02 | CANCELLING 密码登录放行 | 同上且有密码 | 密码登录 | code=0，落中间页 | P1 |
| LOGIN-03 | DELETED 短信登录拒绝 | membership DELETED | 短信登录 careermate | 网关 403 CAREERMATE_ACCESS_REVOKED「该账号已注销，如需使用请重新注册」 | P0 |
| LOGIN-04 | DELETED 密码登录拒绝 | 同上 | 密码登录 | 同上 403 友好 | P1 |
| LOGIN-05 | 登录补建 membership | 老号无 careermate membership | 短信登录 | ensureMembership 建 ACTIVE，登录成功 | P0 |
| LOGIN-06 | 补建幂等不覆盖 PENDING | membership PENDING_DELETION | 再登录 | ensureMembership ON CONFLICT DO NOTHING，不把 PENDING 改回 ACTIVE | P0 |
| LOGIN-07 | 补建幂等不覆盖 DELETED | membership DELETED | 登录 | 仍拒绝（不因 ensure 复活） | P0 |
| LOGIN-08 | CANCELLING /me 字段 | CANCELLING 登录 | GET /me | status=CANCELLING + deletionScheduledAt 非空 | P0 |
| LOGIN-09 | DELETED 用户 /me | 若持旧 token | /me | 本地 status=DELETED → 过滤器 401「用户已被禁用」 | P1 |
| LOGIN-10 | CANCELLING 登录记住我 | CANCELLING | 勾记住我登录 | 正常签发（冷静期不影响记住我），落中间页 | P2 |
| LOGIN-11 | 非 careermate aud 不受影响 | ragforge-admin-api | RAG 登录 | enforceCareermateAccess 不介入 RAG 登录 | P1 |
| LOGIN-12 | 登录失败计数与注销无关 | CANCELLING | 连错密码 | 失败锁/验证码逻辑独立，不被注销状态干扰 | P2 |

## 五、TC-INTER 注销中中间页 + 路由守卫（12）

| ID | 标题 | 前置 | 步骤 | 预期 | 优先级 |
|---|---|---|---|---|---|
| INTER-01 | CANCELLING 落中间页 | status=CANCELLING 登录 | 访问 /chat | 重定向到 /account/cancelling，不进主界面 | P0 |
| INTER-02 | 中间页展示到期日/剩余天数 | CANCELLING | 进中间页 | 显示"X 天后（YYYY-MM-DD）"，X 由 deletionScheduledAt 动态算 | P1 |
| INTER-03 | 剩余天数边界(今天到期) | deletion=今天 | 进中间页 | 显示"0 天后"，不为负 | P2 |
| INTER-04 | deletionScheduledAt 缺失兜底 | 字段 null | 进中间页 | 兜底文案"30 天后"，不报错/不 NaN | P2 |
| INTER-05 | 中间页"恢复账号" | CANCELLING | 点恢复 | 撤销成功 → /chat | P0 |
| INTER-06 | 中间页"确认离开" | CANCELLING | 点离开 | 登出跳登录 | P1 |
| INTER-07 | ACTIVE 访问中间页重定向 | status=ACTIVE | 直接访问 /account/cancelling | 重定向 /chat（避免正常用户误入） | P1 |
| INTER-08 | 中间页不影响其他应用提示 | CANCELLING | 进中间页 | 文案含"不影响你的其他应用" | P1 |
| INTER-09 | CANCELLING 深链任意页 | CANCELLING | 直接访问 /mine/resume | 守卫重定向到中间页 | P1 |
| INTER-10 | 未登录访问中间页 | 无 token | 访问 /account/cancelling | 守卫先拦未认证 → /login | P1 |
| INTER-11 | 恢复失败提示 | 恢复接口报错 | 点恢复失败 | 页内友好错误提示，不白屏 | P2 |
| INTER-12 | 恢复中防重复点击 | CANCELLING | 快速多次点恢复 | 按钮 disabled，只发一次撤销 | P2 |

## 六、TC-PURGE 到期清理 + 事件驱动本地清理（16）

| ID | 标题 | 前置 | 步骤 | 预期 | 优先级 |
|---|---|---|---|---|---|
| PURGE-01 | 到期扫描命中 | membership PENDING_DELETION 且到期 | 网关 cron 跑 | 扫出该 membership，置 DELETED，发 user.app_removed | P0 |
| PURGE-02 | 未到期不清理 | PENDING 未到期 | cron 跑 | 不处理该 membership | P0 |
| PURGE-03 | 行级锁多副本防重 | 2 副本同时跑 | 并发 cron | 仅 1 副本 UPDATE 命中 1 行、发 1 次事件；另一副本命中 0 行跳过 | P0 |
| PURGE-04 | 单账号失败不阻断整体 | 批中 1 个抛异常 | cron | 该账号失败被吞、记日志，其余照常清理 | P1 |
| PURGE-05 | 已 DELETED 不重复清 | 已 DELETED | cron 再跑 | 查询条件过滤，不重复发事件 | P1 |
| PURGE-06 | user.app_removed 收事件清本地 | 网关发 app=careermate | CareerMate webhook | LocalAccountPurger 匿名化 user + 删内容，status=DELETED | P0 |
| PURGE-07 | 匿名化 PII 不可逆 | 清理后 | 查本地 user | phone/email=null、username=deleted_<id>、displayName="已注销用户"、password_hash=null | P0 |
| PURGE-08 | 删除个人内容 | 清理后 | 查简历/对话/投递/面试/画像 | 该 userId 相关行全部删除 | P0 |
| PURGE-09 | 事件幂等去重 | 同 event_id 重投 | 重复投递 | event_id Redis 去重，不重复清理 | P0 |
| PURGE-10 | HMAC 签名校验 | 伪造签名 | 伪造事件调 webhook | 拒绝，不清理 | P0 |
| PURGE-11 | app!=careermate 忽略 | user.app_removed app=ragforge | CareerMate 收到 | isCareermateApp=false，不清 CareerMate 数据 | P0 |
| PURGE-12 | 本地用户不存在 | authUserId 无本地用户 | 收事件 | purge 返回 false，无异常 | P1 |
| PURGE-13 | 本地已 DELETED 幂等 | 本地 status=DELETED | 再收事件 | 跳过，不重复删 | P1 |
| PURGE-14 | 清理事务原子性 | 删内容中途失败 | mock 删失败 | 事务回滚，不出现"删一半" | P1 |
| PURGE-15 | 清理失败不炸 webhook | purge 抛异常 | 收事件 | AuthEventService 捕获记日志，仍返回 accepted（可重投递） | P1 |
| PURGE-16 | Redis 不可用时事件处理 | Redis down | 收事件 | 返回 unavailable，不误清理/不误 ack | P2 |

## 七、TC-RAG 跨服务隔离（CareerMate 注销对 RAG 的影响评估）（14）

| ID | 标题 | 前置 | 步骤 | 预期 | 优先级 |
|---|---|---|---|---|---|
| RAG-01 | 同手机号双 App | 一个手机号同时有 careermate + ragforge membership | — | 网关一条 auth_users + 两条 membership | P0 |
| RAG-02 | CM 注销不改 RAG membership | 双 App 用户 | CM 注销 | 只 careermate membership → PENDING_DELETION；ragforge membership 仍 ACTIVE | P0 |
| RAG-03 | CM 注销后 RAG 仍可登录 | 双 App，CM 已 CANCELLING | 登录 RAGForge(ragforge-admin-api) | 登录成功，不受 careermate 注销影响 | P0 |
| RAG-04 | CM 到期清理不动 RAG 数据 | CM membership 到期 DELETED | 网关发 user.app_removed(app=careermate) | 仅 CareerMate 清本地；RAGForge 不响应该 app 事件、数据不动 | P0 |
| RAG-05 | 共享身份不被删 | CM 注销到期 | 查 auth_users | auth_users 身份仍在（因 RAG 在用），仅 careermate membership DELETED | P0 |
| RAG-06 | CM 注销不吊销 RAG 会话 | 双 App 均登录 | CM 注销（吊销 CM 会话）| RAG 会话是否受影响？—— 期望不受影响（吊销按 CM 会话/用户级键，需确认 RAG token 不被 revoked:user 键误伤） | P0 |
| RAG-07 | 用户级吊销键范围 | 双 App | CM 注销 revokeUserAfter | 该键若被 RAG 网关校验，会误伤 RAG token —— **重点排查**：确认 CM 的 revoked:user 键作用域仅 CM（或 RAG 不校验此键） | P0 |
| RAG-08 | RAG 注销不影响 CM | 双 App | RAG 侧走账号级/应用级注销 | 反向验证：不误删 CM membership/数据 | P1 |
| RAG-09 | 仅 CM 单 App 用户注销 | 只有 careermate membership | CM 注销到期 | careermate DELETED；可选：无其他 membership 时是否清共享身份（按设计本期不做，确认身份保留不影响他人）| P1 |
| RAG-10 | RAG 用 CM 的 aud token | — | 用 careermate-api token 调 RAG | aud 不匹配被拒（既有隔离）| P1 |
| RAG-11 | CM DELETED 后同号 RAG 注册 | CM DELETED、RAG 未开通 | 该号在 RAG 注册 | 复用共享身份 + 新建 ragforge membership，不受 CM DELETED 影响 | P2 |
| RAG-12 | user.app_removed 订阅隔离 | — | 网关发 app=careermate | 仅 careermate 订阅方收（据 event_subscriptions），RAG 不收该 app 的清理指令 | P1 |
| RAG-13 | 双 App 同时注销 | 双 App | CM 与 RAG 分别注销 | 两条 membership 各自 PENDING_DELETION，互不干扰，到期各清各的 | P2 |
| RAG-14 | RAG 侧登录准入不被 CM 影响 | CM membership DELETED | RAG 登录 | enforceCareermateAccess 只针对 careermate-api，不拦 RAG 登录 | P1 |

## 八、TC-MEMBER membership 状态机 + 自愈（8）

| ID | 标题 | 前置 | 步骤 | 预期 | 优先级 |
|---|---|---|---|---|---|
| MEMBER-01 | ACTIVE→PENDING_DELETION | ACTIVE | 注销 | markPendingDeletion 命中，30 天 | P0 |
| MEMBER-02 | PENDING_DELETION→ACTIVE | PENDING | 撤销 | cancelDeletion 命中 1 行 | P0 |
| MEMBER-03 | PENDING_DELETION→DELETED | PENDING 到期 | cron | markDeletedIfDue 命中 | P0 |
| MEMBER-04 | DELETED 终态不可逆 | DELETED | 注销/撤销 | markPendingDeletion/cancel 命中 0 行；不回退 | P0 |
| MEMBER-05 | 无 membership 注销 | 无 membership | 直接调网关注销 | markPendingDeletion 返回 null → 404「未开通该应用」 | P1 |
| MEMBER-06 | 登录自愈补建 | 无 membership | 登录 careermate | ensureMembership 建 ACTIVE | P0 |
| MEMBER-07 | 不支持的 app | — | POST /auth/apps/foo/deletion-request | 400 APP_NOT_SUPPORTED「不支持的应用」 | P1 |
| MEMBER-08 | app 大小写/空格 | — | app=" Careermate " | 归一化为 careermate 正常处理 | P2 |

## 九、TC-MSG 友好提示汇总（12）

| ID | 场景 | 期望文案（友好） | 优先级 |
|---|---|---|---|
| MSG-01 | 确认字错误/空 | 请输入「注销」确认注销操作 | P0 |
| MSG-02 | 短信验证码错误 | 验证码不正确，请重新输入。如已过期，请重新获取 | P0 |
| MSG-03 | 非注销中撤销 | 当前账号不在注销流程中 | P0 |
| MSG-04 | 未开通该应用注销 | 当前账号未开通该应用，无需注销 | P1 |
| MSG-05 | 不支持的应用 | 不支持的应用 | P1 |
| MSG-06 | DELETED 登录 | 该账号已注销，如需使用请重新注册 | P0 |
| MSG-07 | 未登录调注销/撤销 | 未认证（或"登录状态已失效，请重新登录"）| P1 |
| MSG-08 | 网关不可达 | 认证服务不可用（不暴露堆栈/网关字样）| P1 |
| MSG-09 | 中间页主文案 | 你的账号已申请注销，将于 X 天后永久删除 | P1 |
| MSG-10 | 中间页安抚 | 不影响你的其他应用 | P1 |
| MSG-11 | 恢复失败 | 恢复失败，请稍后再试 | P2 |
| MSG-12 | 文案无"演示/简历demo"等口径问题 | 企业级措辞，无泄露 | P2 |

## 十、TC-CONC 并发 / 幂等 / 极限（12）

| ID | 标题 | 步骤 | 预期 | 优先级 |
|---|---|---|---|---|
| CONC-01 | 同账号并发注销 | 2 请求同时注销 | 只生效一次（网关幂等 COALESCE），倒计时一致 | P1 |
| CONC-02 | 注销与撤销竞态 | 注销与撤销几乎同时 | 最终状态一致、无中间态泄露 | P1 |
| CONC-03 | 撤销与到期清理竞态 | 到期瞬间撤销 | 行级锁保证：要么撤销成功(ACTIVE)、要么已 DELETED，不出现半态 | P0 |
| CONC-04 | 多副本 cron 并发 | N 副本同时跑清理 | 每 membership 仅 1 副本处理、1 次事件 | P0 |
| CONC-05 | 事件重复投递 | 同 event_id 投递 M 次 | 幂等仅处理 1 次 | P0 |
| CONC-06 | 大批量到期清理 | 万级到期 membership | 分批处理不 OOM、不长事务卡库 | P2 |
| CONC-07 | 注销风暴发码限流 | 短时间大量注销发码 | 网关发码限流生效，友好提示 | P2 |
| CONC-08 | 超长/畸形请求体 | body 超大/畸形 JSON | 400 友好，不 500/不崩 | P2 |
| CONC-09 | 到期时间时区 | 服务器/DB 时区不一致 | deletion_scheduled_at 用 UTC，剩余天数计算正确 | P1 |
| CONC-10 | 注销后 30 天边界 | 到期日当天/前一秒/后一秒 | 到期判定 <=now 精确，不早清/不漏清 | P1 |
| CONC-11 | 事件乱序（app_removed 早于 requested）| 极端乱序 | 幂等/状态守卫保证最终一致 | P2 |
| CONC-12 | webhook 重试风暴 | 网关重试多次 | 幂等去重，本地不重复删/不放大 | P1 |

## 十一、TC-GW 网关接口直测（8）

| ID | 标题 | 步骤 | 预期 | 优先级 |
|---|---|---|---|---|
| GW-01 | POST deletion-request 无 Bearer | 不带 token | 401 ACCESS_TOKEN_INVALID | P0 |
| GW-02 | POST 合法 | Bearer + app=careermate | 200，返回 deletionScheduledAt | P0 |
| GW-03 | POST 不支持 app | app=foo | 400 APP_NOT_SUPPORTED | P1 |
| GW-04 | POST 无 membership | 用户无该 app membership | 404 APP_MEMBERSHIP_NOT_FOUND | P1 |
| GW-05 | DELETE 合法撤销 | PENDING_DELETION | 200 status=ACTIVE | P0 |
| GW-06 | DELETE 非 PENDING | ACTIVE/DELETED | 400 NOT_PENDING_DELETION | P0 |
| GW-07 | GET deletion-status | 各状态 | 返回 status + pendingDeletion 布尔 | P1 |
| GW-08 | token user_id 缺失 | claim 无 user_id | 401 ACCESS_TOKEN_INVALID | P1 |

---

## 附：重点深挖 bug 方向（评审提示）

1. **RAG-06 / RAG-07 用户级吊销键作用域**：CareerMate 注销时 `revoked:user:<authUserId>` 是否会被 RAGForge 网关侧校验从而误伤 RAG 的 token（同一 authUserId）。若共用吊销键机制，CM 注销可能连带把 RAG 会话踢下线——**必须验证 RAG token 不受此键影响**（应用级注销不应下线 RAG）。
2. **REVOKE-02 时间戳残留**：MyBatis-Plus null-skip 已修，回归验证撤销后字段真清空。
3. **CANCEL-16 本地/网关一致性**：网关失败时本地必须回滚，防"本地 CANCELLING、网关未标记"或反之。
4. **CONC-03/04 行级锁**：到期清理的多副本安全依赖 SQL 守卫，重点并发验证。
5. **PURGE-08 内容删除完整性**：确认设计列出的所有个人数据表都被清（防漏表导致 PII 残留、PIPL 违规）。
6. **CANCEL-15 自愈**：存量短信用户注销前须能补建 membership，防大面积注销失败。

---

*测试用例 V1.0 — 2026-07-12 — 待评审后执行；共 126 条*
