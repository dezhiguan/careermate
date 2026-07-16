package com.careermate.interview.dto;

import lombok.Data;

import java.util.List;

/**
 * JD-aware 面试题 VO —— 围绕一条 JD 生成的针对性面试题列表。
 *
 * <p>依据「这条 JD + 用户简历 + 面试题库」出题，每题带标签：
 * JD_FOCUSED（该 JD 核心考点）/ HIT_RESUME（命中简历技能）/ WEAK_POINT（简历薄弱点）。
 *
 * <p>「有据才答」：JD 内容缺失时 {@link #dataAvailable}=false 且 questions 为空、明说原因。
 */
@Data
public class JdAwareQuestionsVO {

    /** 目标 JD 的 RAGForge docId。 */
    private Long jdDocId;

    /** JD 标题（岗位/来源）。 */
    private String jdTitle;

    /** 针对性面试题列表。 */
    private List<JdAwareQuestion> questions;

    /** 一句话小结。 */
    private String aiSummary;

    /** 是否成功生成（JD 未找到则 false）。 */
    private boolean dataAvailable;

    /** 单道面试题。 */
    @Data
    public static class JdAwareQuestion {
        /** 题号（从 1 递增）。 */
        private int questionNo;
        /** 题目。 */
        private String questionText;
        /** 题型：技术 / 系统设计 / 行为 / HR。 */
        private String questionType;
        /** 参考要点。 */
        private List<String> referencePoints;
        /** 标签：JD_FOCUSED / HIT_RESUME / WEAK_POINT。 */
        private String tag;
        /** 为什么该问这道题。 */
        private String rationale;
    }
}
