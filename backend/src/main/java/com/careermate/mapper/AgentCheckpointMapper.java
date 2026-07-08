package com.careermate.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.careermate.model.entity.AgentCheckpointEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AgentCheckpointMapper extends BaseMapper<AgentCheckpointEntity> {

    /** B3：某 run 最新的 checkpoint（step_index 最大），用于崩溃续跑与相邻去重。 */
    @Select("SELECT * FROM agent_checkpoint WHERE run_id = #{runId} ORDER BY step_index DESC, id DESC LIMIT 1")
    AgentCheckpointEntity findLatest(@Param("runId") String runId);
}
