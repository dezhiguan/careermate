package com.careermate.auth.sms;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "aliyun.sms")
public class AliyunSmsProperties {

    private boolean enabled = false;
    private boolean mockEnabled = true;
    private String accessKeyId = "";
    private String accessKeySecret = "";
    private String signName = "";
    private String region = "cn-hangzhou";
    private String endpoint = "dypnsapi.aliyuncs.com";
    private Template template = new Template();
    private String phoneHashPepper = "";

    @Data
    public static class Template {
        private String login = "";
        private int validMinutes = 5;
    }
}
