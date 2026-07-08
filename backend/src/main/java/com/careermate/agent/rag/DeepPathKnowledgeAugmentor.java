package com.careermate.agent.rag;

import com.careermate.agent.tool.rag.RagRetrieveRequest;
import com.careermate.agent.tool.rag.RagRetrieveResult;
import com.careermate.agent.tool.rag.RagRetrieveScene;
import com.careermate.agent.tool.rag.RagRetrievedChunk;
import com.careermate.knowledge.KnowledgeRetrievalService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * A1 补漏：deep-path 带引用检索（等价 QuestionAnswerAdvisor，绕开 spring-ai-core 版本错配）。
 *
 * <p>deep-path 时主动经 {@link KnowledgeRetrievalService} 检索场景知识库，产出带来源编号的参考资料块
 * 注入 system prompt，并约束模型"基于资料作答 + 关键结论标注来源 + 无据不编造"——即"引用可核验"。
 * 检索失败/无命中返回空串，不影响主流程（错误信息保持友好、静默降级）。
 */
@Slf4j
@Component
public class DeepPathKnowledgeAugmentor {

    private static final int TOP_K = 5;

    private final KnowledgeRetrievalService knowledgeRetrievalService;

    public DeepPathKnowledgeAugmentor(KnowledgeRetrievalService knowledgeRetrievalService) {
        this.knowledgeRetrievalService = knowledgeRetrievalService;
    }

    /**
     * 构造带来源标注的参考资料块；无查询/无命中/检索异常均返回空串。
     */
    public String buildCitationBlock(String userMessage) {
        return buildCitationBlock(RagRetrieveScene.GENERAL, userMessage);
    }

    public String buildCitationBlock(RagRetrieveScene scene, String userMessage) {
        if (!StringUtils.hasText(userMessage)) {
            return "";
        }
        try {
            RagRetrieveResult result = knowledgeRetrievalService.retrieve(RagRetrieveRequest.builder()
                    .query(userMessage)
                    .scene(scene == null ? RagRetrieveScene.GENERAL : scene)
                    .topK(TOP_K)
                    .build());
            if (result == null || !result.isSuccess() || result.getChunks() == null || result.getChunks().isEmpty()) {
                return "";
            }
            return render(result.getChunks());
        } catch (Exception e) {
            log.warn("deep-path 引用检索失败（静默降级，不影响对话）: {}", e.getMessage());
            return "";
        }
    }

    private String render(List<RagRetrievedChunk> chunks) {
        StringBuilder sb = new StringBuilder("\n\n【参考资料 · 可核验来源】\n")
                .append("请基于以下资料作答，并在关键结论后用 [编号] 标注来源；资料未覆盖处如实说明，不要编造：\n");
        for (int i = 0; i < chunks.size(); i++) {
            RagRetrievedChunk c = chunks.get(i);
            String cite = c.getCitation() == null ? "" : c.getCitation();
            String preview = c.getContentPreview() == null ? "" : c.getContentPreview();
            sb.append('[').append(i + 1).append("] ").append(cite).append(' ').append(preview).append('\n');
        }
        return sb.toString();
    }
}
