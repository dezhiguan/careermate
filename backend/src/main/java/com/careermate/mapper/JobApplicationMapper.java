package com.careermate.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.careermate.model.entity.JobApplicationEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface JobApplicationMapper extends BaseMapper<JobApplicationEntity> {

    /** 卡片改名：name 可为 null（清除自定义名，回退自动生成）。 */
    @Update("UPDATE job_applications SET display_name = #{name}, last_active_at = now() WHERE id = #{id}")
    int updateDisplayName(@Param("id") Long id, @Param("name") String name);
}
