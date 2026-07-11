package com.careermate.knowledge;

import com.careermate.ragforge.RagForgeProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 知识库配置完整性自检（评审文档第九章 · 守卫 #1）。
 *
 * <p>CareerMate 依赖 6 个 RAGForge 知识库槽位（JD / 面试 / 薪资 / 公司 / 技能 / 个人）。
 * 生产环境常见故障是「代码支持某库、但部署 env 漏配对应 id」，导致检索静默降级或功能变空，
 * 且无任何显式信号。本组件在应用就绪时打印每个槽位的配置状态，缺失即以 WARN 高亮，
 * 把「漏配」从不可见变为一眼可查。纯判定逻辑（{@link #inspect()} / {@link #missingLabels()}）
 * 无副作用，便于单测。
 */
@Slf4j
@Component
public class KbConfigInspector {

    private final RagForgeProperties props;

    public KbConfigInspector(RagForgeProperties props) {
        this.props = props;
    }

    /** 单个知识库槽位的配置快照。 */
    public record KbSlot(String key, String label, String id, boolean configured) {
    }

    /** 返回全部 6 个知识库槽位的配置快照（顺序稳定）。 */
    public List<KbSlot> inspect() {
        return List.of(
                slot("jdKbId", "岗位 JD 库", props.getJdKbId()),
                slot("interviewKbId", "面试题库", props.getInterviewKbId()),
                slot("marketKbId", "薪资行情库", props.getMarketKbId()),
                slot("companyKbId", "目标公司库", props.getCompanyKbId()),
                slot("skillKbId", "岗位技能画像库", props.getSkillKbId()),
                slot("personalKbId", "个人简历库", props.getPersonalKbId())
        );
    }

    /** 未配置（id 为空或非正整数）的知识库中文名清单。 */
    public List<String> missingLabels() {
        return inspect().stream()
                .filter(s -> !s.configured())
                .map(KbSlot::label)
                .toList();
    }

    /** 6 个知识库是否全部已配置。 */
    public boolean allConfigured() {
        return missingLabels().isEmpty();
    }

    private KbSlot slot(String key, String label, String idRaw) {
        return new KbSlot(key, label, StringUtils.hasText(idRaw) ? idRaw.trim() : null, isConfigured(idRaw));
    }

    private boolean isConfigured(String idRaw) {
        if (!StringUtils.hasText(idRaw)) {
            return false;
        }
        try {
            return Long.parseLong(idRaw.trim()) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** 应用就绪时打印知识库配置自检结果；RAGForge 未启用时跳过。 */
    @EventListener(ApplicationReadyEvent.class)
    public void logOnStartup() {
        if (!props.isEnabled()) {
            log.info("[KB-CONFIG] RAGForge 未启用（careermate.ragforge.enabled=false），跳过知识库配置自检。");
            return;
        }
        List<String> missing = missingLabels();
        if (missing.isEmpty()) {
            log.info("[KB-CONFIG] 知识库配置自检通过：6 个知识库（JD / 面试 / 薪资 / 公司 / 技能 / 个人）均已配置。");
            return;
        }
        log.warn("[KB-CONFIG] 知识库配置不完整，以下 {} 个库未配置 id，相关功能将降级或不可用：{}。"
                + "请在部署环境补齐对应 RAGFORGE_*_KB_ID。", missing.size(), String.join("、", missing));
    }
}
