package com.careermate.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.careermate.mapper")
public class MybatisConfig {
}
