package com.careermate.resume.version.verify;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * {@link ResumeFactVerifier} 纯单元测试——不依赖 Spring / DB。
 */
class ResumeFactVerifierTest {

    private final ResumeFactVerifier verifier = new ResumeFactVerifier();

    @Test
    void passesWhenAllStrongFactsHaveSource() {
        String source = "在腾讯科技（深圳）有限公司担任高级工程师，负责交易系统；北京大学 计算机 本科；持有 PMP 认证。";
        String generated = "## 工作经历\n腾讯科技（深圳）有限公司 高级工程师\n## 教育\n北京大学 本科\n## 证书\nPMP";
        FactCheckResult r = verifier.verify(generated, source);
        assertThat(r.isPass()).isTrue();
        assertThat(r.unsourcedFacts()).isEmpty();
        assertThat(r.requiresConfirmation()).isFalse();
    }

    @Test
    void flagsFabricatedCompanyNotInSource() {
        String source = "三年 Java 后端经验，做过订单系统。";
        String generated = "## 工作经历\n阿里巴巴集团 资深工程师\n主导双十一大促。";
        FactCheckResult r = verifier.verify(generated, source);
        assertThat(r.isSuspect()).isTrue();
        assertThat(r.unsourcedFacts()).anyMatch(f -> f.contains("阿里巴巴集团"));
        assertThat(r.requiresConfirmation()).isTrue();
    }

    @Test
    void flagsFabricatedSchool() {
        String source = "自学 Java，无全日制学历相关描述。";
        String generated = "## 教育\n清华大学 软件工程 硕士";
        FactCheckResult r = verifier.verify(generated, source);
        assertThat(r.isSuspect()).isTrue();
        assertThat(r.unsourcedFacts()).anyMatch(f -> f.contains("清华大学"));
        // 学历词「硕士」在源中也无出处，应一并标记
        assertThat(r.unsourcedFacts()).anyMatch(f -> f.contains("硕士"));
    }

    @Test
    void flagsFabricatedCertificate() {
        String source = "熟悉财务流程。";
        String generated = "持有 注册会计师 资格证书。";
        FactCheckResult r = verifier.verify(generated, source);
        assertThat(r.isSuspect()).isTrue();
        assertThat(r.unsourcedFacts()).isNotEmpty();
    }

    @Test
    void flagsFabricatedPercentage() {
        String source = "优化了接口性能，响应更快。";
        String generated = "接口 P99 延迟下降 42%，QPS 提升 3倍。";
        FactCheckResult r = verifier.verify(generated, source);
        assertThat(r.isSuspect()).isTrue();
        assertThat(r.unsourcedFacts()).anyMatch(f -> f.equals("42%"));
        assertThat(r.unsourcedFacts()).anyMatch(f -> f.equals("3倍"));
    }

    @Test
    void treatsExtraSignalsAsTrustedSource() {
        String source = "做过后端开发。";
        String generated = "技能：Kubernetes、Kafka。目标岗位：高级 Java 工程师。";
        // 画像信号里带上这些，视为可信来源 → 不应判可疑
        List<String> signals = List.of("Kubernetes", "Kafka", "高级 Java 工程师");
        FactCheckResult r = verifier.verify(generated, source, signals);
        assertThat(r.isPass()).isTrue();
    }

    @Test
    void nullGeneratedReturnsErrorForFailClosed() {
        FactCheckResult r = verifier.verify(null, "任意源");
        assertThat(r.isError()).isTrue();
        assertThat(r.requiresConfirmation()).isTrue();
    }

    @Test
    void blankSourceWithStrongFactsIsSuspect() {
        String generated = "华为技术有限公司 工程师。";
        FactCheckResult r = verifier.verify(generated, "   ");
        assertThat(r.isSuspect()).isTrue();
        assertThat(r.unsourcedFacts()).anyMatch(f -> f.contains("华为技术有限公司"));
    }

    @Test
    void noStrongFactsPasses() {
        String generated = "热爱学习，沟通能力强，具备团队协作精神。";
        FactCheckResult r = verifier.verify(generated, "");
        assertThat(r.isPass()).isTrue();
    }

    @Test
    void normalizationIgnoresWhitespaceDifferences() {
        String source = "曾任职  字节跳动 有限公司 。";
        String generated = "字节跳动有限公司 后端工程师";
        FactCheckResult r = verifier.verify(generated, source);
        // 源里带空格，归一化后应能匹配到「字节跳动有限公司」
        assertThat(r.isPass()).isTrue();
    }

    @Test
    void extractsLongestInstitutionSuffix() {
        var facts = verifier.extractStrongFacts("入职 蚂蚁金服有限责任公司 任职。");
        // 应抽出带「有限责任公司」的完整机构名，而非误切成「有限公司」
        assertThat(facts).anyMatch(f -> f.contains("有限责任公司"));
    }

    @Test
    void multipleUnsourcedFactsAllCollected() {
        String source = "普通开发经历。";
        String generated = "腾讯科技有限公司；北京大学；PMP；50%。";
        FactCheckResult r = verifier.verify(generated, source);
        assertThat(r.unsourcedFacts()).hasSizeGreaterThanOrEqualTo(3);
    }
}
