package com.careermate.resume;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeContext {

    private boolean available;
    private Long resumeId;
    private String title;
    private String content;
    private String contextText;
}
