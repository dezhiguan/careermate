package com.careermate.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.careermate.model.entity.AgentRunEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AgentRunMapper extends BaseMapper<AgentRunEntity> {

    /** B3：某用户最近的运行（含续跑/分叉），按开始时间倒序。 */
    @Select("SELECT * FROM agent_run WHERE user_id = #{userId} ORDER BY started_at DESC LIMIT #{limit}")
    List<AgentRunEntity> listRecentByUser(@Param("userId") Long userId, @Param("limit") int limit);
}
