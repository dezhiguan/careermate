package com.careermate.auth.captcha;

import lombok.Getter;

/**
 * 登录需要图形验证码时抛出。图形验证码的权威实现在 auth-gateway（与 RAGForge 一致）：
 * auth-gateway 在密码失败达阈值后返回 HTTP 423 CAPTCHA_REQUIRED，携带 PNG 图片与 challengeId。
 * CareerMate 仅透传——本异常承载网关下发的验证码，由 GlobalExceptionHandler 组装进响应 data。
 */
@Getter
public class CaptchaRequiredException extends RuntimeException {

    private final String challengeId;
    private final String captchaImage;

    public CaptchaRequiredException(String message, String challengeId, String captchaImage) {
        super(message);
        this.challengeId = challengeId;
        this.captchaImage = captchaImage;
    }
}
