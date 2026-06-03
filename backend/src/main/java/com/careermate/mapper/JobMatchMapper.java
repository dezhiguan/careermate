package com.careermate.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.careermate.model.entity.JobMatchEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface JobMatchMapper extends BaseMapper<JobMatchEntity> {
}
