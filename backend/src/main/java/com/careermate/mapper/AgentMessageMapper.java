package com.careermate.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.careermate.model.entity.AgentMessageEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.OffsetDateTime;
import java.util.List;

@Mapper
public interface AgentMessageMapper extends BaseMapper<AgentMessageEntity> {

    /** A4：某时间窗内有对话的用户（蒸馏调度用）。 */
    @Select("SELECT DISTINCT user_id FROM agent_messages WHERE created_at >= #{since} AND created_at < #{until}")
    List<Long> findActiveUserIds(@Param("since") OffsetDateTime since, @Param("until") OffsetDateTime until);

    /** A4：某用户在时间窗内的对话文本（role: content），上限 40 条。 */
    @Select("SELECT role || ': ' || content FROM agent_messages "
            + "WHERE user_id = #{userId} AND created_at >= #{since} AND created_at < #{until} "
            + "ORDER BY created_at LIMIT 40")
    List<String> findConversationTexts(@Param("userId") Long userId,
                                       @Param("since") OffsetDateTime since,
                                       @Param("until") OffsetDateTime until);
}
