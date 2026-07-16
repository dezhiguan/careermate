package com.careermate.pipeline.dto;

import lombok.Data;

import java.util.List;

/**
 * 投递看板：按阶段分列。
 */
@Data
public class PipelineBoardVO {

    private int total;
    private List<Column> columns;

    /** 一个阶段列。 */
    @Data
    public static class Column {
        private String stage;
        private String label;
        private int order;
        private int count;
        private List<ApplicationVO> applications;
    }
}
