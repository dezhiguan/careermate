package com.careermate.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmsSendResponse {

    private long cooldownSeconds;
    /** Client must pass this back on login; equals provider outId when present, else server-generated id. */
    private String challengeId;
    /** Provider outId when returned by SMS auth service; may be null. */
    private String outId;
}
