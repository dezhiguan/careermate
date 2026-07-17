package com.careermate.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.careermate.model.entity.ResumeVersionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ResumeVersionMapper extends BaseMapper<ResumeVersionEntity> {

    /** 导出成功后标记已导出。 */
    @Update("UPDATE resume_versions SET exported = true WHERE version_id = #{versionId} AND user_id = #{userId}")
    int markExported(@Param("userId") Long userId, @Param("versionId") String versionId);
}
