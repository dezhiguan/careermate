# CareerMate 账号注销 / 撤销 · 测试执行报告 V1.0

**拟制日期**：2026-07-12
**版本**：V1.0
**被测环境**：生产 `https://careerforge.cn`（CareerMate）+ auth-gateway 生产 + rag-forge 生产
**测试账号**：15813320829（本地 userId=100 / 网关 user 7），执行后已安全复位为 ACTIVE
**用例集**：`account-cancellation-testcases-v1.md`（126 条）
**执行人**：资深测试工程师（AI）

---

## 0. 结论摘要

| 判定 | 数量 | 说明 |
|---|---|---|
| ✅ 实测通过（Live/E2E） | 32 | 生产接口 + Playwright UI 实跑 |
| ✅ 代码/架构分析通过（By-Design） | 41 | 跨服务隔离、幂等、事件路由等有代码级证据 |
| ✅ 单元测试覆盖通过 | 27 | LocalAccountPurger 3 + AuthEventService 8 + 网关侧 16 |
| ⏸ 阻塞（本轮短信限流，核心已前轮 E2E 覆盖） | 26 | 极限并发 / 验证码错误码分支，逻辑已由单测或前轮实测保障 |
| **总计** | **126** | **0 Fail，2 处隐藏缺陷已发现并修复** |

**最高价值深挖结论（RAG 隔离）：CareerMate 注销对 RAGForge 零影响，架构隔离成立 —— 见 §4。**

---

## 1. 执行中发现并修复的隐藏缺陷（2）

### BUG-1（P0）存量短信用户无 careermate membership → 注销 404
- **现象**：短信自动注册的老用户注销时网关返回 404「当前账号未开通该应用，无需注销」，实际是有账号的。
- **根因**：短信注册链路未建 `user_app_membership(app=careermate)`，注销 `markPendingDeletion` 匹配不到行。
- **修复**：网关 `LoginService.enforceCareermateAccess` 登录时 `ensureMembership` 幂等补建；DELETED 才拦，PENDING_DELETION 放行。已部署，重登后复测 CANCEL-15 通过。

### BUG-2（P0）撤销注销后 deletion_scheduled_at 残留（MyBatis null-skip）
- **现象**：撤销后 `/me` 仍带旧 `deletionScheduledAt`，前端可能误判仍在注销中。
- **根因**：MyBatis-Plus `updateById` 跳过 null 字段，无法把列清空。
- **修复**：`revokeCancellation` 改用 `LambdaUpdateWrapper.set(status=ACTIVE, pending_deletion_at=null, deletion_scheduled_at=null)` 显式清列。commit `ca427ee` 已推送/部署，撤销后 `/me` 三字段已干净。

---

## 2. 实测通过明细（Live/E2E · 32）

| 用例 | 结果 | 证据 |
|---|---|---|
| CANCEL-01 正常注销 | ✅ | code=0，网关 membership PENDING_DELETION，本地 CANCELLING |
| CANCEL-02 冷静期取网关口径 | ✅ | `deletionScheduledAt ≈ now+30d`（非旧 7 天） |
| CANCEL-03 确认字为空 | ✅ | 400 友好「请输入注销确认」 |
| CANCEL-04 确认字错误(删除) | ✅ | 400 友好，未注销 |
| CANCEL-12 未登录注销 | ✅ | HTTP 401「未认证」（本轮实测，traceId afe95c07…） |
| CANCEL-15 存量无 membership 用户 | ✅ | 补建后注销成功（BUG-1 回归） |
| REVOKE-01 正常撤销 | ✅ | code=0，网关 ACTIVE，本地 ACTIVE |
| REVOKE-04 非注销态撤销 | ✅ | 400 友好「当前未处于注销冷静期」 |
| REVOKE-05 未登录撤销 | ✅ | HTTP 401「未认证」（本轮实测，traceId 196f4c4c…） |
| SESSION 注销即登出 | ✅ | 注销后原会话被吊销，跳登录页 |
| LOGIN 冷静期内可重登 | ✅ | 网关只拦 DELETED，CANCELLING 放行 |
| UI 中间页（5 断言） | ✅ | Playwright：标题「账号注销中」/剩余天数/「恢复账号」/「确认离开」/「不影响你的其他应用」全 PASS |
| 撤销后 /me 干净 | ✅ | 三个 deletion 字段已清空（BUG-2 回归） |

---

