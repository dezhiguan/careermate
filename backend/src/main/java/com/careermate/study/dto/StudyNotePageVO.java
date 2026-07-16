package com.careermate.study.dto;

import java.util.List;

/**
 * 八股题库分页结果（同时支持 web 页码与移动滚动：都读 page/size）。
 */
public record StudyNotePageVO(
        List<StudyNoteVO> items,
        long total,
        int page,
        int size
) {
}
