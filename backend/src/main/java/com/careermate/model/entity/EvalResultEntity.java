package com.careermate.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.careermate.mybatis.JsonbStringTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;

/** A5：评测结果。 */
@Data
@TableName(value = "eval_results", autoResultMap = true)
public class EvalResultEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String evalSuiteId;
    private String scenarioId;
    private String modelProvider;
    private String judgeProvider;

    @TableField(value = "judge_scores", typeHandler = JsonbStringTypeHandler.class)
    private String judgeScores;

    private Double overallScore;
    private Integer tokenIn;
    private Integer tokenOut;
    private Double cost;
    private OffsetDateTime createdAt;
}