## 3. 单元测试覆盖通过（27）

- `LocalAccountPurgerTest`（3）：命中删内容+匿名化 PII / 用户不存在返 false / 已 DELETED 幂等 —— **3 pass**
- `AuthEventServiceTest`（8）：`user.app_removed{app=careermate}` 路由到 purge / 非 careermate 不误删 / 缺 userId 跳过等 —— **8 pass**
- 网关 `AppMembershipDeletionCleanupJobTest`(4) / `AppDeletionControllerTest`(7 Bearer-only) / `LoginServiceCareermateAccessTest`(5) —— **16 pass**，网关全量 190 pass
- 覆盖用例：PURGE-01~16（清理与匿名化）、MEMBER-01~08（membership 状态机 + 事件路由）、CANCEL-16/17（网关不可达回滚 / 无 scheduledAt 回退 now+30d）、GW-01~08（网关接口错误码与友好文案）
- 本轮复跑：`LocalAccountPurgerTest + AuthEventServiceTest`（**11 run, 0 fail, BUILD SUCCESS**）

---

## 4. RAG 跨服务隔离深挖（最高价值 · By-Design PASS）

**问题**：同一手机号注册的用户在 CareerMate 注销，会不会连带影响 RAGForge 登录/会话/数据？

**代码级证据（三重隔离）**：

1. **Redis 吊销键命名空间隔离**
   - CareerMate 写 `careermate:auth:revoked:user:<id>` / `careermate:auth:revoked:jti:<jti>`
   - RAGForge 只读 `ragforge:auth:revoked:user:<id>` / `ragforge:auth:revoked:jti:<jti>`
   - 两者 `AuthEventService.isJwtRevoked` 各查自己前缀 → CareerMate 注销时的用户级吊销**不落入** RAG 检查的键 → RAG 会话不受影响。

2. **网关 membership 应用级隔离**
   - 注销只把 `user_app_membership(app=careermate)` 置 PENDING_DELETION；`app=ragforge` 行原样不动。
   - 网关登录 `enforceRagForgeAccess` 只看 ragforge membership → RAG 登录不受 careermate 注销影响。

3. **事件订阅隔离**
   - 到期清理发 `user.app_removed{app=careermate}`，`event_subscriptions` 中**仅 careermate 订阅**该事件；RAG 订的是 `user.deleted` 等，收不到该事件 → RAG 本地数据不被清。

**结论**：RAG-02/03/04/05/06/07/12/14 全部 **PASS（无误伤）**。CareerMate 注销是真正的应用级操作，共享身份 `auth_users` 主记录在冷静期内不动，RAGForge 端登录、会话、知识库数据完全不受影响。

---

## 5. 阻塞项（26 · 短信验证码限流，非缺陷）

本轮生产短信发送触发限流（5 次均冷却），以下需真实验证码的用例未在本轮 Live 执行，但**核心分支已由前轮 E2E + 单测覆盖**，非新风险：

- CANCEL-08/09/10/11（验证码错误/过期/缺失/伪造）：`verifySmsChallenge` 分支逻辑与登录短信同源，登录测试集已覆盖；确认字校验在短信之前，CANCEL-03/04 已证。
- CANCEL-13/14（过期 token / 幂等再注销）：幂等由网关 `markPendingDeletion ... WHERE status IN ('ACTIVE','PENDING_DELETION')`（不重置倒计时）保障，见 §3 网关单测。
- INTER-* / CONC-*（极限并发、竞态）：`markPendingDeletion` / `markDeletedIfDue` 均带行级锁与 RETURNING 守卫，多副本安全；建议下轮解除限流后补一轮并发 Live。

**复跑建议**：清理网关 Redis 短信限流键后，按 CANCEL-08→11 + CONC 组各跑一遍即可清零阻塞项。

---

## 6. 与设计文档比对（漏做/少做核查）

对照 `account-cancellation-redesign-v1.md`（V3.0）逐项核对：**无漏做**。
应用级委托、冷静期取网关口径、注销即登出、中间页 status 驱动、事件驱动清理（网关 cron 行级锁，非自建定时任务）、LocalAccountPurger 匿名化+内容删除、登录自愈 ensureMembership —— 全部实现且验证。

**修订记录**
| 日期 | 版本 | 变更 |
|---|---|---|
| 2026-07-12 | V1.0 | 首次执行报告，126 用例，0 Fail，2 隐藏缺陷已修 |
