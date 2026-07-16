package com.careermate.agent.tool;

import com.careermate.pipeline.dto.PipelineBoardVO;
import com.careermate.pipeline.service.PipelineService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 工具：查询用户的投递看板（各阶段在办数量），支撑「我最近投得怎么样」。
 */
@Component
public class GetPipelineBoardTool implements AgentTool {

    private final PipelineService pipelineService;

    public GetPipelineBoardTool(PipelineService pipelineService) {
        this.pipelineService = pipelineService;
    }

    @Override
    public String name() {
        return "get_pipeline_board";
    }

    @Override
    public String description() {
        return "查询用户求职投递看板：各阶段（准备投递/约面/面试中/Offer谈薪/已结束）的在办机会数量";
    }

    @Override
    public AgentToolDefinition definition() {
        return AgentToolDefinition.base(
                name(),
                "投递进度看板",
                description(),
                AgentToolDomain.DASHBOARD,
                AgentToolPermission.READ_USER_DATA,
                AgentToolRiskLevel.LOW
        ).example("我最近投得怎么样").build();
    }

    @Override
    public boolean supports(AgentToolContext context) {
        return true;
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        if (context.getUserId() == null) {
            return AgentToolResult.failure(name(), "查询看板失败", "未认证");
        }
        PipelineBoardVO board = pipelineService.getBoard(context.getUserId());

        List<Map<String, Object>> stages = new ArrayList<>();
        StringBuilder brief = new StringBuilder();
        if (board.getColumns() != null) {
            for (PipelineBoardVO.Column c : board.getColumns()) {
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("stage", c.getStage());
                s.put("label", c.getLabel());
                s.put("count", c.getCount());
                stages.add(s);
                if (c.getCount() > 0) {
                    if (brief.length() > 0) {
                        brief.append("、");
                    }
                    brief.append(c.getLabel()).append(c.getCount());
                }
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", board.getTotal());
        data.put("stages", stages);

        String summary = board.getTotal() == 0
                ? "你还没有在办的投递机会"
                : "共 " + board.getTotal() + " 个在办：" + brief;
        return AgentToolResult.success(name(), summary, data);
    }
}
