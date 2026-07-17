package com.careermate.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.careermate.model.entity.JobApplicationEntity;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface JobApplicationMapper extends BaseMapper<JobApplicationEntity> {

    /** 卡片改名：name 可为 null（清除自定义名，回退自动生成）。 */
    @Update("UPDATE job_applications SET display_name = #{name}, last_active_at = now() WHERE id = #{id}")
    int updateDisplayName(@Param("id") Long id, @Param("name") String name);

    /** 某用户各简历版本被多少个机会引用（简历版本「用于 N 机会」复用计数）。 */
    @Select("SELECT resume_version_id AS versionId, COUNT(*) AS cnt FROM job_applications "
            + "WHERE user_id = #{userId} AND deleted_at IS NULL AND resume_version_id IS NOT NULL "
            + "GROUP BY resume_version_id")
    List<Map<String, Object>> countByResumeVersion(@Param("userId") Long userId);
}
