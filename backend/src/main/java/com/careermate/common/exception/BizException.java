package com.careermate.common.exception;

import com.careermate.common.api.ErrorCode;
import lombok.Getter;

@Getter
public class BizException extends RuntimeException {

    private final Integer code;
    private final String message;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.message = errorCode.getMessage();
    }

    public BizException(Integer code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }
}
