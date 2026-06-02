package com.careermate.llm;

import com.careermate.llm.dto.ChatResponse;

public interface StreamCallback {

    void onToken(String token);

    void onComplete(ChatResponse response);

    void onError(Throwable error);
}
