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

    @Test
    void 问薪资走谈薪工具而不是泛化检索() {
        // rag_retriever 返回的是薪资报告原文，模型自己从里面「读」分位数，读出 P50=32k；
        // get_salary_guidance 返回的是算好的分位与锚点（接口实为 18K）。同一个问题给出两套
        // 数字，而用户是要拿这个去谈薪的，所以薪资类提问必须优先命中谈薪工具。
        for (String q : new String[]{
                "我这个方向在广州大概能拿多少薪资？",
                "这个岗位的薪资水平怎么样",
                "我期望 38k 合理吗，怎么谈"}) {
            Optional<AgentToolRouter.RoutedTool> routed = router.route(q);
            assertTrue(routed.isPresent(), q);
            assertEquals("get_salary_guidance", routed.get().toolName(), q);
        }
    }

    @Test
    void 泛问市场行情仍走检索() {
        // 不是询价，别把要全景的问题也吃进谈薪工具
        Optional<AgentToolRouter.RoutedTool> routed = router.route("现在的就业市场行情怎么样？");
        assertTrue(routed.isPresent());
        assertEquals("rag_retriever", routed.get().toolName());
    }
}
