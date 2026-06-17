package com.careermate.market.dto;

import lombok.Data;

@Data
public class MarketSourceCitationVO {

    private String citation;
    private String contentPreview;
    private String sourceTitle;
    private Double score;
    private String chunkType;
}
