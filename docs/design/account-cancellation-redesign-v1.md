# CareerMate 账号注销流程重设计 · 设计稿

**拟制日期**：2026-07-12
**版本**：V3.0
**状态**：待评审 / 待实现
**作者**：架构评审
**修订记录**：

| 版本 | 日期 | 修订内容 |
|---|---|---|
| V1.0 | 2026-07-12 | CareerMate 自建 15 天冷静期 + 定时清理 Job（Redis 锁）|
| V2.0 | 2026-07-12 | 改为委托网关账号级注销 + 订阅 `user.deleted` 事件；发现会**连带注销 RAGForge**（共享身份）|
| **V3.0** | **2026-07-12** | **最终版**：改为**应用级注销**——只退 CareerMate、不影响 RAGForge。基于网关已有的 `user_app_membership`（每 App 一行）扩展：网关新增**应用级注销**（membership 级冷静期 + 到期清理 + 应用级事件），CareerMate 委托该接口并订阅应用级事件清本地。冷静期 **30 天** |

---

## 一、背景与结论

- 现状：CareerMate 注销只改本地表、不通知网关、无删除任务 —— 空头承诺 + PIPL 违规。
- 网关是**共享身份中心**：一个手机号一条 `auth_users`，CareerMate/RAGForge 共用；但**准入按 App 分**（`user_app_membership` 每 App 一行）。
- **账号级注销会连 RAGForge 一起删**（不符合"两个独立产品共用登录"的直觉）。
- **最终决策：应用级注销** —— CareerMate 的"注销"只退 CareerMate（删本地数据 + 停用 careermate membership），**共享身份与 RAGForge 准入不受影响**，与"注册按 App 分渠道"对称。

## 二、网关现状（依据）

| 对象 | 现状 |
|---|---|
| `user_app_membership` | `(id, user_id, app, role, status, created_at)`，UNIQUE(user_id, app)；status 默认 ACTIVE；**无 deletion_scheduled_at** |
| `MembershipRepository` | `ensureMembership / find(userId,app) / listByUser`；**无移除/停用方法** |
| 账号级注销 | `AccountDeletionController`（identity 级 PENDING_DELETION，30 天）+ `AccountDeletionCleanupJob`（匿名化 + 发 `user.deleted`）|
| 登录准入 | 按 `targetAud`（careermate-api / ragforge-admin-api）+ `enforceRagForgeAccess`；**尚未按 careermate membership.status 拦截** |
| 事件 | `eventPublisher.publish("user.deleted", {user_id})` |

## 三、最终方案（V3·应用级）

### 3.1 流程
```
[CareerMate 账号设置·注销]  短信验证 + 输入"注销"确认
   ▼
[CareerMate 后端] 调网关"应用级注销"(app=careermate) → 网关把该 membership 置 PENDING_DELETION(30天)
   ├─ 本地 status=CANCELLING 镜像 + 记 deletion_scheduled_at(网关返回)
   └─ 吊销该用户 CareerMate 会话
   ▼
[CareerMate 前端] 立即登出 → 跳登录页（"注销申请已提交，30 天内重新登录可恢复"）
   │  ……30 天冷静期……（此期间 RAGForge 照常可登）
   ▼
[再次登录 CareerMate] 准入被拦(careermate membership=PENDING_DELETION) → 前端落「注销中」中间页
   ├─ 恢复账号 → 调网关撤销(app=careermate) → membership=ACTIVE → 回主界面
   └─ 确认离开 → 仅登出
   ▼
[到期] 网关应用级清理 Job：membership=DELETED → 发 user.app_removed{user_id, app:careermate}
   ▼
[CareerMate 事件处理器] 收到 user.app_removed(app=careermate) → 删/匿名化本地：user 主记录 PII + 简历/对话/投递/面试等 + status=DELETED
```

### 3.2 关键决策
| # | 项 | 结论 |
|---|---|---|
| 1 | 注销粒度 | **应用级**（只 careermate），不影响 RAGForge 与共享身份 |
| 2 | 冷静期 | **30 天**（membership 级，对齐账号级口径）|
| 3 | 确认注销后 | 立即登出 + 吊销 CareerMate 会话 |
| 4 | 再登录 | 准入被拦 → 前端「注销中」中间页 |
| 5 | 恢复/离开 | 恢复=委托网关撤销回原状态；离开=仅登出 |
| 6 | 清理触发 | 网关应用级 Job 到期 → 发 `user.app_removed` 事件 → CareerMate 订阅清本地（**CareerMate 无 cron/无 Redis 锁**）|
| 7 | 清理方式 | 匿名化 user 主记录 PII + 删个人内容，status=DELETED |
| 8 | 最后一个 App 边界 | 若 careermate 是该用户唯一 membership，可选顺带触发身份级清理（本期先不做，留待后续）|

