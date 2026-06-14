package com.careermate.auth.sms;

public enum SmsScene {
    MOBILE_LOGIN("mobile_login"),
    PASSWORD_RESET("password_reset");

    private final String value;

    SmsScene(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static SmsScene fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (SmsScene scene : values()) {
            if (scene.value.equals(value)) {
                return scene;
            }
        }
        return null;
    }
}
