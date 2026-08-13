package com.careermate.agent.tool;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 关键词路由是底线，不能只在 LLM 调用出错时才可达。
 *
 * <p>线上实测：「帮我创建一次模拟面试训练」被 LLM 意图识别判为「无需工具」，
 * 于是什么都没建，模型却回复「已成功创建面试训练，共 5 题，当前第 1 题」；
 * 「我这个方向在广州能拿多少薪资」同样没调工具，模型自己编了一组分位数还声称
 * 「我已调用薪资工具获取广州市场数据」。而这两句关键词路由本来都能稳稳命中。
 */
class IntentRouterKeywordFloorTest {

    private final AgentToolRouter router = new AgentToolRouter();

    @Test
    void 创建面试训练能被关键词路由命中() {
        Optional<AgentToolRouter.RoutedTool> routed = router.route("帮我创建一次模拟面试训练");
        assertTrue(routed.isPresent(), "显式的创建动词必须命中工具");
        assertEquals("create_interview_session", routed.get().toolName());
    }

    @Test
    void 问薪资能被关键词路由命中() {
        Optional<AgentToolRouter.RoutedTool> routed = router.route("我这个方向在广州大概能拿多少薪资？");
        assertTrue(routed.isPresent(), "薪资类提问必须走工具，不能让模型自己编数字");
    }
}