---

## 四、auth-gateway 改动清单（新增应用级注销）

1. **迁移**：`user_app_membership` 增列 `deletion_scheduled_at TIMESTAMPTZ NULL`；status 增加取值 `PENDING_DELETION` / `DELETED`。
2. **MembershipRepository**：新增
   - `markPendingDeletion(userId, app)` → status=PENDING_DELETION, deletion_scheduled_at=now()+30d（幂等，不重置倒计时）
   - `cancelDeletion(userId, app)` → status=ACTIVE, deletion_scheduled_at=NULL
   - `findExpired(limit)` / `markDeleted(userId, app)`（带 `WHERE status='PENDING_DELETION'` 行级锁守卫，多副本安全）
3. **应用级注销接口**（新增 controller，Bearer + `{phone, smsCode, app}`）：
   - `POST /auth/apps/{app}/deletion-request` → markPendingDeletion，返回 deletionScheduledAt
   - `DELETE /auth/apps/{app}/deletion-request` → cancelDeletion
   - `GET /auth/apps/{app}/deletion-request` → 查该 app 的注销状态（供中间页展示）
4. **登录准入**：`loginPassword/loginMobile` 在 targetAud=careermate-api 时，校验该用户 `careermate` membership.status；为 PENDING_DELETION → 返回 423「账号注销中，登录页可撤销」；DELETED → 拒绝。RAGForge 不受影响。
5. **应用级清理 Job**（新增或扩展）：每日扫 `membership PENDING_DELETION 且到期` → markDeleted → `eventPublisher.publish("user.app_removed", {user_id, app})`；行级锁守卫，无需 ShedLock。
6. **事件订阅**：`event_subscriptions` 增加 `user.app_removed` → CareerMate 的 webhook（HMAC）。

## 五、CareerMate 改动清单

**后端**
- `AuthGatewayClient`：`requestAppDeletion(bearer, phone, smsCode, "careermate")` / `cancelAppDeletion(bearer, "careermate")`
- `UserSettingsService.requestCancellation / revokeCancellation`：改为委托网关应用级接口 + 本地 status 镜像 + 吊销会话
- `AuthEventService`：新增 `user.app_removed`（app=careermate 时）处理 → 匿名化本地 user + 删个人内容 + status=DELETED + 审计；幂等去重
- 事件白名单/HMAC 复用现有

**前端**
- 账号设置：注销成功 → 登出 + 跳登录 + 提示
- 新增「注销中」中间页 + 路由守卫；移除旧 Banner

## 六、为何不用 CareerMate 自建 cron/Redis 锁
- 清理触发改为**订阅网关应用级事件**；网关清理 Job 用**行级锁**多副本安全。CareerMate 侧零 cron、零分布式锁，与既有"网关权威 + 事件下游"一致。

## 七、分阶段目标（拟）

| 阶段 | 目标 | 目标日期 |
|---|---|---|
| P1 | 网关：membership 迁移 + repo + 应用级注销接口 + 登录准入 + 清理 Job + `user.app_removed` 事件 | 2026-07-15 |
| P2 | CareerMate 后端：委托应用级注销 + 吊销会话 + `user.app_removed` 事件清本地 | 2026-07-16 |
| P3 | CareerMate 前端：注销即登出 + 「注销中」中间页 + 移除旧 Banner | 2026-07-16 |
| P4 | 联调 + 生产验证（造 careermate membership 到期 → 验证只 CareerMate 清、RAGForge 不受影响）| 2026-07-17 |

---

## 八、合规与安全
- **PIPL**：CareerMate 删本地个人数据满足自身义务；共享身份因 RAGForge 仍在用而保留（用户未要求删 RAG）。
- **隔离**：应用级注销只动 careermate membership，RAGForge 完全不受影响。
- **会话安全**：注销即吊销 CareerMate 会话。
- **一致性**：网关 membership 为该 App 准入真相，CareerMate 事件驱动跟随。
- **多副本**：网关清理 Job 行级锁；CareerMate 事件幂等去重。

---

*设计稿 V3.0 — 2026-07-12 — 应用级注销（网关 membership 扩展 + 事件驱动），待评审后进入实现*
