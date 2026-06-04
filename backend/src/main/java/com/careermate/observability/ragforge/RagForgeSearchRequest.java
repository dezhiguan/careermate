package com.careermate.observability.ragforge;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RagForgeSearchRequest {

    private String kbId;
    private String query;
    private int topK;
    private String searchType;
}
