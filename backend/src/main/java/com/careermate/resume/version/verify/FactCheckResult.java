package com.careermate.resume.version.verify;

import java.util.Collections;
import java.util.List;

/**
 * 简历生成的确定性事实校验结果（P2）。
 *
 * <p>作为 LLM 判断真实性之外的字符串级兜底：抽取生成版本中的强事实
 * （公司/院校/机构、学历、证书、显著量化数字），逐条比对源简历 + 画像信号，
 * 源中无支撑者列入 {@link #unsourcedFacts()} 并判定为 {@link Status#SUSPECT}。
 *
 * <p>该结果会以 JSON 落库到 {@code resume_versions.fact_check}，并驱动工作流的
 * 「不自动落库 + 卡片标红」护栏。
 */
public record FactCheckResult(Status status, List<String> unsourcedFacts, String checkedAt) {

    public enum Status {
        /** 全部强事实均有源支撑。 */
        PASS,
        /** 存在源中无出处的强事实，疑似编造。 */
        SUSPECT,
        /** 校验自身异常，按 fail-closed 处理（等同不通过）。 */
        ERROR
    }

    public FactCheckResult {
        // 防御性拷贝，保证不可变
        unsourcedFacts = unsourcedFacts == null
                ? List.of()
                : List.copyOf(unsourcedFacts);
    }

    public boolean isSuspect() {
        return status == Status.SUSPECT;
    }

    public boolean isError() {
        return status == Status.ERROR;
    }

    /** 通过（可自动落库）。 */
    public boolean isPass() {
        return status == Status.PASS;
    }

    /** SUSPECT 或 ERROR 都视为「不可自动落库、需人工确认」。 */
    public boolean requiresConfirmation() {
        return status != Status.PASS;
    }

    public static FactCheckResult pass(String checkedAt) {
        return new FactCheckResult(Status.PASS, Collections.emptyList(), checkedAt);
    }

    public static FactCheckResult suspect(List<String> unsourcedFacts, String checkedAt) {
        return new FactCheckResult(Status.SUSPECT, unsourcedFacts, checkedAt);
    }

    public static FactCheckResult error(String checkedAt) {
        return new FactCheckResult(Status.ERROR, Collections.emptyList(), checkedAt);
    }
}
