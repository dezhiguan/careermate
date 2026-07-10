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

    PHONE_FORMAT_INVALID(400, "请输入正确的手机号"),
    SMS_SEND_TOO_FREQUENT(429, "验证码已发送，请稍后再试"),
    SMS_SEND_LIMITED(429, "操作过于频繁，请稍后再试"),
    SMS_CODE_INVALID(400, "验证码错误或已过期"),
    SMS_CODE_EXPIRED(400, "验证码错误或已过期"),
    SMS_CODE_TOO_MANY_ATTEMPTS(400, "验证码错误或已过期"),
    SMS_PROVIDER_ERROR(500, "验证码发送失败，请稍后再试"),
    SMS_CAPTCHA_REQUIRED(403, "当前操作需要安全验证，请稍后再试"),

    MOBILE_AUTH_CODE_WRONG(400, "验证码错误，请重新输入"),
    MOBILE_AUTH_CHALLENGE_REQUIRED(400, "请先获取验证码"),
    MOBILE_AUTH_EXPIRED(400, "验证码已失效，请重新获取"),
    MOBILE_AUTH_INVALID(400, "验证码已失效，请重新获取"),
    MOBILE_AUTH_TOO_MANY_ATTEMPTS(400, "验证码错误次数过多，请重新获取"),
    MOBILE_AUTH_LIMITED(429, "操作过于频繁，请稍后再试"),
    MOBILE_AUTH_PROVIDER_ERROR(500, "暂时无法验证验证码，请稍后再试"),
    PHONE_BIND_CONFLICT(400, "登录失败，请稍后再试"),

    PASSWORD_RESET_INVALID(400, "验证码错误或已过期，请重新获取"),
    PASSWORD_RESET_LIMITED(429, "操作过于频繁，请稍后再试"),
    PASSWORD_RESET_PROVIDER_ERROR(500, "验证码发送失败，请稍后再试"),

    ACCOUNT_BANNED(403, "账号已被停用，请联系客服处理"),
    ACCOUNT_LOCKED(429, "账号已因多次失败被暂时锁定，请15分钟后再试，或使用手机验证码登录"),
    ACCOUNT_CANCELLING(403, "账号正在注销中，如需恢复请在7天内撤销注销"),
    CAPTCHA_REQUIRED(400, "连续登录失败，请先完成图形验证"),
    CAPTCHA_INVALID(400, "图形验证码错误，请重新输入"),
    USERNAME_TAKEN(400, "该账号名已被使用，请换一个"),
    USERNAME_FORMAT_INVALID(400, "账号名为4-32位，支持字母、数字、下划线、中划线，且不能全为数字"),
    USERNAME_UPDATE_TOO_FREQUENT(400, "账号名每30天只能修改一次"),
    EMAIL_ALREADY_BOUND(400, "该邮箱已绑定其他账号"),
    EMAIL_FORMAT_INVALID(400, "请输入有效的邮箱地址"),
    PASSWORD_WEAK(400, "密码至少8位，且包含字母和数字"),
    PASSWORD_HISTORY_CONFLICT(400, "新密码不能与近3次使用的密码相同"),
    SESSION_NOT_FOUND(404, "会话不存在或已过期"),
    PHONE_VERIFY_REQUIRED(400, "请先完成手机号验证"),
    PHONE_ALREADY_BOUND(400, "该手机号已绑定其他账号"),
    ACCOUNT_DELETE_CONFIRM_INVALID(400, "请输入「注销」确认注销操作"),
    ACCOUNT_NOT_CANCELLING(400, "当前账号不在注销流程中");

    private final Integer code;
    private final String message;
}
