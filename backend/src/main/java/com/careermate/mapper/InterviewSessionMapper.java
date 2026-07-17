package com.careermate.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.careermate.model.entity.InterviewSessionEntity;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface InterviewSessionMapper extends BaseMapper<InterviewSessionEntity> {

    /** 某用户各公司的面试练习（面经）数：会话经 job_match 关到公司名。用于看板卡「面经×N」。 */
    @Select("SELECT jm.company_name AS company, COUNT(*) AS cnt "
            + "FROM interview_sessions s JOIN job_matches jm ON s.job_match_id = jm.id "
            + "WHERE s.user_id = #{userId} AND jm.company_name IS NOT NULL "
            + "GROUP BY jm.company_name")
    List<Map<String, Object>> countByCompany(@Param("userId") Long userId);
}
