package com.careermate.artifact.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.careermate.artifact.ArtifactConstants;
import com.careermate.artifact.dto.CreateAgentArtifactCommand;
import com.careermate.common.exception.BizException;
import com.careermate.mapper.AgentArtifactMapper;
import com.careermate.model.entity.AgentArtifactEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentArtifactServiceImplTest {

    @Mock
    private AgentArtifactMapper agentArtifactMapper;

    private AgentArtifactServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AgentArtifactServiceImpl(agentArtifactMapper, new ObjectMapper());
    }

    @Test
    void createArtifactSuccess() {
        var command = new CreateAgentArtifactCommand(
                1L,
                "WS-abc",
                ArtifactConstants.TYPE_RESUME_VERSION,
                "腾讯 - 算法工程师",
                "针对 腾讯 - 算法工程师 生成的简历版本",
                ArtifactConstants.REF_RESUME_VERSION,
                "ver-123",
                Map.of("versionName", "腾讯 - 算法工程师", "targetJdId", "doc-1")
        );

        var vo = service.create(command);

        ArgumentCaptor<AgentArtifactEntity> captor = ArgumentCaptor.forClass(AgentArtifactEntity.class);
        verify(agentArtifactMapper).insert(captor.capture());
        AgentArtifactEntity saved = captor.getValue();
        assertEquals(1L, saved.getUserId());
        assertEquals("WS-abc", saved.getSessionId());
        assertEquals(ArtifactConstants.TYPE_RESUME_VERSION, saved.getArtifactType());
        assertEquals(ArtifactConstants.STATUS_ACTIVE, saved.getStatus());
        assertNotNull(saved.getArtifactId());
        assertNotNull(vo.artifactId());
        assertEquals("继续优化", vo.actionLabel());
        assertEquals("/chat/WS-abc", vo.actionRoute());
    }

    @Test
    void listRecentOnlyReturnsCurrentUser() {
        AgentArtifactEntity mine = artifactEntity("a1", 1L, LocalDateTime.of(2026, 6, 2, 10, 0));
        when(agentArtifactMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(mine));

        var list = service.listRecent(1L, 10);

        assertEquals(1, list.size());
        assertEquals("a1", list.get(0).artifactId());
        ArgumentCaptor<LambdaQueryWrapper<AgentArtifactEntity>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(agentArtifactMapper).selectList(captor.capture());
    }

    @Test
    void limitHasUpperBound() {
        when(agentArtifactMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        service.listRecent(1L, 999);

        ArgumentCaptor<LambdaQueryWrapper<AgentArtifactEntity>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(agentArtifactMapper).selectList(captor.capture());
        assertEquals(ArtifactConstants.MAX_LIST_LIMIT, AgentArtifactServiceImpl.normalizeLimit(999));
    }

    @Test
    void rejectsContentMarkdownInMetadata() {
        var command = new CreateAgentArtifactCommand(
                1L,
                "WS-abc",
                ArtifactConstants.TYPE_RESUME_VERSION,
                "标题",
                "摘要",
                ArtifactConstants.REF_RESUME_VERSION,
                "ver-1",
                Map.of("contentMarkdown", "# 不应保存")
        );

        assertThrows(BizException.class, () -> service.create(command));
    }

    @Test
    void metadataDoesNotContainContentMarkdown() {
        var command = new CreateAgentArtifactCommand(
                1L,
                "WS-abc",
                ArtifactConstants.TYPE_RESUME_VERSION,
                "标题",
                "摘要",
                ArtifactConstants.REF_RESUME_VERSION,
                "ver-1",
                Map.of("versionName", "标题")
        );

        service.create(command);

        ArgumentCaptor<AgentArtifactEntity> captor = ArgumentCaptor.forClass(AgentArtifactEntity.class);
        verify(agentArtifactMapper).insert(captor.capture());
        String metadataJson = captor.getValue().getMetadata();
        assertNotNull(metadataJson);
        assertFalse(metadataJson.contains("contentMarkdown"));
    }

    private static AgentArtifactEntity artifactEntity(String artifactId, Long userId, LocalDateTime createdAt) {
        AgentArtifactEntity entity = new AgentArtifactEntity();
        entity.setArtifactId(artifactId);
        entity.setUserId(userId);
        entity.setArtifactType(ArtifactConstants.TYPE_RESUME_VERSION);
        entity.setTitle("测试产物");
        entity.setRefType(ArtifactConstants.REF_RESUME_VERSION);
        entity.setRefId("ver-1");
        entity.setSessionId("WS-abc");
        entity.setStatus(ArtifactConstants.STATUS_ACTIVE);
        entity.setCreatedAt(createdAt);
        return entity;
    }
}
