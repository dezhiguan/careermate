package com.careermate.resume.coldstart;

/**
 * 简历来源（P1）。落库到 resumes.origin，默认 UPLOAD 以保证存量语义不变。
 */
public final class ResumeOrigin {

    /** 上传文件解析而来（历史默认）。 */
    public static final String UPLOAD = "UPLOAD";
    /** 用户手动输入文本创建。 */
    public static final String MANUAL = "MANUAL";
    /** 无上传冷启动建档（L1/L2/L3）产生。 */
    public static final String COLD_START = "COLD_START";

    private ResumeOrigin() {
    }
}
