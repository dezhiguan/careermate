package com.careermate.agent.memory.ltm;

import lombok.Data;

/** A4：cosine 检索命中的 fact + 相似度分。 */
@Data
public class LtmMatch {
    private Long id;
    private String factType;
    private String factText;
    private Double confidence;
    /** cosine 相似度 0-1（1 最相似）。 */
    private Double score;
}
