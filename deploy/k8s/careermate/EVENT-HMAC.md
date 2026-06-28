# CareerMate 事件 webhook 验签密钥

`backend-deployment.yaml` 通过 `envFrom: secretRef: careermate-event-hmac` 注入 `AUTH_EVENT_HMAC_SECRET`，用于验证网关投递的 `session.revoked` / `user.password.changed` webhook 签名（HMAC-SHA256）。**密钥不入 git。**

注意：该配置默认值为**空**，空则 CareerMate 拒收一切 webhook（401）。必须与网关订阅配置及 RAGForge 侧用**同一把强随机密钥**。

```bash
# S 为三处共用的强密钥（见 auth-gateway 仓库 deploy/k8s/auth-gateway/EVENT-HMAC.md）
kubectl -n careermate create secret generic careermate-event-hmac \
  --from-literal=AUTH_EVENT_HMAC_SECRET="$S"
```
