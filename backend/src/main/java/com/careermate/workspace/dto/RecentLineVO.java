package com.careermate.workspace.dto;

import java.time.OffsetDateTime;

/**
 * 「最近会话线」列表项：用户正在推进的一条 JD 对话线，供一键返回续聊。
 *
 * @param sessionId    会话线外部 id（WS-xxx），跳转 /chat/{sessionId}
 * @param title        展示标题（一般是「公司 · 岗位」）
 * @param jdId         绑定的 JD 文档 id（可空）
 * @param workspaceType 工作空间类型（JD_PREP 等）
 * @param lastActiveAt 最近活跃时间，用于排序与相对时间展示
 */
public record RecentLineVO(
        String sessionId,
        String title,
        String jdId,
        String workspaceType,
        OffsetDateTime lastActiveAt
) {
}
