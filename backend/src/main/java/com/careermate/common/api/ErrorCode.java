package com.careermate.common.api;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    SUCCESS(0, "success"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未认证"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    TOO_MANY_REQUESTS(429, "请求过多"),
    INTERNAL_ERROR(500, "系统异常"),

    PHONE_FORMAT_INVALID(400, "手机号格式不正确"),
    SMS_SEND_TOO_FREQUENT(429, "验证码已发送，请稍后再试"),
    SMS_SEND_LIMITED(429, "请求过于频繁，请稍后再试"),
    SMS_CODE_INVALID(400, "验证码错误或已过期"),
    SMS_CODE_EXPIRED(400, "验证码错误或已过期"),
    SMS_CODE_TOO_MANY_ATTEMPTS(400, "验证码错误或已过期"),
    SMS_PROVIDER_ERROR(500, "验证码发送失败，请稍后再试"),
    SMS_CAPTCHA_REQUIRED(403, "需要图形验证码"),

    MOBILE_AUTH_INVALID(400, "验证失败，请重新获取验证码"),
    MOBILE_AUTH_EXPIRED(400, "验证失败，请重新获取验证码"),
    MOBILE_AUTH_TOO_MANY_ATTEMPTS(400, "验证失败，请重新获取验证码"),
    MOBILE_AUTH_LIMITED(429, "请求过于频繁，请稍后再试"),
    MOBILE_AUTH_PROVIDER_ERROR(500, "登录失败，请稍后再试"),
    PHONE_BIND_CONFLICT(400, "登录失败，请稍后再试");

    private final Integer code;
    private final String message;
}
